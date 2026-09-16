const { v4: uuidv4 } = require('uuid');

const jobs = new Map();

function createJob(type, requestData) {
    const jobId = uuidv4();
    const job = {
        id: jobId,
        type,
        state: 'QUEUED',
        progress: 0,
        message: 'Waiting in queue...',
        request: requestData,
        result: null,
        createdAt: Date.now()
    };
    jobs.set(jobId, job);
    return job;
}

function updateJob(jobId, state, progress, message, result = null) {
    const job = jobs.get(jobId);
    if (job) {
        job.state = state;
        job.progress = progress;
        job.message = message;
        if (result) job.result = result;
    }
}

function getJob(jobId) {
    return jobs.get(jobId);
}

module.exports = {
    createJob,
    updateJob,
    getJob
};
