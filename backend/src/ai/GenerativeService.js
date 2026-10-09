const fs = require('fs');
const path = require('path');
const { randomUUID } = require('crypto');
const JobManager = require('../jobs/JobManager');
const { getFilePath, STORAGE_DIR } = require('../storage/StorageManager');

const GEMINI_BASE_URL = 'https://generativelanguage.googleapis.com/v1beta';
const FAL_QUEUE_BASE_URL = 'https://queue.fal.run';
const IMAGE_MODEL = (process.env.IMAGE_MODEL || 'gemini-nano-banana-2.1').replace(/^models\//, '');
const VIDEO_MODEL = (process.env.VIDEO_MODEL || 'veo-3.1-fast-generate-preview').replace(/^models\//, '');
const TRELLIS_MODEL_ID = process.env.TRELLIS_MODEL_ID || 'fal-ai/trellis';
const MAX_TRELLIS_POLLS = Number(process.env.TRELLIS_MAX_POLLS || 180);
const TRELLIS_POLL_INTERVAL_MS = Number(process.env.TRELLIS_POLL_INTERVAL_MS || 3000);

function getGoogleApiKey() {
    return process.env.GEMINI_API_KEY || process.env.GOOGLE_AI_API_KEY || '';
}

function publicOutputUrl(filename) {
    const port = process.env.PORT || 3000;
    const publicBase = (process.env.PUBLIC_BASE_URL || ('http://localhost:' + port)).replace(/\/+$/, '');
    return publicBase + '/v1/ai/files/' + encodeURIComponent(filename);
}

function updateFailure(jobId, code, message) {
    JobManager.updateJob(jobId, 'FAILED', 0, message, { error: code });
}

async function readSource(job) {
    if (!job.request.filename) return null;
    const sourcePath = getFilePath(path.basename(job.request.filename));
    const buffer = await fs.promises.readFile(sourcePath);
    return {
        buffer,
        path: sourcePath,
        mimeType: job.request.mimeType || 'application/octet-stream'
    };
}

async function saveOutput(buffer, extension) {
    const filename = 'output_' + randomUUID() + extension;
    await fs.promises.writeFile(path.join(STORAGE_DIR, filename), buffer);
    return { filename, outputUrl: publicOutputUrl(filename) };
}

function decodeMaskImage(maskData) {
    const raw = String(maskData || '').trim();
    if (!raw) return null;

    const dataUri = raw.match(/^data:(image\/[A-Za-z0-9.+-]+);base64,([\s\S]+)$/i);
    if (raw.startsWith('data:') && !dataUri) {
        throw Object.assign(new Error('The selection mask must be a base64-encoded image.'), { code: 'INVALID_MASK_DATA' });
    }

    const mimeType = dataUri ? dataUri[1].toLowerCase() : 'image/png';
    const encoded = (dataUri ? dataUri[2] : raw).replace(/\s+/g, '').replace(/-/g, '+').replace(/_/g, '/');
    if (!encoded || encoded.length > 14 * 1024 * 1024 || !/^[A-Za-z0-9+/]+={0,2}$/.test(encoded)) {
        throw Object.assign(new Error('The selection mask is invalid or exceeds the 10 MB limit.'), { code: 'INVALID_MASK_DATA' });
    }

    const buffer = Buffer.from(encoded, 'base64');
    if (!buffer.length || buffer.length > 10 * 1024 * 1024) {
        throw Object.assign(new Error('The selection mask is empty or exceeds the 10 MB limit.'), { code: 'INVALID_MASK_DATA' });
    }
    return { type: 'image', mime_type: mimeType, data: buffer.toString('base64') };
}

function buildImagePrompt(request) {
    const operation = String(request.operation || request.type || 'IMAGE_GEN').toUpperCase();
    const userPrompt = String(request.prompt || '').trim();
    let instruction;

    if (operation.includes('BACKGROUNDREMOVAL') || operation.includes('BACKGROUND_REMOVAL')) {
        instruction = 'Remove the background from the supplied image. Preserve the original foreground subject and fine edges, and return a transparent-background PNG. Do not replace or redesign the subject.';
    } else if (operation.includes('DETECTOBJECTS') || operation.includes('OBJECT_DETECTION')) {
        instruction = 'Return an annotated version of the supplied image with clear, thin bounding boxes and short labels for the visible objects. Keep the original image content intact.';
    } else if (operation.includes('OBJECTREMOVAL') || operation.includes('OBJECT_REMOVAL')) {
        instruction = 'Remove the selected unwanted object from the image and realistically reconstruct the background. Preserve the rest of the image.';
    } else if (operation.includes('UPSCALE')) {
        instruction = 'Improve resolution and detail while preserving the exact subject, framing, text, and colors. Avoid inventing content or changing the composition.';
    } else if (operation.includes('ENHANCE')) {
        instruction = 'Enhance the supplied image: reduce noise, improve exposure and clarity, and preserve natural details and the original composition.';
    } else if (operation.includes('RESTYLE')) {
        instruction = 'Edit and restyle the supplied image according to the user instructions while keeping the main subject recognizable.';
    } else if (operation === 'FILL' || operation.includes('GENERATIVE_FILL')) {
        instruction = 'Edit the supplied image according to the user instructions. Seamlessly fill or replace the selected region if a mask is supplied, matching lighting, perspective, and texture.';
    } else if (operation.includes('OBJECT_REPLACEMENT')) {
        instruction = 'Replace the requested object in the supplied image and naturally blend the replacement with the original scene.';
    } else {
        instruction = 'Create or edit an image following the user instructions. Return the generated image, not just a textual description.';
    }

    return userPrompt ? instruction + '\nUser instructions: ' + userPrompt : instruction;
}

async function processWithGemini(jobId, job) {
    const apiKey = getGoogleApiKey();
    if (!apiKey) {
        throw Object.assign(new Error('Configure GEMINI_API_KEY on the backend.'), { code: 'PROVIDER_NOT_CONFIGURED' });
    }

    JobManager.updateJob(jobId, 'PREPARING', 0.12, 'Preparing Gemini image request…');
    const input = [{ type: 'text', text: buildImagePrompt(job.request) }];
    const source = await readSource(job);

    if (source) {
        if (!source.mimeType.startsWith('image/')) {
            throw Object.assign(new Error('This image operation requires an image file.'), { code: 'INVALID_MEDIA_TYPE' });
        }
        if (source.buffer.length > 20 * 1024 * 1024) {
            throw Object.assign(new Error('Input image exceeds the 20 MB processing limit.'), { code: 'INPUT_TOO_LARGE' });
        }
        input.push({
            type: 'image',
            mime_type: source.mimeType,
            data: source.buffer.toString('base64')
        });
    }

    const operation = String(job.request.operation || job.request.type || '').toUpperCase();
    const maskData = String(job.request.maskData || '').trim();
    if (maskData && (operation.includes('OBJECTREMOVAL') || operation.includes('OBJECT_REMOVAL') ||
        operation === 'FILL' || operation.includes('GENERATIVE_FILL'))) {
        input.push({
            type: 'text',
            text: 'The next image is a selection mask from the user. Use it only as a guide to identify the exact region to remove or edit in the source image. Preserve all unmarked areas; do not reproduce the mask as part of the result.'
        });
        input.push(decodeMaskImage(maskData));
    }

    JobManager.updateJob(jobId, 'PROCESSING', 0.35, 'Processing with Gemini…');
    const response = await fetch(GEMINI_BASE_URL + '/interactions', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'x-goog-api-key': apiKey
        },
        body: JSON.stringify({
            model: IMAGE_MODEL,
            input,
            response_format: { type: 'image' }
        })
    });

    const bodyText = await response.text();
    let data;
    try { data = JSON.parse(bodyText); } catch { data = {}; }

    if (!response.ok) {
        throw Object.assign(
            new Error(data.error && data.error.message ? data.error.message : 'Gemini image request failed (HTTP ' + response.status + ').'),
            { code: 'GEMINI_REQUEST_FAILED' }
        );
    }

    // Interactions returns image data through output_image or an image block in output.
    const outputSteps = Array.isArray(data.output) ? data.output : [];
    const imagePart = data.output_image ||
        outputSteps.slice().reverse().find(part =>
            part && part.type === 'image' && (part.data || part.image || part.content)
        );

    const nestedImage = imagePart && (imagePart.image || imagePart.content);
    const encoded = imagePart && (imagePart.data || (nestedImage && nestedImage.data));
    if (!encoded) {
        const textOutput = data.output_text ||
            outputSteps.slice().reverse().find(part => part && part.type === 'text')?.text;
        throw Object.assign(
            new Error(textOutput || 'Gemini returned no image. Check API access, model availability, and image input.'),
            { code: 'NO_IMAGE_OUTPUT' }
        );
    }

    const mimeType = imagePart.mime_type || imagePart.mimeType ||
        (nestedImage && (nestedImage.mime_type || nestedImage.mimeType)) || 'image/png';
    const extension = mimeType.toLowerCase().includes('jpeg') ? '.jpg'
        : mimeType.toLowerCase().includes('webp') ? '.webp' : '.png';
    const output = await saveOutput(Buffer.from(encoded, 'base64'), extension);

    JobManager.updateJob(jobId, 'COMPLETED', 1, 'Gemini image processing complete.', {
        outputUrl: output.outputUrl,
        filename: output.filename,
        mimeType,
        provider: IMAGE_MODEL
    });
}

