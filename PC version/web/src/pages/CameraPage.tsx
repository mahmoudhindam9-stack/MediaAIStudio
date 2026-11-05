import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Hls from 'hls.js';
import { Aperture, Camera, CameraOff, CheckCircle2, CircleHelp, Cpu, Monitor, Play, RefreshCw, Smartphone, Wifi, X } from 'lucide-react';
import { backend, jobForm } from '../api/backend';

type Source = 'usb' | 'phone' | 'ip';
type AgentHealth = { status?: string; ffmpeg?: { available: boolean; version?: string | null }; rtsp?: { active: boolean; source?: string | null; error?: string | null } };
type DeviceResult = { available: boolean; version?: string | null; devices: Array<{ name: string; kind: string }>; message?: string | null };
type OnvifDevice = { name: string; xaddr: string; xaddrs: string[]; scopes: string[] };

export default function CameraPage() {
  const navigate = useNavigate();
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const hlsRef = useRef<Hls | null>(null);
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

  const agentFetch = useCallback(async <T,>(path: string, init: RequestInit = {}): Promise<T> => {
    const response = await fetch('/api/camera' + path, {
      ...init,
      headers: { ...(init.body ? { 'Content-Type': 'application/json' } : {}), ...init.headers }
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.message || data.error || 'The local camera companion is unavailable.');
    return data as T;
  }, []);

  const refreshAgent = useCallback(async () => {
    try {
      const health = await agentFetch<AgentHealth>('/health');
      setAgent(health);
      return health;
    } catch (e) {
      setAgent(null);
      setError(e instanceof Error ? e.message : 'Cannot reach the local camera companion.');
      return null;
    }
  }, [agentFetch]);

  const refreshDevices = useCallback(async () => {
    try {
      const all = await navigator.mediaDevices.enumerateDevices();
      setDevices(all.filter(d => d.kind === 'videoinput'));
      const result = await agentFetch<DeviceResult>('/devices');
      setDeviceResult(result);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not list camera devices.');
    }
  }, [agentFetch]);

  const stopCamera = useCallback(async () => {
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

  async function discoverOnvif() {
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
        if (!rtspUrl.trim()) throw new Error('Enter the IP camera RTSP address first.');
        const result = await agentFetch<{ playlist: string }>('/rtsp/start', {
          method: 'POST', body: JSON.stringify({ url: rtspUrl.trim() })
        });
        if (Hls.isSupported()) {
          const hls = new Hls({ enableWorker: true, lowLatencyMode: true, liveSyncDurationCount: 2 });
          hlsRef.current = hls;
          hls.loadSource(result.playlist);
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
          video.src = result.playlist;
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
  }, [agentFetch, refreshAgent, refreshDevices]);

  return <div>
    <div className="intro">
      <div className="eyebrow">LOCAL CAMERA COMPANION</div>
      <h1>Camera studio</h1>
      <p>Connect USB webcams and network cameras to the Windows AI workspace. Camera access stays on this PC unless you choose to submit a captured frame to an AI provider.</p>
    </div>
    <div className="camera-layout">
      <section className="panel camera-controls">
        <div className="camera-title"><div><div className="eyebrow">INPUT SOURCE</div><h2>Choose a camera</h2></div><span className={active ? 'camera-live' : 'camera-idle'}>{active ? '● LIVE' : '● STANDBY'}</span></div>
        <div className="camera-source-grid">
          <button className={source === 'usb' ? 'camera-source selected' : 'camera-source'} onClick={() => { void stopCamera(); setSource('usb'); }}><Monitor/><b>USB / PC</b><small>Webcam and capture card</small></button>
          <button className={source === 'phone' ? 'camera-source selected' : 'camera-source'} onClick={() => { void stopCamera(); setSource('phone'); }}><Smartphone/><b>Phone camera</b><small>Use the rear camera on this device</small></button>
          <button className={source === 'ip' ? 'camera-source selected' : 'camera-source'} onClick={() => { void stopCamera(); setSource('ip'); }}><Wifi/><b>IP / RTSP</b><small>Bridge network streams locally</small></button>
        </div>
        {source === 'usb' && <div className="camera-field"><label>Browser camera</label><select value={deviceId} onChange={e => setDeviceId(e.target.value)}><option value="">Default camera</option>{devices.map((d, i) => <option key={d.deviceId} value={d.deviceId}>{d.label || 'Camera ' + (i + 1)}</option>)}</select><button className="camera-link" onClick={() => void refreshDevices()}><RefreshCw size={14}/> Refresh devices</button><small>{deviceResult?.available ? deviceResult.devices.length + ' DirectShow camera(s) detected by Windows' : 'Browser camera access is available; install FFmpeg to enable Windows-level device discovery.'}</small></div>}
        {source === 'phone' && <div className="camera-tip"><Smartphone size={18}/><span>This option uses the rear camera of the device currently viewing the page. The local Windows-only URL cannot be opened from a phone; phone-to-PC relay and secure pairing are not included yet.</span></div>}
        {source === 'ip' && <div className="camera-field"><label>RTSP camera address</label><input type="password" autoComplete="off" value={rtspUrl} onChange={e => setRtspUrl(e.target.value)} placeholder="rtsp://username:password@192.168.1.20:554/stream1"/><small>Credentials are passed to the local FFmpeg process and are not intentionally written to app logs.</small><button className="camera-link" disabled={discovering} onClick={() => void discoverOnvif()}><Wifi size={14}/>{discovering ? 'Searching network…' : 'Discover ONVIF cameras'}</button>{onvifDevices.length > 0 && <div className="onvif-list">{onvifDevices.map(device => <div className="onvif-item" key={device.xaddr}><b>{device.name}</b><small>{device.xaddr}</small><button className="camera-link" onClick={async () => { try { const host = new URL(device.xaddr).hostname; await navigator.clipboard.writeText(host); setMessage('Copied camera host ' + host + '. Enter the RTSP path supplied by the camera manufacturer.'); } catch { setMessage('ONVIF endpoint: ' + device.xaddr); } }}>Copy camera host</button></div>)}</div>}{!agent?.ffmpeg?.available && <div className="camera-warning">FFmpeg is not detected. Install FFmpeg and restart the app to enable RTSP-to-HLS conversion.</div>}</div>}
        <div className="camera-actions"><button className="primary" disabled={busy || (source === 'ip' && !agent?.ffmpeg?.available)} onClick={active ? () => void stopCamera() : () => void connectCamera()}>{active ? <><CameraOff size={16}/> Stop camera</> : <><Play size={16}/>{busy ? 'Connecting…' : 'Connect camera'}</>}</button><button className="secondary" disabled={!active} onClick={capture}><Camera size={16}/> Capture frame</button></div>
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
