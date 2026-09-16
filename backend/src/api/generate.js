const express = require('express');
const multer = require('multer');
const JobManager = require('../jobs/JobManager');
const GenerativeService = require('../ai/GenerativeService');
const { STORAGE_DIR } = require('../storage/StorageManager');

const router = express.Router();
const upload = multer({ dest: STORAGE_DIR });

// Middleware for auth
function requireAuth(req, res, next) {
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
        return res.status(401).json({ error: 'AUTHENTICATION_ERROR', message: 'Missing Authorization header' });
    }
    // Validate token here in production
    next();
}

router.post('/image/generate', requireAuth, upload.single('media'), (req, res) => {
    const { prompt, type } = req.body;
    const media = req.file;

    const job = JobManager.createJob(type || 'IMAGE_GEN', { prompt, filename: media ? media.filename : null });
    
    // Kick off async processing
    GenerativeService.processImageJob(job.id);
    
    res.json({ jobId: job.id, status: job.state });
});

router.post('/video/generate', requireAuth, upload.single('media'), (req, res) => {
    const { prompt, type } = req.body;
    const media = req.file;

    const job = JobManager.createJob(type || 'VIDEO_GEN', { prompt, filename: media ? media.filename : null });
    
    // Kick off async processing
    GenerativeService.processVideoJob(job.id);
    
    res.json({ jobId: job.id, status: job.state });
});

router.get('/jobs/:jobId', requireAuth, (req, res) => {
    const job = JobManager.getJob(req.params.jobId);
    if (!job) {
        return res.status(404).json({ error: 'NOT_FOUND', message: 'Job not found' });
    }
    res.json(job);
});

module.exports = router;
