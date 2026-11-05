import { useEffect, useRef, useState } from "react";
import Hls from "hls.js";
import { Aperture, Camera, CameraOff, CircleHelp, FolderOpen, ImagePlus, Monitor, Play, Radio, Settings2, ShieldCheck, Smartphone, Video, Wifi, X } from "lucide-react";

type Source = "usb" | "phone" | "ip";
export default function App() {
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const hlsRef = useRef<Hls | null>(null);
  const [source, setSource] = useState<Source>("usb");
  const [devices, setDevices] = useState<MediaDeviceInfo[]>([]);
  const [deviceId, setDeviceId] = useState("");
  const [ipUrl, setIpUrl] = useState("");
  const [active, setActive] = useState(false);
  const [error, setError] = useState("");
  const [shots, setShots] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);

  async function refreshDevices() {
    try {
      const all = await navigator.mediaDevices.enumerateDevices();
      setDevices(all.filter(d => d.kind === "videoinput"));
    } catch { setError("تعذر الوصول إلى قائمة الكاميرات. تحقق من صلاحية المتصفح."); }
  }

  function stopCamera() {
    streamRef.current?.getTracks().forEach(t => t.stop());
    streamRef.current = null;
    hlsRef.current?.destroy();
    hlsRef.current = null;
    if (videoRef.current) videoRef.current.srcObject = null;
    setActive(false);
  }

  async function startCamera() {
    setError("");
    stopCamera();
    setBusy(true);
    try {
      if (source === "ip") {
        if (!ipUrl.trim()) throw new Error("أدخل رابط بث الكاميرا أولاً.");
        const video = videoRef.current;
        if (!video) return;
        if (ipUrl.toLowerCase().includes(".m3u8") && Hls.isSupported()) {
          const hls = new Hls({ enableWorker: true });
          hlsRef.current = hls;
          hls.loadSource(ipUrl.trim());
          hls.attachMedia(video);
          hls.on(Hls.Events.ERROR, (_event, data) => {
            if (data.fatal) setError("فشل تشغيل بث IP. تأكد من الرابط وCORS وإتاحة البث عبر HTTPS.");
          });
        } else {
          video.src = ipUrl.trim();
        }
        await video.play();
      } else {
        if (!navigator.mediaDevices?.getUserMedia) throw new Error("الكاميرا تتطلب HTTPS أو localhost ومتصفحاً يدعم getUserMedia.");
        const stream = await navigator.mediaDevices.getUserMedia({
          audio: false,
          video: source === "phone"
            ? { facingMode: { ideal: "environment" }, width: { ideal: 1920 }, height: { ideal: 1080 } }
            : { deviceId: deviceId ? { exact: deviceId } : undefined, width: { ideal: 1920 }, height: { ideal: 1080 } }
        });
        streamRef.current = stream;
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          await videoRef.current.play();
        }
        await refreshDevices();
      }
      setActive(true);
    } catch (e) {
      setError(e instanceof Error ? e.message : "تعذر تشغيل الكاميرا.");
      stopCamera();
    } finally { setBusy(false); }
  }

  function capture() {
    const video = videoRef.current;
    if (!video || !video.videoWidth) return;
    const canvas = document.createElement("canvas");
    canvas.width = video.videoWidth; canvas.height = video.videoHeight;
    canvas.getContext("2d")?.drawImage(video, 0, 0);
    setShots(prev => [canvas.toDataURL("image/jpeg", .92), ...prev]);
  }

  useEffect(() => {
    refreshDevices();
    return () => {
      streamRef.current?.getTracks().forEach(t => t.stop());
      hlsRef.current?.destroy();
    };
  }, []);

  return <main className="app-shell" dir="ltr">
    <aside className="sidebar">
      <div className="brand"><span className="brand-mark"><Aperture size={22}/></span><span>MediaAIStudio<small>CREATIVE WORKSPACE</small></span></div>
      <div className="side-label">WORKSPACE</div>
      <button className="nav active"><Video size={18}/> Camera Studio</button>
      <button className="nav" onClick={() => document.getElementById("library")?.scrollIntoView({behavior:"smooth"})}><FolderOpen size={18}/> Media Library <span className="count">{shots.length}</span></button>
      <div className="side-label">TOOLS</div>
      <button className="nav" onClick={() => setError("أدوات الذكاء الاصطناعي ستُربط بخدمات MediaAIStudio الحالية في المرحلة التالية.")}><Aperture size={18}/> AI Enhancements</button>
      <button className="nav" onClick={() => setError("إعدادات التطبيق ستُضاف مع ربط الحسابات والخدمات.")}><Settings2 size={18}/> Settings</button>
      <div className="sidebar-bottom"><ShieldCheck size={16}/> Local camera access<br/><span>Only after your permission</span></div>
    </aside>

    <section className="workspace">
      <header className="topbar">
        <div><div className="eyebrow">CAPTURE / INPUT</div><h1>Camera Studio</h1></div>
        <div className="top-actions"><span className="secure"><span/> Secure session</span><button className="icon-btn" title="Help" onClick={() => setError("USB camera: connect and select it. Phone: open this page on your phone. IP: use an HTTPS HLS or browser-compatible stream URL.")}><CircleHelp size={18}/></button></div>
      </header>

      <div className="content">
        <section className="source-panel">
          <div className="section-heading"><div><span className="eyebrow">INPUT SOURCE</span><h2>Choose your camera</h2></div><span className="source-status"><span className={active ? "dot live" : "dot"}/>{active ? "LIVE" : "NOT CONNECTED"}</span></div>
          <div className="source-grid">
            <button className={source==="usb"?"source-card selected":"source-card"} onClick={() => {stopCamera();setSource("usb");}}><Monitor/><b>USB / PC Camera</b><small>Webcam and capture cards</small></button>
            <button className={source==="phone"?"source-card selected":"source-card"} onClick={() => {stopCamera();setSource("phone");}}><Smartphone/><b>Phone Camera</b><small>Use this page on your phone</small></button>
            <button className={source==="ip"?"source-card selected":"source-card"} onClick={() => {stopCamera();setSource("ip");}}><Wifi/><b>IP Camera</b><small>Network stream input</small></button>
          </div>
          {source==="usb" && <div className="source-config"><label>Connected camera</label><select value={deviceId} onChange={e=>setDeviceId(e.target.value)}><option value="">Default camera</option>{devices.map((d,i)=><option key={d.deviceId} value={d.deviceId}>{d.label || `Camera ${i+1}`}</option>)}</select><button className="text-btn" onClick={refreshDevices}>Refresh devices</button></div>}
          {source==="phone" && <div className="hint"><Smartphone size={18}/> Open the deployed PC workspace from your phone browser and allow camera access. The rear camera is preferred automatically.</div>}
          {source==="ip" && <div className="source-config"><label>HLS / browser stream URL</label><input value={ipUrl} onChange={e=>setIpUrl(e.target.value)} placeholder="https://camera.example/live/stream.m3u8"/><p className="note">RTSP links cannot play directly in browsers. An RTSP-to-WebRTC/HLS gateway will be required for most IP cameras.</p></div>}
          <div className="control-row"><button className="primary" disabled={busy} onClick={active?stopCamera:startCamera}>{active?<><CameraOff size={17}/> Stop camera</>:<><Play size={17}/> {busy?"Connecting…":"Connect camera"}</>}</button><button className="secondary" disabled={!active} onClick={capture}><Camera size={17}/> Capture frame</button></div>
          {error && <div className="notice" role="status"><span>{error}</span><button onClick={()=>setError("")}><X size={16}/></button></div>}
        </section>

        <section className="preview-panel">
          <div className="preview-head"><div><span className="eyebrow">LIVE MONITOR</span><h2>Preview</h2></div><span className="resolution">{active?"SOURCE ACTIVE":"AWAITING INPUT"}</span></div>
          <div className="video-stage">
            <video ref={videoRef} playsInline muted autoPlay/>
            {!active && <div className="empty-state"><div className="empty-icon"><Camera size={30}/></div><b>Your camera preview will appear here</b><span>Connect a USB webcam, phone camera, or supported IP stream.</span></div>}
            {active && <span className="live-pill"><span/> LIVE</span>}
          </div>
          <div className="preview-foot"><span><Radio size={15}/> {active?"Input connected":"No active input"}</span><span>MediaAIStudio · PC workspace</span></div>
        </section>

        <section className="library-panel" id="library">
          <div className="section-heading"><div><span className="eyebrow">CAPTURED MEDIA</span><h2>Recent captures</h2></div><span className="count large">{shots.length} items</span></div>
          {shots.length===0 ? <div className="library-empty"><ImagePlus size={22}/><span>Your captured frames will be collected here.</span></div> : <div className="shot-grid">{shots.map((s,i)=><a className="shot" key={i} href={s} download={`mediaaistudio-capture-${i+1}.jpg`}><img src={s}/><span>Capture {shots.length-i}</span></a>)}</div>}
        </section>
      </div>
    </section>
  </main>;
}