'use strict';

const http = require('http');
const fs = require('fs');
const os = require('os');
const path = require('path');
const crypto = require('crypto');
const dgram = require('dgram');
const { spawn } = require('child_process');

const PORT = Number(process.env.CAMERA_AGENT_PORT || 3101);
const TOKEN = String(process.env.CAMERA_AGENT_TOKEN || '');
const PAIR_CODE = String(process.env.CAMERA_PAIR_CODE || '');
const PAIR_EXPIRES_AT = Date.now() + 10 * 60 * 1000;
const sessions = new Map();
let pairCodeUsed = false;
let pairFailures = 0;
let pairBlockedUntil = 0;
const extraOrigins = new Set(String(process.env.CAMERA_ALLOWED_ORIGINS || '').split(',').map(value => value.trim()).filter(Boolean));
const FFMPEG = String(process.env.FFMPEG_PATH || 'ffmpeg');
const MEDIA_DIR = path.join(os.tmpdir(), 'mediaaistudio-camera');
fs.mkdirSync(MEDIA_DIR, { recursive: true });

let streamProcess = null;
let streamState = { active: false, source: null, startedAt: null, error: null };

function authorized(req) {
  const supplied = String(req.headers.authorization || '').replace(/^Bearer\s+/i, '');
  const a = Buffer.from(supplied);
  const b = Buffer.from(TOKEN);
  return b.length > 0 && a.length === b.length && crypto.timingSafeEqual(a, b);
}
function send(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(JSON.stringify(body));
}
function readJson(req) {
  return new Promise((resolve, reject) => {
    let body = '';
    req.on('data', chunk => { body += chunk; if (body.length > 16384) reject(new Error('Request too large')); });
    req.on('end', () => {
      try { resolve(body ? JSON.parse(body) : {}); } catch { reject(new Error('Invalid JSON')); }
    });
    req.on('error', reject);
  });
}
function ffmpegAvailable() {
  return new Promise(resolve => {
    const child = spawn(FFMPEG, ['-version'], { windowsHide: true });
    let output = '';
    child.stdout.on('data', d => { output += d.toString(); });
    child.on('error', () => resolve({ available: false, version: null }));
    child.on('close', code => resolve({ available: code === 0, version: output.split(/\r?\n/)[0] || null }));
  });
}
function stopStream() {
  if (streamProcess && !streamProcess.killed) {
    try { streamProcess.kill('SIGTERM'); } catch {}
  }
  streamProcess = null;
  streamState = { active: false, source: null, startedAt: null, error: null };
  for (const name of fs.readdirSync(MEDIA_DIR)) {
    if (name.endsWith('.m3u8') || name.endsWith('.ts')) {
      try { fs.unlinkSync(path.join(MEDIA_DIR, name)); } catch {}
    }
  }
}
async function listDevices() {
  const availability = await ffmpegAvailable();
  if (!availability.available) return { ...availability, devices: [], message: 'FFmpeg is not installed or is not available on PATH.' };
  return new Promise(resolve => {
    const child = spawn(FFMPEG, ['-hide_banner', '-list_devices', 'true', '-f', 'dshow', '-i', 'dummy'], { windowsHide: true });
    let stderr = '';
    child.stderr.on('data', d => { stderr += d.toString(); });
    child.on('error', () => resolve({ available: false, version: null, devices: [], message: 'Could not start FFmpeg.' }));
    child.on('close', () => {
      const devices = [];
      const re = /"([^"]+)"\s+\((video|audio)\)/gi;
      let match;
      while ((match = re.exec(stderr))) devices.push({ name: match[1], kind: match[2] });
      resolve({ ...availability, devices: devices.filter(d => d.kind === 'video'), message: null });
    });
  });
}
function startRtsp(url) {
  let parsed;
  try { parsed = new URL(url); } catch { throw new Error('Enter a valid RTSP URL.'); }
  if (!['rtsp:', 'rtsps:'].includes(parsed.protocol)) throw new Error('Only rtsp:// and rtsps:// camera URLs are accepted.');
  if (!parsed.hostname) throw new Error('The RTSP URL must include a camera host.');
  if (streamProcess && !streamProcess.killed) stopStream();

  const playlist = path.join(MEDIA_DIR, 'stream.m3u8');
  const args = [
    '-hide_banner', '-loglevel', 'warning', '-rtsp_transport', 'tcp', '-i', url,
    '-an', '-vf', 'scale=w=1280:h=720:force_original_aspect_ratio=decrease',
    '-c:v', 'libx264', '-preset', 'ultrafast', '-tune', 'zerolatency',
    '-f', 'hls', '-hls_time', '2', '-hls_list_size', '5',
    '-hls_flags', 'delete_segments+append_list+omit_endlist',
    '-hls_segment_filename', path.join(MEDIA_DIR, 'segment_%03d.ts'), playlist
  ];
  const child = spawn(FFMPEG, args, { windowsHide: true, stdio: ['ignore', 'ignore', 'pipe'] });
  let errorText = '';
  child.stderr.on('data', d => { errorText = (errorText + d.toString()).slice(-3000); });
  child.on('error', error => { streamState.error = error.message; streamState.active = false; });
  child.on('close', code => {
    if (streamProcess === child) {
      streamState.active = false;
      if (code !== 0 && code !== null) streamState.error = errorText.trim() || 'FFmpeg stopped unexpectedly.';
      streamProcess = null;
    }
  });
  streamProcess = child;
  streamState = { active: true, source: parsed.hostname, startedAt: new Date().toISOString(), error: null };
  return { ok: true, playlist: '/api/camera/hls/stream.m3u8', status: streamState };
}

