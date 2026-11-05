# MediaAIStudio PC 1.8.0

This edition runs as a local web application in the default browser. It does not install an Android emulator or modify the Android edition.

## Install
1. Extract the repository or PC release folder to a permanent location (do not run it from inside the ZIP preview).
2. Double-click `Install.bat`.
3. The installer checks for Node.js LTS and installs it with Windows Package Manager (winget) when missing.
4. It installs backend and web dependencies and builds the browser workspace, including the Camera studio page.
5. It asks for Gemini and optional FAL API keys, then creates a verified Desktop shortcut and a Start Menu shortcut.
6. Double-click the MediaAIStudio shortcut. The local workspace opens at `http://127.0.0.1:4173`.

If the Desktop shortcut is missing, run `CreateShortcut.ps1` from PowerShell in the extracted MediaAIStudio folder. The installer also creates a Start Menu shortcut under MediaAIStudio.

## Requirements
- Windows 10/11 with winget available for automatic Node.js installation.
- Internet access for setup, npm dependencies and AI provider requests.
- Gemini API key for image/video generation and transcription; FAL key is optional for supported 3D provider features.

If winget is unavailable, install Node.js LTS from https://nodejs.org/en/download and run Install.bat again.

## Local services
- Browser UI: http://127.0.0.1:4173
- Local backend: http://127.0.0.1:3100
- Camera companion: http://127.0.0.1:3101 (loopback only)
- Backend secrets: `backend/.env` (never place provider keys in frontend VITE variables).
- Runtime logs: `logs/backend.log`

The local launcher starts the backend and camera companion, then serves the built web interface from one local origin. It injects the backend token server-side and proxies `/api/backend/*` and `/api/camera/*` to their local services. The token is not exposed in browser JavaScript.

## Camera studio
Open **Camera studio** from the left navigation inside the PC app.

- **USB / PC webcams:** use the browser camera permission prompt and select a connected webcam. FFmpeg is optional for Windows-level DirectShow device enumeration.
- **IP cameras:** install FFmpeg and enter an `rtsp://` or `rtsps://` URL in Camera studio. FFmpeg converts the stream to a temporary local HLS playlist. ONVIF discovery can find compatible cameras on the same local network.
- **Phone camera:** uses the camera on the device currently viewing the app. Opening the PC app on the PC uses the PC's available camera; it does not relay a phone's live camera feed to the PC.
- **Phone-to-PC live relay:** not included in this release.
- Camera credentials are passed to the local FFmpeg process. They are not intentionally written to app logs or sent to the AI provider. Avoid reusing administrator passwords where possible.
- Captured frames stay in the browser until you explicitly choose **Send snapshot to AI**. Only then are they uploaded to the configured AI backend.
- The camera companion binds to loopback only; it is not exposed to the LAN or public internet.

### Pairing the Vercel camera page with the local Windows helper
1. Start the Windows app and open Camera studio locally. It displays a one-time 8-digit pairing code.
2. Open the Vercel Camera studio page in a browser on that same Windows PC and enter the code.
3. The helper accepts one pairing, allows the paired browser session for four hours, and keeps the camera companion bound to 127.0.0.1. The browser token remains in memory and is not saved to local storage.
4. The helper permits HTTPS Vercel origins for this pairing endpoint. Browser local-network permission prompts may appear depending on browser version and policy.

The one-time code expires after 10 minutes and is rate-limited. Restart the local app to generate a fresh code after it is used or expires. The phone camera can be used when the page is opened on the phone, but this does not yet relay live phone video to the PC.

## Uninstall
Run `Uninstall.bat` to remove the Desktop shortcut. It intentionally leaves Node.js installed because other programs may depend on it, and preserves app files and generated data.

## Vercel
For cloud hosting, deploy `web/` as the Vercel root directory and configure the server-side variables described in `web/README.md`. The local PC installer and cloud deployment are separate run modes.
