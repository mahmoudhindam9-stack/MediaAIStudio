package com.example.ai.generative

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class GenerativeEngine(private val context: Context) {
    
    private val _jobs = MutableStateFlow<Map<String, GenerativeJob>>(emptyMap())
    val jobs: StateFlow<Map<String, GenerativeJob>> = _jobs.asStateFlow()
    
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val cloudClient = CloudGenerativeClient()

    fun submitJob(request: GenerativeRequest): String {
        val jobId = UUID.randomUUID().toString()
        val job = GenerativeJob(
            id = jobId,
            request = request,
            state = JobState.QUEUED,
            message = "Waiting in queue..."
        )
        _jobs.value = _jobs.value + (jobId to job)
        processJobAsync(jobId)
        return jobId
    }

    private fun processJobAsync(jobId: String) {
        scope.launch {
            val job = _jobs.value[jobId] ?: return@launch
            updateJobState(jobId, JobState.PREPARING, 0.1f, "Submitting to cloud provider...")
            
            val isVideo = job.request.type == GenerativeType.IMAGE_TO_VIDEO || 
                          job.request.type == GenerativeType.VIDEO_TO_VIDEO || 
                          job.request.type == GenerativeType.VIDEO_EXTENSION
            
            val remoteJobId = if (isVideo) {
                cloudClient.submitVideoJob(job.request.prompt, job.request.type.name)
            } else {
                cloudClient.submitImageJob(job.request.prompt, job.request.type.name)
            }
            
            if (remoteJobId == null) {
                updateJobState(jobId, JobState.FAILED, 0f, "Failed to connect to backend", GenerativeResult.Error("NETWORK_ERROR"))
                return@launch
            }
            
            pollJobStatus(jobId, remoteJobId)
        }
    }
    
    private suspend fun pollJobStatus(localJobId: String, remoteJobId: String) {
        var isPolling = true
        while (isPolling) {
            val status = cloudClient.getJobStatus(remoteJobId)
            if (status == null) {
                updateJobState(localJobId, JobState.FAILED, 0f, "Lost connection to backend", GenerativeResult.Error("NETWORK_ERROR"))
                break
            }
            
            val mappedState = when(status.state) {
                "QUEUED" -> JobState.QUEUED
                "PREPARING" -> JobState.PREPARING
                "PROCESSING" -> JobState.PROCESSING
                "COMPLETED" -> JobState.COMPLETED
                "FAILED" -> JobState.FAILED
                else -> JobState.PROCESSING
            }
            
            val result = if (mappedState == JobState.COMPLETED && status.outputUrl != null) {
                GenerativeResult.Success(Uri.parse(status.outputUrl))
            } else if (mappedState == JobState.FAILED) {
                GenerativeResult.Error(status.errorReason ?: "Unknown Error")
            } else null
            
            updateJobState(localJobId, mappedState, status.progress, status.message, result)
            
            if (mappedState == JobState.COMPLETED || mappedState == JobState.FAILED || mappedState == JobState.CANCELLED) {
                isPolling = false
            } else {
                delay(1000)
            }
        }
    }
    
    fun cancelJob(jobId: String) {
        val job = _jobs.value[jobId] ?: return
        if (job.state != JobState.COMPLETED && job.state != JobState.FAILED) {
            updateJobState(jobId, JobState.CANCELLED, 0f, "Cancelled by user")
        }
    }

    private fun updateJobState(jobId: String, state: JobState, progress: Float, message: String, result: GenerativeResult? = null) {
        val currentJobs = _jobs.value.toMutableMap()
        val job = currentJobs[jobId] ?: return
        currentJobs[jobId] = job.copy(
            state = state,
            progress = progress,
            message = message,
            result = result
        )
        _jobs.value = currentJobs
    }
}
