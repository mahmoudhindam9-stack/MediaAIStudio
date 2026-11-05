import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Hls from 'hls.js';
import { Aperture, Camera, CameraOff, CheckCircle2, CircleHelp, Cpu, Monitor, Play, RefreshCw, Smartphone, Wifi, X } from 'lucide-react';
import { QRCodeSVG } from 'qrcode.react';
import { backend, jobForm } from '../api/backend';

type Source = 'usb' | 'phone' | 'ip' | 'wifi';
type AgentHealth = { status?: string; ffmpeg?: { available: boolean; version?: string | null }; rtsp?: { active: boolean; source?: string | null; error?: string | null }; pairCode?: string | null; pairCodeExpiresAt?: number };
type DeviceResult = { available: boolean; version?: string | null; devices: Array<{ name: string; kind: string }>; message?: string | null };
type OnvifDevice = { name: string; xaddr: string; xaddrs: string[]; scopes: string[] };

export default function CameraPage() {
  const navigate = useNavigate();
  const isLocalApp = ['localhost', '127.0.0.1'].includes(window.location.hostname);
  const agentBase = isLocalApp ? '/api/camera' : 'http://127.0.0.1:3101';
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const hlsRef = useRef<Hls | null>(null);\n  const phonePeerRef = useRef<RTCPeerConnection | null>(null);
  const [source, setSource] = useState<Source>('usb');
  const [devices, setDevices] = useState<MediaDeviceInfo[]>([]);
  const [deviceId, setDeviceId] = useState('');
  const [rtspUrl, setRtspUrl] = useState('');
  const [agent, setAgent] = useState<AgentHealth | null>(null);
  const [deviceResult, setDeviceResult] = useState<DeviceResult | null>(null);
  const [onvifDevices, setOnvifDevices] = useState<OnvifDevice[]>([]);
  const [discovering, setDiscovering] = useState(false);
  const [active, setActive] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [snapshot, setSnapshot] = useState('');
  const [aiBusy, setAiBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [agentToken, setAgentToken] = useState('');
  const [pairCodeInput, setPairCodeInput] = useState('');
  const [pairBusy, setPairBusy] = useState(false);
  const [wifiQrUrl, setWifiQrUrl] = useState('');
  const [wifiBusy, setWifiBusy] = useState(false);
  const [wifiStatus, setWifiStatus] = useState('');

  const agentFetch = useCallback(async <T,>(path: string, init: RequestInit = {}): Promise<T> => {
    const headers = new Headers(init.headers);
    if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
    if (!isLocalApp && agentToken) headers.set('Authorization', 'Bearer ' + agentToken);
    const response = await fetch(agentBase + path, { ...init, headers });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.message || data.error || 'The local camera companion is unavailable.');
    return data as T;
  }, [agentBase, agentToken, isLocalApp]);

  const refreshAgent = useCallback(async () => {
    if (!isLocalApp && !agentToken) { setAgent(null); return null; }
    try {
      const health = await agentFetch<AgentHealth>('/health');
      setAgent(health);
      return health;
    } catch (e) {
      setAgent(null);
      setError(e instanceof Error ? e.message : 'Cannot reach the local camera companion.');
      return null;
    }
  }, [agentFetch, agentToken, isLocalApp]);

  const refreshDevices = useCallback(async () => {
    try {
      const all = await navigator.mediaDevices.enumerateDevices();
      setDevices(all.filter(d => d.kind === 'videoinput'));
      if (isLocalApp || agentToken) {
        const result = await agentFetch<DeviceResult>('/devices');
        setDeviceResult(result);
      } else setDeviceResult(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not list camera devices.');
    }
  }, [agentFetch, agentToken, isLocalApp]);

  const stopCamera = useCallback(async () => {\n    phonePeerRef.current?.close();\n    phonePeerRef.current = null;
    streamRef.current?.getTracks().forEach(track => track.stop());
    streamRef.current = null;
    hlsRef.current?.destroy();
    hlsRef.current = null;
    if (videoRef.current) {
      videoRef.current.pause();
      videoRef.current.srcObject = null;
      videoRef.current.removeAttribute('src');
      videoRef.current.load();
    }
    try { await agentFetch('/rtsp/stop', { method: 'POST', body: '{}' }); } catch {}
    setActive(false);
  }, [agentFetch]);

  async function pairWithLocalAgent() {
    setPairBusy(true);
    setError('');
    try {
      const response = await fetch(agentBase + '/pair', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ code: pairCodeInput.trim() }) });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(data.message || 'Could not pair with the Windows camera companion.');
      setAgentToken(data.token);
      setMessage('Paired with the Windows camera companion. The connection expires in four hours.');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Pairing failed. Make sure MediaAIStudio is running on this PC.');
    } finally { setPairBusy(false); }
  }


  const waitForIce = (pc: RTCPeerConnection) => new Promise<void>(resolve => {
    if (pc.iceGatheringState === 'complete') return resolve();
    const timer = window.setTimeout(resolve, 12000);
    pc.addEventListener('icegatheringstatechange', () => {
      if (pc.iceGatheringState === 'complete') { window.clearTimeout(timer); resolve(); }
    });
  });

  async function createWifiPairing() {
    setWifiBusy(true); setError(''); setWifiQrUrl(''); setWifiStatus('Preparing secure phone connection…');
    try {
      let publicUrl: string | null = null;
      for (let i = 0; i < 30 && !publicUrl; i++) {
        const status = await fetch('/api/phone-relay/status').then(r => r.json());
        publicUrl = status.publicUrl || null;
        if (!publicUrl) await new Promise(resolve => window.setTimeout(resolve, 1000));
      }
      if (!publicUrl) throw new Error('Secure camera link is not ready. Install Cloudflare Tunnel (cloudflared) and restart MediaAIStudio.');
      const session = await fetch('/api/phone-relay/session', { method: 'POST' }).then(async r => {
        const d = await r.json(); if (!r.ok) throw new Error(d.error || 'Could not create a pairing session.'); return d;
      });
      const pc = new RTCPeerConnection({ iceServers: [{ urls: 'stun:stun.l.google.com:19302' }] });\n      phonePeerRef.current = pc;\n      pc.addTransceiver('video', { direction: 'recvonly' });
      const remote = new MediaStream();
      if (videoRef.current) videoRef.current.srcObject = remote;
      pc.ontrack = event => {
        event.streams[0]?.getTracks().forEach(track => remote.addTrack(track));
        if (videoRef.current) { videoRef.current.srcObject = remote; void videoRef.current.play(); }
        setActive(true); setWifiStatus('Phone camera connected successfully.');
      };
      pc.onconnectionstatechange = () => {
        if (pc.connectionState === 'failed' || pc.connectionState === 'disconnected') setWifiStatus('Phone connection interrupted. Keep both devices on the same Wi-Fi network.');
      };
      const offer = await pc.createOffer();
      await pc.setLocalDescription(offer);
      await waitForIce(pc);
      const posted = await fetch('/api/phone-relay/session/' + session.id, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ offer: pc.localDescription }) });
      if (!posted.ok) throw new Error('Could not publish the camera connection offer.');
      setWifiQrUrl(publicUrl + '/phone-camera.html?s=' + session.id);
      setWifiStatus('Scan the QR code using your phone camera, then allow camera access.');
      let connected = false;
      for (let i = 0; i < 120 && !connected; i++) {
        await new Promise(resolve => window.setTimeout(resolve, 1000));
        const response = await fetch('/api/phone-relay/session/' + session.id).then(r => r.json());
        if (response.answer) {
          await pc.setRemoteDescription(response.answer);
          connected = true;
          setWifiStatus('Phone camera connected. Live video is arriving.');
        }
      }
      if (!connected) setWifiStatus('Pairing timed out. Generate a new QR code and try again.');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not start the Wi-Fi camera connection.');
      setWifiStatus('');
    } finally { setWifiBusy(false); }
  }

  async function discoverOnvif() {
    if (!isLocalApp && !agentToken) { setError('Pair this browser with the Windows camera companion first.'); return; }
    setDiscovering(true);
    setError('');
    try {
      const result = await agentFetch<{ devices: OnvifDevice[]; message?: string | null }>('/onvif/discover', { method: 'POST', body: '{}' });
      setOnvifDevices(result.devices || []);
      setMessage(result.devices.length ? 'Found ' + result.devices.length + ' ONVIF camera endpoint(s). Use the device address to complete the RTSP stream URL.' : 'No ONVIF cameras replied. Check that the PC and cameras are on the same network and that multicast is allowed.');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'ONVIF discovery failed.');
    } finally { setDiscovering(false); }
  }

  async function connectCamera() {
    setError('');
    setMessage('');
    setBusy(true);
    await stopCamera();
    try {
      const video = videoRef.current;
      if (!video) return;
      if (source === 'ip') {
        if (!isLocalApp && !agentToken) throw new Error('Pair this browser with the Windows camera companion first.');
        if (!rtspUrl.trim()) throw new Error('Enter the IP camera RTSP address first.');
        const result = await agentFetch<{ playlist: string }>('/rtsp/start', {
          method: 'POST', body: JSON.stringify({ url: rtspUrl.trim() })
        });
        const playlist = isLocalApp ? result.playlist : agentBase + result.playlist.replace('/api/camera', '');
        if (Hls.isSupported()) {
          const hls = new Hls({ enableWorker: true, lowLatencyMode: true, liveSyncDurationCount: 2, xhrSetup: (xhr) => { if (agentToken) xhr.setRequestHeader('Authorization', 'Bearer ' + agentToken); } });
          hlsRef.current = hls;
          hls.loadSource(playlist);
          hls.attachMedia(video);
          hls.on(Hls.Events.ERROR, (_event, data) => {
            if (data.fatal) setError('The RTSP bridge could not play this camera. Check the camera credentials, network and FFmpeg installation.');
          });
          await new Promise<void>((resolve, reject) => {
            const timer = window.setTimeout(() => reject(new Error('The camera stream did not start within 20 seconds.')), 20000);
            hls.on(Hls.Events.MANIFEST_PARSED, () => { window.clearTimeout(timer); void video.play().then(resolve).catch(reject); });
            hls.on(Hls.Events.ERROR, (_event, data) => { if (data.fatal) { window.clearTimeout(timer); reject(new Error('FFmpeg could not decode this RTSP stream.')); } });
          });
        } else {
          video.src = playlist;
          await video.play();
        }
      } else {
        if (!navigator.mediaDevices?.getUserMedia) throw new Error('Camera access requires HTTPS or localhost and a modern browser.');
        const constraints: MediaStreamConstraints = {
          audio: false,
          video: source === 'phone'
            ? { facingMode: { ideal: 'environment' }, width: { ideal: 1920 }, height: { ideal: 1080 } }
            : { deviceId: deviceId ? { exact: deviceId } : undefined, width: { ideal: 1920 }, height: { ideal: 1080 } }
        };
        const stream = await navigator.mediaDevices.getUserMedia(constraints);
        streamRef.current = stream;
        video.srcObject = stream;
        await video.play();
        await refreshDevices();
      }
      setActive(true);
      await refreshAgent();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unable to start the selected camera.');
      await stopCamera();
    } finally { setBusy(false); }
  }

  function capture() {
    const video = videoRef.current;
    if (!video || !video.videoWidth) return;
    const canvas = document.createElement('canvas');
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    canvas.getContext('2d')?.drawImage(video, 0, 0);
    setSnapshot(canvas.toDataURL('image/jpeg', 0.92));
    setMessage('Snapshot ready. You can send it to the AI image tools.');
  }

  async function sendToAi() {
    if (!snapshot) return;
    setAiBusy(true);
    setError('');
    try {
      const blob = await (await fetch(snapshot)).blob();
      const file = new File([blob], 'camera-capture.jpg', { type: 'image/jpeg' });
      await backend.submit('/image/process', jobForm({
        prompt: 'Analyze this camera frame and improve its visual quality while preserving the original scene and details.',
        type: 'IMAGE_PROCESS',
        operation: 'ENHANCE',
        file
      }));
      setMessage('Camera frame sent to AI processing. Opening the job tracker…');
      navigate('/jobs');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'AI processing could not be started. Check provider configuration.');
    } finally { setAiBusy(false); }
  }

  useEffect(() => {
    void refreshAgent();
    void refreshDevices();
    const agentRetry = window.setInterval(() => { void refreshAgent(); }, 5000);
    return () => {
      window.clearInterval(agentRetry);
      streamRef.current?.getTracks().forEach(track => track.stop());
      hlsRef.current?.destroy();
      void agentFetch('/rtsp/stop', { method: 'POST', body: '{}' });
    };
  }, [agentFetch, refreshAgent, refreshDevices, agentToken, isLocalApp, agentBase]);

  return <div>
    <div className="intro">
      <div className="eyebrow">LOCAL CAMERA COMPANION</div>
      <h1>Camera studio</h1>
      <p>Connect USB webcams and network cameras to the Windows AI workspace. Camera access stays on this PC unless you choose to submit a captured frame to an AI provider.</p>
    </div>
    {!isLocalApp && !agentToken && <section className="panel camera-pair-panel"><div className="eyebrow">SECURE LOCAL CONNECTION</div><h2>Pair with your Windows PC</h2><p>Open MediaAIStudio on the Windows PC, go to Camera studio, and copy the one-time pairing code shown there. Enter it below to let this Vercel page use that PC's local camera companion.</p><div className="camera-pair-row"><input inputMode="numeric" maxLength={8} value={pairCodeInput} onChange={e => setPairCodeInput(e.target.value.replace(/\D/g, ''))} placeholder="8-digit pairing code"/><button className="primary" disabled={pairBusy || pairCodeInput.length !== 8} onClick={() => void pairWithLocalAgent()}>{pairBusy ? 'Pairing…' : 'Pair this browser'}</button></div><small>The code is one-use, expires after 10 minutes, and the paired session expires after four hours. This requires the PC companion to be running on the same computer as this browser.</small></section>}
    {isLocalApp && agent?.pairCode && <section className="panel camera-pair-panel"><div className="eyebrow">PAIR VERCEL WITH THIS PC</div><h2>One-time pairing code</h2><div className="camera-pair-code">{agent.pairCode}</div><p>Enter this code in Camera studio when opened from the Vercel site on this same PC. It expires in 10 minutes and can be used once.</p></section>}
    <div className="camera-layout">
      <section className="panel camera-controls">
        <div className="camera-title"><div><div className="eyebrow">INPUT SOURCE</div><h2>Choose a camera</h2></div><span className={active ? 'camera-live' : 'camera-idle'}>{active ? '● LIVE' : '● STANDBY'}</span></div>
        <div className="camera-source-grid">
          <button className={source === 'usb' ? 'camera-source selected' : 'camera-source'} onClick={() => { void stopCamera(); setSource('usb'); }}><Monitor/><b>USB / PC</b><small>Webcam and capture card</small></button>
          <button className={source === 'phone' ? 'camera-source selected' : 'camera-source'} onClick={() => { void stopCamera(); setSource('phone'); }}><Smartphone/><b>Phone camera</b><small>Use the rear camera on this device</small></button>
          <button className={source === 'ip' ? 'camera-source selected' : 'camera-source'} onClick={() => { void stopCamera(); setSource('ip'); }}><Wifi/><b>IP / RTSP</b><small>Bridge network streams through the paired PC</small></button>
          <button className={source === 'wifi' ? 'camera-source selected' : 'camera-source'} onClick={() => { void stopCamera(); setSource('wifi'); setWifiQrUrl(''); }}><Smartphone/><b>Wi-Fi phone</b><small>Scan QR to send phone live camera</small></button>
        </div>
        {source === 'usb' && <div className="camera-field"><label>Browser camera</label><select value={deviceId} onChange={e => setDeviceId(e.target.value)}><option value="">Default camera</option>{devices.map((d, i) => <option key={d.deviceId} value={d.deviceId}>{d.label || 'Camera ' + (i + 1)}</option>)}</select><button className="camera-link" onClick={() => void refreshDevices()}><RefreshCw size={14}/> Refresh devices</button><small>{deviceResult?.available ? deviceResult.devices.length + ' DirectShow camera(s) detected by Windows' : 'Browser camera access is available; install FFmpeg to enable Windows-level device discovery.'}</small></div>}
        {source === 'wifi' && <div className="camera-field"><div className="camera-tip"><Wifi size={18}/><span>Connect your phone to the same Wi-Fi network. A temporary secure link will open the camera page after scanning.</span></div><button className="primary" disabled={wifiBusy} onClick={() => void createWifiPairing()}>{wifiBusy ? 'Preparing QR code…' : 'Generate phone connection QR'}</button>{wifiQrUrl && <div className="wifi-qr-panel"><QRCodeSVG value={wifiQrUrl} size={210} bgColor="#ffffff" fgColor="#10131a" level="H"/><b>Scan with your phone</b><small>{wifiStatus}</small><button className="camera-link" onClick={() => void navigator.clipboard.writeText(wifiQrUrl)}>Copy secure phone link</button><button className="secondary" onClick={() => { void stopCamera(); setWifiQrUrl(''); setWifiStatus('Phone camera disconnected.'); }}>Disconnect phone</button></div>}</div>}
        {source === 'phone' && <div className="camera-tip"><Smartphone size={18}/><span>This uses the rear camera of the device currently viewing the page. If opened on a phone over HTTPS, it can capture from that phone; live phone-to-PC video relay is a separate feature and is not enabled yet.</span></div>}
        {source === 'ip' && <div className="camera-field"><label>RTSP camera address</label><input type="password" autoComplete="off" value={rtspUrl} onChange={e => setRtspUrl(e.target.value)} placeholder="rtsp://username:password@192.168.1.20:554/stream1"/><small>Credentials are passed to the local FFmpeg process and are not intentionally written to app logs.</small><button className="camera-link" disabled={discovering} onClick={() => void discoverOnvif()}><Wifi size={14}/>{discovering ? 'Searching network…' : 'Discover ONVIF cameras'}</button>{onvifDevices.length > 0 && <div className="onvif-list">{onvifDevices.map(device => <div className="onvif-item" key={device.xaddr}><b>{device.name}</b><small>{device.xaddr}</small><button className="camera-link" onClick={async () => { try { const host = new URL(device.xaddr).hostname; await navigator.clipboard.writeText(host); setMessage('Copied camera host ' + host + '. Enter the RTSP path supplied by the camera manufacturer.'); } catch { setMessage('ONVIF endpoint: ' + device.xaddr); } }}>Copy camera host</button></div>)}</div>}{!agent?.ffmpeg?.available && <div className="camera-warning">FFmpeg is not detected. Install FFmpeg and restart the app to enable RTSP-to-HLS conversion.</div>}</div>}
        <div className="camera-actions"><button className="primary" disabled={busy || source === 'wifi' || (source === 'ip' && !agent?.ffmpeg?.available)} onClick={active ? () => void stopCamera() : () => void connectCamera()}>{active ? <><CameraOff size={16}/> Stop camera</> : <><Play size={16}/>{busy ? 'Connecting…' : 'Connect camera'}</>}</button><button className="secondary" disabled={!active} onClick={capture}><Camera size={16}/> Capture frame</button></div>
        {error && <div className="error camera-error"><span>{error}</span><button onClick={() => setError('')}><X size={15}/></button></div>}
        {message && <div className="camera-success"><CheckCircle2 size={15}/>{message}</div>}
      </section>
      <section className="panel camera-preview">
        <div className="camera-title"><div><div className="eyebrow">LIVE MONITOR</div><h2>Camera preview</h2></div><span className="camera-agent"><Cpu size={14}/> {agent?.ffmpeg?.available ? 'FFmpeg ready' : 'Browser mode'}</span></div>
        <div className="camera-stage"><video ref={videoRef} playsInline muted autoPlay/>{!active && <div className="camera-empty"><Aperture size={34}/><b>Waiting for camera</b><small>Choose an input and connect to see the live feed.</small></div>}{active && <span className="camera-live-pill">● LIVE</span>}</div>
        {snapshot && <div className="snapshot-row"><img src={snapshot} alt="Captured camera frame"/><div><b>Latest snapshot</b><small>Ready for AI image processing</small><button className="primary" disabled={aiBusy} onClick={() => void sendToAi()}>{aiBusy ? 'Sending…' : 'Send snapshot to AI'}</button></div></div>}
      </section>
    </div>
    <div className="camera-footnote"><CircleHelp size={15}/><span>RTSP decoding runs locally through FFmpeg. The browser never receives the camera password as a public API key. AI upload happens only after you press “Send snapshot to AI”.</span></div>
  </div>;
}
