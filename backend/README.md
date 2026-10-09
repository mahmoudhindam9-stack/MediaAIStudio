# MediaAIStudio AI Backend

This Express service is the server-side provider bridge for MediaAIStudio. Provider credentials belong on this server, never inside the Android APK.

## Implemented routes

- POST /v1/ai/image/process — Gemini image edits and enhancement requests used by the Android cloud provider through the documented Interactions API.
- POST /v1/ai/image/generate — asynchronous image generation and TRELLIS jobs.
- POST /v1/ai/video/generate — Veo text/image-to-video jobs.
- GET /v1/ai/jobs/:jobId and GET /v1/ai/files/:filename — job state and authenticated generated outputs.
- GET /health — basic service health.

The backend currently stores job state in memory and generated files in temp_media/. Use persistent object storage and a durable queue for multi-instance production deployments. Add user-level authentication and rate limiting before making this service public. The shared Android app token is an app-to-backend gate only; any token embedded in an APK is extractable and is not a user identity.

## Configure

Copy backend/.env.example to backend/.env. Set:

- MEDIA_AI_BACKEND_API_KEY: app-to-backend token; the same value must be passed as MEDIA_AI_BACKEND_API_KEY when building the Android app.
- PUBLIC_BASE_URL: public origin of this backend, e.g. https://your-api.example.com (no /v1/ai suffix).
- GEMINI_API_KEY: Google AI Studio key for Gemini image processing and Veo video generation. GOOGLE_AI_API_KEY remains supported as an alias.
- FAL_KEY: fal API key for hosted TRELLIS image-to-3D.
- IMAGE_MODEL: defaults to gemini-3.1-flash-image.
- VIDEO_MODEL: defaults to veo-3.1-fast-generate-preview.
- TRELLIS_MODEL_ID: defaults to fal-ai/trellis.

Set Android build variables MEDIA_AI_BACKEND_URL=https://your-api.example.com/v1/ai and MEDIA_AI_BACKEND_API_KEY to the same app token. The provider keys GEMINI_API_KEY and FAL_KEY must remain only in the backend environment. Do not add them to the Android app .env or package them into the APK.

## TRELLIS notes

TRELLIS is a hosted image-to-3D operation. The app requests consent before upload, shows job progress, and can save the completed mesh as a .glb file. The backend submits the image to the fal-ai/trellis queue, polls the job, downloads the mesh, and serves it via the authenticated files route.

The upstream Microsoft TRELLIS project needs a Linux/CUDA/NVIDIA GPU environment for self-hosting; this app intentionally calls a hosted inference provider rather than downloading large model weights to the phone.

## Run locally

From the backend directory run npm install, copy .env.example to .env, fill in the settings, and run npm start. Use Node.js 18 or newer for global fetch. Use HTTPS for public production deployments and review provider pricing and terms before enabling user access.