async function processTrellisImageTo3D(jobId, job) {
    const apiKey = process.env.FAL_KEY;
    if (!apiKey) {
        throw Object.assign(new Error('Configure FAL_KEY on the backend to enable hosted TRELLIS.'), { code: 'TRELLIS_NOT_CONFIGURED' });
    }
    if (!job.request.filename) {
        throw Object.assign(new Error('TRELLIS requires a source image.'), { code: 'IMAGE_REQUIRED' });
    }

    const source = await readSource(job);
    if (!source || !source.mimeType.startsWith('image/')) {
        throw Object.assign(new Error('TRELLIS accepts an image file.'), { code: 'INVALID_MEDIA_TYPE' });
    }
    if (source.buffer.length > 12 * 1024 * 1024) {
        throw Object.assign(new Error('Image exceeds the TRELLIS 12 MB upload limit.'), { code: 'INPUT_TOO_LARGE' });
    }

    JobManager.updateJob(jobId, 'PREPARING', 0.08, 'Preparing image for TRELLIS…');
    const params = job.request.parameters || {};
    const numeric = (name, fallback) => {
        const raw = params['param_' + name] !== undefined ? params['param_' + name] : params[name];
        const parsed = Number(raw);
        return Number.isFinite(parsed) ? parsed : fallback;
    };

    const input = {
        image_url: 'data:' + source.mimeType + ';base64,' + source.buffer.toString('base64'),
        ss_guidance_strength: numeric('ss_guidance_strength', 7.5),
        ss_sampling_steps: numeric('ss_sampling_steps', 12),
        slat_guidance_strength: numeric('slat_guidance_strength', 3),
        slat_sampling_steps: numeric('slat_sampling_steps', 12),
        mesh_simplify: numeric('mesh_simplify', 0.95),
        texture_size: String(numeric('texture_size', 1024))
    };

    JobManager.updateJob(jobId, 'UPLOADING', 0.16, 'Submitting image to the hosted TRELLIS provider…');
    const submit = await fetch(FAL_QUEUE_BASE_URL + '/' + TRELLIS_MODEL_ID, {
        method: 'POST',
        headers: {
            'Authorization': 'Key ' + apiKey,
            'Content-Type': 'application/json',
            'Accept': 'application/json'
        },
        body: JSON.stringify(input)
    });

    const submitText = await submit.text();
    let submitted;
    try { submitted = JSON.parse(submitText); } catch { submitted = {}; }
    if (!submit.ok) {
        throw Object.assign(
            new Error(submitted.detail || submitted.message || 'TRELLIS submission failed (HTTP ' + submit.status + ').'),
            { code: 'TRELLIS_SUBMIT_FAILED' }
        );
    }
    const requestId = submitted.request_id || submitted.requestId;
    if (!requestId) {
        throw Object.assign(new Error('TRELLIS provider returned no request ID.'), { code: 'TRELLIS_INVALID_RESPONSE' });
    }

    const requestBase = FAL_QUEUE_BASE_URL + '/' + TRELLIS_MODEL_ID + '/requests/' + encodeURIComponent(requestId);
    let completed = false;

    for (let attempt = 0; attempt < MAX_TRELLIS_POLLS; attempt += 1) {
        await new Promise(resolve => setTimeout(resolve, TRELLIS_POLL_INTERVAL_MS));
        const statusResponse = await fetch(requestBase + '/status', {
            headers: { 'Authorization': 'Key ' + apiKey, 'Accept': 'application/json' }
        });
        const statusText = await statusResponse.text();
        let status;
        try { status = JSON.parse(statusText); } catch { status = {}; }

        if (!statusResponse.ok) {
            throw Object.assign(new Error(status.detail || 'Unable to read TRELLIS job status.'), { code: 'TRELLIS_STATUS_FAILED' });
        }
        const state = String(status.status || status.state || '').toUpperCase();
        const progress = Math.min(0.86, 0.2 + (attempt / Math.max(1, MAX_TRELLIS_POLLS)) * 0.65);

        if (state === 'FAILED' || state === 'ERROR' || state === 'CANCELLED') {
            throw Object.assign(new Error(status.error || status.message || 'TRELLIS generation failed.'), { code: 'TRELLIS_GENERATION_FAILED' });
        }
        if (state === 'COMPLETED' || state === 'SUCCEEDED') {
            completed = true;
            break;
        }
        JobManager.updateJob(jobId, 'PROCESSING', progress, 'Generating 3D geometry and textures…');
    }

    if (!completed) {
        throw Object.assign(new Error('TRELLIS generation timed out. Please retry.'), { code: 'TRELLIS_TIMEOUT' });
    }

    JobManager.updateJob(jobId, 'DOWNLOADING', 0.9, 'Downloading generated 3D mesh…');
    const resultResponse = await fetch(requestBase, {
        headers: { 'Authorization': 'Key ' + apiKey, 'Accept': 'application/json' }
    });
    const resultText = await resultResponse.text();
    let result;
    try { result = JSON.parse(resultText); } catch { result = {}; }

    if (!resultResponse.ok) {
        throw Object.assign(new Error(result.detail || 'Unable to retrieve TRELLIS output.'), { code: 'TRELLIS_RESULT_FAILED' });
    }

    const mesh = result.model_mesh || (result.data && result.data.model_mesh) || result.mesh || (result.data && result.data.mesh);
    const meshUrl = mesh && (mesh.url || (mesh.file && mesh.file.url));
    if (!meshUrl) {
        throw Object.assign(new Error('TRELLIS completed but did not return a 3D mesh URL.'), { code: 'TRELLIS_MESH_MISSING' });
    }
    const parsedMeshUrl = new URL(meshUrl);
    if (parsedMeshUrl.protocol !== 'https:') {
        throw Object.assign(new Error('TRELLIS returned an unsafe download URL.'), { code: 'TRELLIS_INVALID_OUTPUT_URL' });
    }

    const meshResponse = await fetch(meshUrl);
    if (!meshResponse.ok) {
        throw Object.assign(new Error('Could not download TRELLIS mesh.'), { code: 'TRELLIS_DOWNLOAD_FAILED' });
    }
    const outputBytes = Buffer.from(await meshResponse.arrayBuffer());
    if (!outputBytes.length || outputBytes.length > 150 * 1024 * 1024) {
        throw Object.assign(new Error('TRELLIS output is empty or exceeds the 150 MB limit.'), { code: 'TRELLIS_OUTPUT_TOO_LARGE' });
    }

    const providerFileName = String(mesh.file_name || mesh.filename || new URL(meshUrl).pathname);
    const providerExt = path.extname(providerFileName).toLowerCase();
    const extension = ['.glb', '.gltf', '.obj', '.ply'].includes(providerExt) ? providerExt : '.glb';
    const output = await saveOutput(outputBytes, extension);

    JobManager.updateJob(jobId, 'COMPLETED', 1, 'TRELLIS 3D model generated.', {
        outputUrl: output.outputUrl,
        filename: output.filename,
        mimeType: extension === '.glb' ? 'model/gltf-binary' : 'application/octet-stream',
        provider: 'TRELLIS'
    });
}