function parseOnvifResponse(buffer) {
  const xml = buffer.toString('utf8');
  const getTag = (name) => {
    const match = xml.match(new RegExp('<(?:\\w+:)?' + name + '[^>]*>([\\s\\S]*?)<\\/(?:\\w+:)?' + name + '>', 'i'));
    return match ? match[1].replace(/&amp;/g, '&').trim() : '';
  };
  const xaddrs = getTag('XAddrs').split(/\s+/).filter(Boolean);
  const scopes = getTag('Scopes').split(/\s+/).filter(Boolean);
  const endpoint = getTag('Address');
  if (!xaddrs.length) return null;
  return {
    endpoint,
    xaddrs,
    scopes,
    name: scopes.map(scope => {
      const part = scope.split('/').filter(Boolean).pop() || '';
      try { return decodeURIComponent(part); } catch { return part; }
    }).find(value => value && !value.includes(':')) || 'ONVIF camera'
  };
}
function discoverOnvif() {
  return new Promise(resolve => {
    const socket = dgram.createSocket({ type: 'udp4', reuseAddr: true });
    const found = new Map();
    let finished = false;
    const finish = (message) => {
      if (finished) return;
      finished = true;
      try { socket.close(); } catch {}
      resolve({ devices: Array.from(found.values()), message: message || null });
    };
    const timer = setTimeout(() => finish(null), 3500);
    socket.on('message', packet => {
      const device = parseOnvifResponse(packet);
      if (device) for (const address of device.xaddrs) found.set(address, { ...device, xaddr: address });
    });
    socket.on('error', error => { clearTimeout(timer); finish(error.message); });
    socket.bind(0, () => {
      try {
        socket.addMembership('239.255.255.250');
        socket.setMulticastTTL(2);
        const messageId = 'uuid:' + crypto.randomUUID();
        const probe = '<?xml version="1.0" encoding="UTF-8"?>' +
          '<e:Envelope xmlns:e="http://www.w3.org/2003/05/soap-envelope" xmlns:w="http://schemas.xmlsoap.org/ws/2004/08/addressing" xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery" xmlns:dn="http://www.onvif.org/ver10/network/wsdl">' +
          '<e:Header><w:MessageID>' + messageId + '</w:MessageID><w:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</w:To><w:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</w:Action></e:Header>' +
          '<e:Body><d:Probe><d:Types>dn:NetworkVideoTransmitter</d:Types></d:Probe></e:Body></e:Envelope>';
        socket.send(Buffer.from(probe), 3702, '239.255.255.250', error => {
          if (error) { clearTimeout(timer); finish(error.message); }
        });
      } catch (error) {
        clearTimeout(timer);
        finish(error.message);
      }
    });
  });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url || '/', 'http://127.0.0.1');
  const corsAllowed = applyCors(req, res);
  if (req.method === 'OPTIONS') return res.writeHead(corsAllowed ? 204 : 403).end();
  if (req.method === 'POST' && url.pathname === '/pair') {
    if (!corsAllowed) return send(res, 403, { error: 'ORIGIN_NOT_ALLOWED' });
    if (Date.now() < pairBlockedUntil) return send(res, 429, { error: 'PAIR_RATE_LIMITED', message: 'Wait before trying another pairing code.' });
    try {
      const body = await readJson(req);
      const supplied = Buffer.from(String(body.code || '').trim());
      const expected = Buffer.from(PAIR_CODE);
      const valid = !pairCodeUsed && Date.now() < PAIR_EXPIRES_AT && expected.length > 0 && supplied.length === expected.length && crypto.timingSafeEqual(supplied, expected);
      if (!valid) {
        pairFailures += 1;
        if (pairFailures >= 8) pairBlockedUntil = Date.now() + 5 * 60 * 1000;
        return send(res, 401, { error: 'PAIR_CODE_INVALID', message: 'The pairing code is invalid, expired, or already used.' });
      }
      pairCodeUsed = true;
      pairFailures = 0;
      const sessionToken = crypto.randomBytes(32).toString('hex');
      const expiresAt = Date.now() + 4 * 60 * 60 * 1000;
      sessions.set(sessionToken, { expiresAt });
      return send(res, 200, { token: sessionToken, expiresAt });
    } catch (error) { return send(res, 400, { error: 'PAIR_REQUEST_INVALID', message: error.message }); }
  }
  if (!authorized(req)) return send(res, 401, { error: 'UNAUTHORIZED' });
  if (req.method === 'GET' && url.pathname === '/health') {
    const ffmpeg = await ffmpegAvailable();
    return send(res, 200, { status: 'ok', ffmpeg, rtsp: streamState, pairCode: suppliedToken(req) === TOKEN && !pairCodeUsed && Date.now() < PAIR_EXPIRES_AT ? PAIR_CODE : null, pairCodeExpiresAt: PAIR_EXPIRES_AT });
  }
  if (req.method === 'POST' && url.pathname === '/onvif/discover') {
    const result = await discoverOnvif();
    return send(res, 200, result);
  }
  if (req.method === 'GET' && url.pathname === '/devices') {
    return send(res, 200, await listDevices());
  }
  if (req.method === 'GET' && url.pathname === '/rtsp/status') return send(res, 200, streamState);
  if (req.method === 'POST' && url.pathname === '/rtsp/start') {
    try {
      const body = await readJson(req);
      const ffmpeg = await ffmpegAvailable();
      if (!ffmpeg.available) return send(res, 503, { error: 'FFMPEG_MISSING', message: 'Install FFmpeg and restart MediaAIStudio to enable RTSP cameras.' });
      return send(res, 200, startRtsp(String(body.url || '').trim()));
    } catch (error) { return send(res, 400, { error: 'RTSP_START_FAILED', message: error.message }); }
  }
  if (req.method === 'POST' && url.pathname === '/rtsp/stop') {
    stopStream();
    return send(res, 200, { ok: true });
  }
  if (req.method === 'GET' && url.pathname.startsWith('/hls/')) {
    const filename = path.basename(url.pathname.slice('/hls/'.length));
    if (!/^(stream\.m3u8|segment_\d+\.ts)$/.test(filename)) return send(res, 400, { error: 'INVALID_MEDIA_PATH' });
    const file = path.join(MEDIA_DIR, filename);
    return fs.readFile(file, (error, data) => {
      if (error) return send(res, 404, { error: 'STREAM_NOT_READY' });
      res.writeHead(200, {
        'Content-Type': filename.endsWith('.m3u8') ? 'application/vnd.apple.mpegurl' : 'video/mp2t',
        'Cache-Control': 'no-store',
        'Access-Control-Allow-Origin': '*'
      });
      res.end(data);
    });
  }
  return send(res, 404, { error: 'NOT_FOUND' });
});
server.listen(PORT, '127.0.0.1', () => console.log('MediaAIStudio camera companion listening on 127.0.0.1:' + PORT));
process.on('SIGINT', () => { stopStream(); server.close(() => process.exit(0)); });
process.on('SIGTERM', () => { stopStream(); server.close(() => process.exit(0)); });
