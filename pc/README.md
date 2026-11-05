# MediaAIStudio PC

Independent web workspace for MediaAIStudio, kept in the Android repository under `pc/` so Android sources and release behavior remain untouched. Deploy this directory as the Vercel project root.

## Current foundation
- USB webcams and capture cards through browser `getUserMedia`.
- Phone rear camera when this page is opened on a phone over HTTPS.
- HLS IP-camera streams where the camera/server exposes a browser-accessible stream and CORS permits playback.
- Frame capture and a local in-session capture tray.
- Responsive desktop/mobile layout.

## Important camera constraints
- Camera permissions require HTTPS (or localhost).
- Browsers cannot open RTSP directly. Typical IP cameras need a gateway that converts RTSP to WebRTC or HLS. This client currently accepts HLS/browser-playable URLs only; it does not pretend to support raw RTSP.
- USB devices are available only when connected to the computer running the browser.
- A phone camera is accessed by opening this web app on the phone; a desktop browser cannot silently use a remote phone camera.

## Development
```bash
npm install
npm run dev
npm run build
```

## Deployment
Create a Vercel project connected to this repository and set its Root Directory to `pc`. The Android app remains at the repository root and is not changed by this workspace.

## Next integration steps
1. Connect the existing MediaAIStudio backend with authenticated server-side configuration; never expose provider API keys in browser code.
2. Add an RTSP-to-WebRTC/HLS gateway for IP cameras.
3. Add the existing Android AI processing workflows as shared backend jobs where technically portable.
4. Add persistent media library, project storage, and optional Electron packaging for Windows.