async function processImageJob(jobId) {
    const job = JobManager.getJob(jobId);
    if (!job) return;
    try {
        const requestedType = String(job.type || '').toUpperCase();
        const operation = String(job.request.operation || '').toUpperCase();
        if (requestedType === 'TRELLIS_IMAGE_TO_3D' || operation === 'TRELLIS_IMAGE_TO_3D') {
            await processTrellisImageTo3D(jobId, job);
        } else {
            await processWithGemini(jobId, job);
        }
    } catch (error) {
        updateFailure(jobId, error.code || 'AI_PROCESSING_FAILED', error.message || 'AI image processing failed.');
    } finally {
        if (job.request.filename) {
            await fs.promises.unlink(getFilePath(path.basename(job.request.filename))).catch(() => {});
        }
    }
}

async function processVideoJob(jobId) {
    const job = JobManager.getJob(jobId);
    if (!job) return;

    try {
        const apiKey = getGoogleApiKey();
        if (!apiKey) {
            throw Object.assign(new Error('Configure GEMINI_API_KEY on the backend.'), { code: 'PROVIDER_NOT_CONFIGURED' });
        }

        const requestType = String(job.type || '').toUpperCase();
        const videoInputMode = requestType === 'VIDEO_TO_VIDEO' || requestType === 'VIDEO_EXTENSION';
        const source = await readSource(job);
        if (requestType === 'IMAGE_TO_VIDEO' && !source) {
            throw Object.assign(new Error('Veo image-to-video requires a source image.'), { code: 'IMAGE_REQUIRED' });
        }
        const parameters = job.request.parameters || {};
        let prompt = String(job.request.prompt || '');

        if (!prompt) {
            if (requestType === 'VIDEO_EXTENSION') {
                prompt = 'Continue and naturally extend this Veo-generated video. Preserve its visual style, subjects, lighting, and motion continuity.';
            } else if (requestType === 'VIDEO_TO_VIDEO') {
                prompt = 'Create a video transformation guided by the supplied source footage. Preserve important subjects and temporal continuity.';
            } else {
                prompt = 'Create a short, cinematic video based on the supplied image.';
            }
        }

        const instance = { prompt };
        const generationParameters = {};

        if (source) {
            if (videoInputMode) {
                if (!source.mimeType.startsWith('video/')) {
                    throw Object.assign(new Error('Video-to-video and video extension require a video input.'), { code: 'INVALID_MEDIA_TYPE' });
                }
                if (source.buffer.length > 14 * 1024 * 1024) {
                    throw Object.assign(new Error('Video input exceeds the 14 MB inline-request limit. Use a shorter or smaller video.'), { code: 'INPUT_TOO_LARGE' });
                }
                instance.video = {
                    inlineData: {
                        mimeType: source.mimeType,
                        data: source.buffer.toString('base64')
                    }
                };
                // Veo video extension is currently limited to 720p input/output.
                generationParameters.resolution = '720p';
            } else {
                if (!source.mimeType.startsWith('image/')) {
                    throw Object.assign(new Error('Veo text-to-video or image-to-video requires an image input, not a video.'), { code: 'INVALID_MEDIA_TYPE' });
                }
                if (source.buffer.length > 14 * 1024 * 1024) {
                    throw Object.assign(new Error('Image input exceeds the 14 MB inline-request limit.'), { code: 'INPUT_TOO_LARGE' });
                }
                instance.image = {
                    inlineData: {
                        mimeType: source.mimeType,
                        data: source.buffer.toString('base64')
                    }
                };
                const requestedResolution = String(parameters.param_resolution || parameters.resolution || '');
                if (['720p', '1080p', '4k'].includes(requestedResolution)) {
                    generationParameters.resolution = requestedResolution;
                }
            }
        } else if (videoInputMode) {
            throw Object.assign(new Error('Video-to-video and video extension require a source video.'), { code: 'VIDEO_REQUIRED' });
        } else {
            const requestedResolution = String(parameters.param_resolution || parameters.resolution || '');
            if (['720p', '1080p', '4k'].includes(requestedResolution)) {
                generationParameters.resolution = requestedResolution;
            }
        }

        JobManager.updateJob(jobId, 'PREPARING', 0.1, videoInputMode ? 'Preparing source video for Veo…' : 'Submitting video request to Veo…');
        const requestBody = { instances: [instance] };
        if (Object.keys(generationParameters).length) requestBody.parameters = generationParameters;

        const response = await fetch(
            GEMINI_BASE_URL + '/models/' + encodeURIComponent(VIDEO_MODEL) + ':predictLongRunning',
            {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'x-goog-api-key': apiKey },
                body: JSON.stringify(requestBody)
            }
        );

        const responseText = await response.text();
        let operation;
        try { operation = JSON.parse(responseText); } catch { operation = {}; }
        if (!response.ok || !operation.name) {
            throw Object.assign(
                new Error((operation.error && operation.error.message) || 'Veo rejected the request (HTTP ' + response.status + ').'),
                { code: 'VEO_SUBMIT_FAILED' }
            );
        }

        let finished = false;
        let videoUrl = null;
        for (let attempt = 0; attempt < 120; attempt += 1) {
            await new Promise(resolve => setTimeout(resolve, 5000));
            const poll = await fetch(
                GEMINI_BASE_URL + '/' + String(operation.name).replace(/^\/+/, ''),
                { headers: { 'x-goog-api-key': apiKey, 'Accept': 'application/json' } }
            );
            const pollText = await poll.text();
            try { operation = JSON.parse(pollText); } catch { operation = {}; }

            if (!poll.ok) {
                throw Object.assign(
                    new Error((operation.error && operation.error.message) || 'Could not poll Veo job.'),
                    { code: 'VEO_POLL_FAILED' }
                );
            }
            JobManager.updateJob(jobId, 'PROCESSING', Math.min(0.85, 0.2 + attempt / 150), 'Veo is generating video…');

            if (operation.done) {
                if (operation.error) {
                    throw Object.assign(new Error(operation.error.message || 'Veo generation failed.'), { code: 'VEO_GENERATION_FAILED' });
                }
                const result = operation.response && operation.response.generateVideoResponse;
                const sample = result && result.generatedSamples && result.generatedSamples[0];
                const generated = result && result.generatedVideos && result.generatedVideos[0];
                videoUrl = (sample && sample.video && sample.video.uri) || (generated && generated.video && generated.video.uri) || null;
                finished = true;
                break;
            }
        }

        if (!finished || !videoUrl) {
            throw Object.assign(new Error('Veo generation timed out or returned no video.'), { code: 'VEO_TIMEOUT' });
        }

        JobManager.updateJob(jobId, 'DOWNLOADING', 0.9, 'Downloading generated video…');
        const videoResponse = await fetch(videoUrl, { headers: { 'x-goog-api-key': apiKey } });
        if (!videoResponse.ok) {
            throw Object.assign(new Error('Could not download generated video.'), { code: 'VEO_DOWNLOAD_FAILED' });
        }
        const output = await saveOutput(Buffer.from(await videoResponse.arrayBuffer()), '.mp4');
        JobManager.updateJob(jobId, 'COMPLETED', 1, 'Veo video generation complete.', {
            outputUrl: output.outputUrl,
            filename: output.filename,
            mimeType: 'video/mp4',
            provider: 'Veo'
        });
    } catch (error) {
        updateFailure(jobId, error.code || 'VIDEO_PROCESSING_FAILED', error.message || 'AI video processing failed.');
    } finally {
        if (job.request.filename) {
            await fs.promises.unlink(getFilePath(path.basename(job.request.filename))).catch(() => {});
        }
    }
}

