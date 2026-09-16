const JobManager = require('../jobs/JobManager');

async function processImageJob(jobId) {
    const job = JobManager.getJob(jobId);
    if (!job) return;
    JobManager.updateJob(jobId, 'PREPARING', 0.1, 'Validating inputs...');
    
    const apiKey = process.env.GOOGLE_AI_API_KEY;
    if (!apiKey) {
        JobManager.updateJob(jobId, 'FAILED', 0, 'Provider not configured', { error: 'PROVIDER_NOT_CONFIGURED' });
        return;
    }
    
    JobManager.updateJob(jobId, 'PROCESSING', 0.5, 'Calling external provider...');
    
    // In a real implementation we would call the Google AI API here.
    // Since we don't have the API code implemented, we explicitly fail instead of faking.
    JobManager.updateJob(jobId, 'FAILED', 0, 'Provider code not implemented on server', { error: 'NOT_IMPLEMENTED' });
}

async function processVideoJob(jobId) {
    const job = JobManager.getJob(jobId);
    if (!job) return;
    JobManager.updateJob(jobId, 'PREPARING', 0.1, 'Validating inputs...');
    
    const apiKey = process.env.GOOGLE_AI_API_KEY;
    if (!apiKey) {
        JobManager.updateJob(jobId, 'FAILED', 0, 'Provider not configured', { error: 'PROVIDER_NOT_CONFIGURED' });
        return;
    }
    
    JobManager.updateJob(jobId, 'PROCESSING', 0.3, 'Calling external provider...');
    
    JobManager.updateJob(jobId, 'FAILED', 0, 'Provider code not implemented on server', { error: 'NOT_IMPLEMENTED' });
}

module.exports = {
    processImageJob,
    processVideoJob
};
