const http = require('http');
const fs = require('fs');
const path = require('path');
const { spawn, execFile } = require('child_process');
const crypto = require('crypto');

const root = __dirname;
const backendDir = path.join(root, 'backend');
const distDir = path.join(root, 'web', 'dist');
const backendEnvPath = path.join(backendDir, '.env');
const PORT = 4173;
const BACKEND_PORT = 3100;
const CAMERA_AGENT_PORT = 3101;
const CAMERA_AGENT_TOKEN = crypto.randomBytes(32).toString('hex');
const CAMERA_PAIR_CODE = String(crypto.randomInt(10000000, 99999999));
const mime = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.mp4': 'video/mp4'
};

if (!fs.existsSync(path.join(distDir, 'index.html'))) {
  console.error('Web build is missing. Run Install.bat first.');
  process.exit(1);
}
if (!fs.existsSync(backendEnvPath)) {
  console.error('Backend configuration is missing. Run Install.bat first.');
  process.exit(1);
}

const dotenv = require(path.join(backendDir, 'node_modules', 'dotenv'));
const localEnv = dotenv.parse(fs.readFileSync(backendEnvPath));
const token = localEnv.MEDIA_AI_BACKEND_API_KEY;
if (!token) {
  console.error('MEDIA_AI_BACKEND_API_KEY is missing from backend/.env');
  process.exit(1);
}

const logDir = path.join(root, 'logs');
fs.mkdirSync(logDir, { recursive: true });
const backendLog = path.join(logDir, 'backend.log');
const backendOut = fs.createWriteStream(backendLog, { flags: 'a' });
const backendErr = fs.createWriteStream(backendLog, { flags: 'a' });
const cameraAgent = spawn(process.execPath, ['camera-agent.js'], {
  cwd: root,
  env: { ...process.env, CAMERA_AGENT_PORT: String(CAMERA_AGENT_PORT), CAMERA_AGENT_TOKEN, CAMERA_PAIR_CODE },
  stdio: ['ignore', backendOut, backendErr],
  windowsHide: true
});
const backend = spawn(process.execPath, ['src/index.js'], {
  cwd: backendDir,
  env: { ...process.env, PORT: String(BACKEND_PORT) },
  stdio: ['ignore', backendOut, backendErr],
  windowsHide: true
});

let stopping = false;
function stopBackend() {
  if (stopping) return;
  stopping = true;
  try { backend.kill(); } catch {}
  try { cameraAgent.kill(); } catch {}
  try { backendOut.end(); backendErr.end(); } catch {}
}
process.on('exit', stopBackend);
process.on('SIGINT', () => { stopBackend(); process.exit(0); });
process.on('SIGTERM', () => { stopBackend(); process.exit(0); });
backend.on('error', (error) => {
  fs.appendFileSync(path.join(logDir, 'backend.log'), String(error) + '\n');
});

const server = http.createServer((req, res) => {
  const requestUrl = new URL(req.url || '/', 'http://127.0.0.1');
  const cameraPrefix = '/api/camera';
  if (requestUrl.pathname === cameraPrefix || requestUrl.pathname.startsWith(cameraPrefix + '/')) {
    const targetPath = requestUrl.pathname.slice(cameraPrefix.length) || '/';
    const headers = { ...req.headers, host: '127.0.0.1:' + CAMERA_AGENT_PORT, authorization: 'Bearer ' + CAMERA_AGENT_TOKEN };
    delete headers.connection;
    const upstream = http.request({ hostname: '127.0.0.1', port: CAMERA_AGENT_PORT, path: targetPath + requestUrl.search, method: req.method, headers }, (upstreamRes) => {
      res.writeHead(upstreamRes.statusCode || 502, upstreamRes.headers);
      upstreamRes.pipe(res);
    });
    upstream.on('error', (error) => {
      if (!res.headersSent) res.writeHead(502, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'CAMERA_AGENT_UNAVAILABLE', message: error.message }));
    });
    req.pipe(upstream);
    return;
  }
  const prefix = '/api/backend';
  if (requestUrl.pathname === prefix || requestUrl.pathname.startsWith(prefix + '/')) {
    const targetPath = requestUrl.pathname.slice(prefix.length) || '/';
    const headers = { ...req.headers, host: '127.0.0.1:' + BACKEND_PORT, authorization: 'Bearer ' + token };
    delete headers.connection;
    const upstream = http.request({
      hostname: '127.0.0.1',
      port: BACKEND_PORT,
      path: targetPath + requestUrl.search,
      method: req.method,
      headers
    }, (upstreamRes) => {
      res.writeHead(upstreamRes.statusCode || 502, upstreamRes.headers);
      upstreamRes.pipe(res);
    });
    upstream.on('error', (error) => {
      if (!res.headersSent) res.writeHead(502, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'BACKEND_UNAVAILABLE', message: error.message }));
    });
    req.pipe(upstream);
    return;
  }

  let pathname;
  try { pathname = decodeURIComponent(requestUrl.pathname); }
  catch { res.writeHead(400); res.end('Bad request'); return; }
  const safePath = path.normalize(pathname).replace(/^([/\\]*\.\.[/\\])+/, '');
  let filePath = path.join(distDir, safePath);
  if (!filePath.startsWith(distDir)) {
    res.writeHead(403);
    res.end('Forbidden');
    return;
  }
  if (pathname === '/' || !path.extname(filePath)) filePath = path.join(distDir, 'index.html');
  fs.stat(filePath, (error, stat) => {
    if (error || !stat.isFile()) {
      filePath = path.join(distDir, 'index.html');
    }
    fs.readFile(filePath, (readError, data) => {
      if (readError) {
        res.writeHead(404);
        res.end('Not found');
        return;
      }
      res.writeHead(200, { 'Content-Type': mime[path.extname(filePath).toLowerCase()] || 'application/octet-stream', 'Cache-Control': 'no-cache' });
      res.end(data);
    });
  });
});

function waitForBackend(attempt = 0) {
  const probe = http.get({ hostname: '127.0.0.1', port: BACKEND_PORT, path: '/health', timeout: 1200 }, (response) => {
    response.resume();
    if (response.statusCode === 200) {
      server.listen(PORT, '127.0.0.1', () => {
        const url = 'http://127.0.0.1:' + PORT;
        execFile('cmd', ['/c', 'start', '', url], { windowsHide: true });
        console.log('MediaAIStudio is running at ' + url);
      });
    } else retry();
  });
  probe.on('error', retry);
  probe.on('timeout', () => { probe.destroy(); retry(); });
  function retry() {
    if (attempt >= 30) {
      console.error('Backend did not start. See logs/backend.log.');
      stopBackend();
      process.exit(1);
      return;
    }
    setTimeout(() => waitForBackend(attempt + 1), 1000);
  }
}
waitForBackend();
