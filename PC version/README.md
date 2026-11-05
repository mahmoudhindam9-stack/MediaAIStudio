# MediaAIStudio — PC version

This edition runs as a local web application in the default browser. It does not install an Android emulator or modify the Android edition.

## Install
1. Extract the repository or PC release folder to a permanent location.
2. Double-click `Install.bat`.
3. The installer checks for Node.js LTS and installs it with Windows Package Manager (winget) when missing.
4. It installs backend and web dependencies, builds the web UI, asks for Gemini and optional FAL API keys, and creates a Desktop shortcut.
5. Double-click the Desktop shortcut. MediaAIStudio opens at `http://127.0.0.1:4173`.

## Requirements
- Windows 10/11 with winget available for automatic Node.js installation.
- Internet access for setup, npm dependencies and AI provider requests.
- Gemini API key for image/video generation and transcription; FAL key is optional for supported 3D provider features.

If winget is unavailable, install Node.js LTS from https://nodejs.org/en/download and run Install.bat again.

## Local services
- Browser UI: http://127.0.0.1:4173
- Local backend: http://127.0.0.1:3100
- Backend secrets: `backend/.env` (never place provider keys in frontend VITE variables).
- Runtime logs: `logs/backend.log`

The local launcher starts the backend and serves the built web interface from one local origin. It injects the backend token server-side and proxies `/api/backend/*` to the backend. The token is not exposed in browser JavaScript.

## Uninstall
Run `Uninstall.bat` to remove the Desktop shortcut. It intentionally leaves Node.js installed because other programs may depend on it, and preserves app files and generated data.

## Vercel
For cloud hosting, deploy `web/` as the Vercel root directory and configure the server-side variables described in `web/README.md`. The local PC installer and cloud deployment are separate run modes.
