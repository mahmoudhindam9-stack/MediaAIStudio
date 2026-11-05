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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GenerativeEngine(private val context: Context) {
    private val _jobs = MutableStateFlow<Map<String, GenerativeJob>>(emptyMap())
    val jobs: StateFlow<Map<String, GenerativeJob>> = _jobs.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val cloudClient = CloudGenerativeClient(context.applicationContext)
    private val remoteJobIds = ConcurrentHashMap<String, String>()

    val isAvailable: Boolean
        get() = cloudClient.isConfigured

    fun submitJob(request: GenerativeRequest): String {
        val jobId = UUID.randomUUID().toString()
        _jobs.value = _jobs.value + (
            jobId to GenerativeJob(
                id = jobId,
                request = request,
                state = JobState.QUEUED,
                message = if (isAvailable) "Waiting for cloud AI…" else "Cloud AI is not configured"
            )
        )

        if (!isAvailable) {
            updateJobState(
                jobId,
                JobState.FAILED,
                0f,
                "Configure MEDIA_AI_BACKEND_URL to enable Cloud AI",
                GenerativeResult.Error("PROVIDER_NOT_CONFIGURED")
            )
        } else {
            processJobAsync(jobId)
        }
        return jobId
    }

    private fun processJobAsync(jobId: String) {
        scope.launch {
            val job = _jobs.value[jobId] ?: return@launch
            updateJobState(jobId, JobState.PREPARING, 0.05f, "Preparing media for upload…")

            updateJobState(jobId, JobState.UPLOADING, 0.10f, "Uploading source media…")
            val remoteJobId = when (job.request.type) {
                GenerativeType.IMAGE_TO_VIDEO,
                GenerativeType.VIDEO_TO_VIDEO,
                GenerativeType.VIDEO_EXTENSION -> cloudClient.submitVideoJob(job.request)
                else -> cloudClient.submitImageJob(job.request)
            }

            if (remoteJobId == null) {
                updateJobState(
                    jobId,
                    JobState.FAILED,
                    0f,
                    "Cloud backend rejected the request or is unreachable",
                    GenerativeResult.Error("NETWORK_OR_BACKEND_ERROR")
                )
                return@launch
            }

            remoteJobIds[jobId] = remoteJobId
            pollJobStatus(jobId, remoteJobId)
        }
    }

    private suspend fun pollJobStatus(localJobId: String, remoteJobId: String) {
        repeat(MAX_POLLS) {
            val status = cloudClient.getJobStatus(remoteJobId)
                ?: run {
                    updateJobState(
                        localJobId,
                        JobState.FAILED,
                        0f,
                        "Lost connection to cloud backend",
                        GenerativeResult.Error("NETWORK_ERROR")
                    )
                    remoteJobIds.remove(localJobId)
                    return
                }

            val state = when (status.state.uppercase()) {
                "QUEUED" -> JobState.QUEUED
                "PREPARING" -> JobState.PREPARING
                "UPLOADING" -> JobState.UPLOADING
                "PROCESSING" -> JobState.PROCESSING
                "DOWNLOADING" -> JobState.DOWNLOADING
                "COMPLETED", "COMPLETE", "SUCCEEDED", "SUCCESS" -> JobState.COMPLETED
                "FAILED", "ERROR" -> JobState.FAILED
                "CANCELLED", "CANCELED" -> JobState.CANCELLED
                else -> JobState.PROCESSING
            }

            if (state == JobState.COMPLETED && status.outputUrl != null) {
                updateJobState(localJobId, JobState.DOWNLOADING, 0.95f, "Downloading generated media…")
                val localOutput = cloudClient.materializeOutput(status.outputUrl, "generative_result")
                if (localOutput != null) {
                    updateJobState(
                        localJobId,
                        JobState.COMPLETED,
                        1f,
                        "Generation complete",
                        GenerativeResult.Success(localOutput, mapOf("remoteJobId" to remoteJobId))
                    )
                } else {
                    updateJobState(
                        localJobId,
                        JobState.FAILED,
                        status.progress,
                        "Generation finished but the output could not be downloaded",
                        GenerativeResult.Error("OUTPUT_DOWNLOAD_FAILED")
                    )
                }
                remoteJobIds.remove(localJobId)
                return
            }

            val result = when (state) {
                JobState.FAILED -> GenerativeResult.Error(status.errorReason ?: "Cloud generation failed")
                JobState.CANCELLED -> GenerativeResult.Error("CANCELLED")
                else -> null
            }

            updateJobState(
                localJobId,
                state,
                status.progress,
                status.message.ifBlank { state.name },
                result
            )

            if (state == JobState.FAILED || state == JobState.CANCELLED) {
                remoteJobIds.remove(localJobId)
                return
            }

            delay(POLL_INTERVAL_MS)
        }

        updateJobState(
            localJobId,
            JobState.FAILED,
            0f,
            "Cloud job timed out",
            GenerativeResult.Error("TIMEOUT")
        )
        remoteJobIds.remove(localJobId)
    }

    fun cancelJob(jobId: String) {
        val job = _jobs.value[jobId] ?: return
        if (job.state == JobState.COMPLETED || job.state == JobState.FAILED || job.state == JobState.CANCELLED) return

        val remoteId = remoteJobIds[jobId]
        updateJobState(
            jobId,
            JobState.CANCELLED,
            job.progress,
            "Cancelled by user",
            GenerativeResult.Error("CANCELLED")
        )
        if (remoteId != null) {
            scope.launch { cloudClient.cancelJob(remoteId) }
            remoteJobIds.remove(jobId)
        }
    }

    fun close() {
        remoteJobIds.clear()
        scope.coroutineContext[Job]?.cancel()
    }

    private fun updateJobState(
        jobId: String,
        state: JobState,
        progress: Float,
        message: String,
        result: GenerativeResult? = null
    ) {
        val currentJobs = _jobs.value.toMutableMap()
        val job = currentJobs[jobId] ?: return
        currentJobs[jobId] = job.copy(
            state = state,
            progress = progress.coerceIn(0f, 1f),
            message = message,
            result = result
        )
        _jobs.value = currentJobs
    }

    companion object {
        private const val MAX_POLLS = 300
        private const val POLL_INTERVAL_MS = 1_000L
    }
}