async function processTranscriptionJob(jobId) {
    const job = JobManager.getJob(jobId);
    if (!job) return;

    try {
        const apiKey = getGoogleApiKey();
        if (!apiKey) {
            throw Object.assign(new Error('Configure GEMINI_API_KEY on the backend.'), { code: 'PROVIDER_NOT_CONFIGURED' });
        }
        const source = await readSource(job);
        if (!source || !source.mimeType.startsWith('video/')) {
            throw Object.assign(new Error('Video transcription requires a video file.'), { code: 'INVALID_MEDIA_TYPE' });
        }
        // Inline Gemini video requests are kept small to remain below request-size limits.
        if (source.buffer.length > 14 * 1024 * 1024) {
            throw Object.assign(new Error('Video exceeds the 14 MB inline transcription limit. Use a shorter or smaller clip.'), { code: 'INPUT_TOO_LARGE' });
        }

        const language = String(job.request.language || 'auto').trim() || 'auto';
        const languageInstruction = language.toLowerCase() === 'auto'
            ? 'Automatically identify the spoken language and transcribe in that language.'
            : 'Transcribe the speech in ' + language + '. Preserve the original spoken language; do not translate.';

        JobManager.updateJob(jobId, 'PREPARING', 0.1, 'Preparing video transcription request…');
        const response = await fetch(
            GEMINI_BASE_URL + '/models/' + encodeURIComponent(process.env.TRANSCRIPTION_MODEL || 'gemini-3.8-flash') + ':generateContent',
            {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'x-goog-api-key': apiKey },
                body: JSON.stringify({
                    contents: [{
                        role: 'user',
                        parts: [
                            {
                                text: 'Transcribe all intelligible spoken dialogue in this video. ' +
                                    languageInstruction +
                                    ' Include speaker labels when distinguishable and timestamps when useful. ' +
                                    'Return the transcript only, not a summary.'
                            },
                            {
                                inlineData: {
                                    mimeType: source.mimeType,
                                    data: source.buffer.toString('base64')
                                }
                            }
                        ]
                    }]
                })
            }
        );

        const responseText = await response.text();
        let data;
        try { data = JSON.parse(responseText); } catch { data = {}; }
        if (!response.ok) {
            throw Object.assign(
                new Error((data.error && data.error.message) || 'Gemini transcription failed (HTTP ' + response.status + ').'),
                { code: 'TRANSCRIPTION_REQUEST_FAILED' }
            );
        }

        const parts = (data.candidates || []).flatMap(candidate => (candidate.content && candidate.content.parts) || []);
        const transcript = parts.map(part => part.text || '').filter(Boolean).join('\n').trim();
        if (!transcript) {
            throw Object.assign(new Error('Gemini returned no transcript.'), { code: 'EMPTY_TRANSCRIPT' });
        }

        JobManager.updateJob(jobId, 'COMPLETED', 1, 'Video transcription complete.', {
            transcript,
            text: transcript,
            language,
            provider: 'Gemini'
        });
    } catch (error) {
        updateFailure(jobId, error.code || 'TRANSCRIPTION_FAILED', error.message || 'Video transcription failed.');
    } finally {
        if (job.request.filename) {
            await fs.promises.unlink(getFilePath(path.basename(job.request.filename))).catch(() => {});
        }
    }
}

module.exports = { processImageJob, processVideoJob, processTranscriptionJob };
