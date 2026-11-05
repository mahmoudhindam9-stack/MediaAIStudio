# MediaAIStudio Web

React + Vite + TypeScript browser workspace for the existing MediaAIStudio backend. The native Android project is stored separately in `Android version/`; this folder contains the browser-based PC interface.

## Features
- Responsive dark dashboard and backend health status.
- Image processing and editing through the existing image process route.
- Text-to-image generation with optional reference image.
- Video generation with prompt and optional source media.
- Video transcription with language selection.
- Job progress polling, cancellation and authenticated result downloads.
- Provider capability checks and secure backend configuration.
- Vercel static hosting with a server-side API proxy.

## Requirements
Node.js 18 or newer, npm, and the existing backend deployed as a long-running Node service.

## Run locally
From this directory run npm install, then copy .env.example to .env.local. Set BACKEND_URL to your backend origin (for example http://localhost:3000) and MEDIA_AI_BACKEND_API_KEY to the same token configured on the backend. Start with npm run dev. Vite does not execute Vercel Functions; use Vercel CLI (vercel dev) after linking the project for end-to-end proxy tests. Run npm run build to build the static frontend.

## Environment variables
- BACKEND_URL (required, server only): backend origin without /v1/ai.
- MEDIA_AI_BACKEND_API_KEY (required, server only): shared app-to-backend bearer token.
- VITE_APP_NAME (optional): UI label.
- VITE_API_PROXY_BASE (optional): defaults to /api/backend.

Never define VITE_BACKEND_API_KEY. Vite embeds VITE-prefixed values in public browser assets. The server-side Vercel Function injects the token. GEMINI_API_KEY and FAL_KEY remain only on the backend host.

## Backend routes used
GET /health, GET /v1/ai/status, POST /v1/ai/image/process, POST /v1/ai/image/generate, POST /v1/ai/video/generate, POST /v1/ai/video/transcribe, GET /v1/ai/jobs/:jobId, DELETE /v1/ai/jobs/:jobId and GET /v1/ai/files/:filename. Uploads use FormData fields media, prompt, type, operation and language. Returned jobId is polled until COMPLETED, FAILED or CANCELLED.

## Deploy to Vercel
1. Import mahmoudhindam9-stack/MediaAIStudio into Vercel.
2. Set Root Directory to `PC version/web` and framework preset to Vite.
3. Build command npm run build; output directory dist; install command npm install.
4. Add BACKEND_URL and MEDIA_AI_BACKEND_API_KEY in Project Settings → Environment Variables.
5. Redeploy and test the Settings page.

## Important production constraints
The current backend uses an in-memory job Map and local temp_media storage and starts async work after returning HTTP 202. It must remain on a persistent long-running Node host, not Vercel serverless. Use durable queue and object storage before multi-instance production. Backend accepts files up to 20 MB, but Vercel Functions have a lower request payload ceiling (commonly 4.5 MB); larger uploads need a separately secured upload path or direct-to-backend flow. The shared token is not user identity; add user authentication, authorization and rate limiting before public multi-user launch.

## Project structure
web/api/backend/[...path].ts
web/src/api/{client.ts,backend.ts}
web/src/components/{Layout.tsx,FeatureForm.tsx,JobCard.tsx}
web/src/pages/{HomePage,DashboardPage,ImageProcessPage,ImageGeneratePage,VideoGeneratePage,VideoTranscribePage,JobsPage,GalleryPage,SettingsPage}.tsx
web/src/types/index.ts
web/src/App.tsx, main.tsx, index.css
web/package.json, vite.config.ts, tsconfig.json, index.html, .env.example, vercel.json, README.md
