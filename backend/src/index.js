require('dotenv').config();
const express = require('express');
const cors = require('cors');
const apiRoutes = require('./api/generate');
const { initStorage } = require('./storage/StorageManager');

function isConfiguredSecret(value) {
    const normalized = String(value || '').trim();
    return normalized.length > 0 &&
        !/^(?:replace[-_ ]?with|change[-_ ]?me|your[-_ ]|placeholder\\b)/i.test(normalized);
}

function isPublicBaseUrlConfigured(value) {
    const normalized = String(value || '').trim();
    if (!normalized || /example\\.com|your-ai-backend/i.test(normalized)) return false;
    try {
        const parsed = new URL(normalized);
        return parsed.protocol === 'https:' &&
            Boolean(parsed.hostname) &&
            parsed.hostname !== 'localhost' &&
            parsed.hostname !== '127.0.0.1' &&
            !parsed.hostname.endsWith('.example.com');
    } catch {
        return false;
    }
}

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

// Initialize temporary storage directory
initStorage();

// Basic API routes
app.use('/v1/ai', apiRoutes);

app.get('/health', (req, res) => {
    const backendAuthConfigured = isConfiguredSecret(process.env.MEDIA_AI_BACKEND_API_KEY);
    const publicBaseUrlConfigured = isPublicBaseUrlConfigured(process.env.PUBLIC_BASE_URL);
    const geminiConfigured = isConfiguredSecret(process.env.GEMINI_API_KEY || process.env.GOOGLE_AI_API_KEY);
    const trellisConfigured = isConfiguredSecret(process.env.FAL_KEY);

    res.json({
        status: 'ok',
        readyForCloudOperations: backendAuthConfigured && publicBaseUrlConfigured && (geminiConfigured || trellisConfigured),
        imageModel: process.env.IMAGE_MODEL || 'gemini-nano-banana-2.1',
        videoModel: process.env.VIDEO_MODEL || 'veo-3.1-fast-generate-preview',
        backendAuthConfigured,
        publicBaseUrlConfigured,
        providers: { gemini: geminiConfigured, trellis: trellisConfigured },
        capabilities: {
            imageProcessing: geminiConfigured,
            imageGeneration: geminiConfigured,
            videoGeneration: geminiConfigured,
            videoTranscription: geminiConfigured,
            imageTo3D: trellisConfigured
        }
    });
});

app.listen(PORT, () => {
    console.log(`Backend listening on port ${PORT}`);
});
