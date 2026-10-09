const express = require('express');
const multer = require('multer');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const JobManager = require('../jobs/JobManager');
const GenerativeService = require('../ai/GenerativeService');
const { STORAGE_DIR, getFilePath } = require('../storage/StorageManager');

const router = express.Router();
const upload = multer({
    dest: STORAGE_DIR,
    limits: { fileSize: 12 * 1024 * 1024, files: 2 }
});

function requireAuth(req, res, next) {
    const expected = process.env.MEDIA_AI_BACKEND_API_KEY || '';
    if (!expected) {
        return res.status(503).json({
            error: 'AUTH_NOT_CONFIGURED',
            message: 'Set MEDIA_AI_BACKEND_API_KEY on the backend before enabling AI requests.'
        });
    }

    const authorization = req.headers.authorization || '';
    const supplied = authorization.startsWith('Bearer ') ? authorization.slice(7).trim() : '';
    const suppliedBuffer = Buffer.from(supplied);
    const expectedBuffer = Buffer.from(expected);
    const valid = suppliedBuffer.length === expectedBuffer.length &&
        suppliedBuffer.length > 0 &&
        crypto.timingSafeEqual(suppliedBuffer, expectedBuffer);

    if (!valid) {
        return res.status(401).json({ error: 'AUTHENTICATION_ERROR', message: 'Invalid or missing backend token.' });
    }
    next();
}

function requestData(req) {
    const parameters = {};
    for (const [key, value] of Object.entries(req.body || {})) {
        if (key.startsWith('param_')) parameters[key] = value;
    }
    return {
        prompt: String(req.body.prompt || ''),
        type: String(req.body.type || 'IMAGE_GEN'),
        operation: String(req.body.operation || ''),
        filename: req.file ? req.file.filename : null,
        mimeType: req.file ? req.file.mimetype : null,
        originalName: req.file ? path.basename(req.file.originalname || 'media') : null,
        parameters,
        maskData: req.body.maskData || ''
    };
}

router.post('/image/process', requireAuth, upload.single('media'), (req, res) => {
    const job = JobManager.createJob(String(req.body.operation || 'IMAGE_PROCESS'), requestData(req));
    void GenerativeService.processImageJob(job.id);
    res.status(202).json({ jobId: job.id, status: job.state });
});

router.post('/image/generate', requireAuth, upload.single('media'), (req, res) => {
    const job = JobManager.createJob(String(req.body.type || 'IMAGE_GEN'), requestData(req));
    void GenerativeService.processImageJob(job.id);
    res.status(202).json({ jobId: job.id, status: job.state });
});

router.post('/video/generate', requireAuth, upload.single('media'), (req, res) => {
    const job = JobManager.createJob(String(req.body.type || 'IMAGE_TO_VIDEO'), requestData(req));
    void GenerativeService.processVideoJob(job.id);
    res.status(202).json({ jobId: job.id, status: job.state });
});

router.get('/jobs/:jobId', requireAuth, (req, res) => {
    const job = JobManager.getJob(req.params.jobId);
    if (!job) return res.status(404).json({ error: 'NOT_FOUND', message: 'Job not found' });
    res.json(job);
});

router.delete('/jobs/:jobId', requireAuth, (req, res) => {
    const job = JobManager.getJob(req.params.jobId);
    if (!job) return res.status(404).json({ error: 'NOT_FOUND', message: 'Job not found' });
    if (!['COMPLETED', 'FAILED', 'CANCELLED'].includes(String(job.state).toUpperCase())) {
        JobManager.updateJob(job.id, 'CANCELLED', job.progress, 'Cancelled by user', { error: 'CANCELLED' });
    }
    res.json({ ok: true });
});

router.get('/files/:filename', requireAuth, (req, res) => {
    const filename = path.basename(req.params.filename);
    if (filename !== req.params.filename) return res.status(400).json({ error: 'INVALID_FILENAME' });

    const filePath = getFilePath(filename);
    if (!fs.existsSync(filePath)) return res.status(404).json({ error: 'FILE_NOT_FOUND' });

    const extension = path.extname(filename).toLowerCase();
    const contentType = extension === '.glb' ? 'model/gltf-binary'
        : extension === '.gltf' ? 'model/gltf+json'
        : extension === '.mp4' ? 'video/mp4'
        : extension === '.jpg' || extension === '.jpeg' ? 'image/jpeg'
        : extension === '.webp' ? 'image/webp'
        : extension === '.obj' || extension === '.ply' ? 'application/octet-stream'
        : 'image/png';

    res.setHeader('Content-Type', contentType);
    res.setHeader('Cache-Control', 'private, max-age=300');
    res.sendFile(filePath);
});

module.exports = router;
