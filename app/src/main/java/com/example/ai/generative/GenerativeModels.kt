package com.example.ai.generative

import android.net.Uri

data class GenerativeRequest(
    val type: GenerativeType,
    val sourceUri: Uri?,
    val maskUri: Uri? = null,
    val prompt: String,
    val parameters: Map<String, Any> = emptyMap()
)

enum class GenerativeType {
    FILL,
    OBJECT_REMOVAL,
    OBJECT_REPLACEMENT,
    IMAGE_TO_IMAGE,
    IMAGE_TO_VIDEO,
    VIDEO_TO_VIDEO,
    VIDEO_EXTENSION
}

sealed class GenerativeResult {
    data class Success(
        val outputUri: Uri,
        val metadata: Map<String, String> = emptyMap()
    ) : GenerativeResult()
    
    data class Error(val reason: String) : GenerativeResult()
}

data class GenerativeProgress(
    val progress: Float,
    val message: String
)

enum class JobState {
    QUEUED,
    PREPARING,
    UPLOADING,
    PROCESSING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class GenerativeJob(
    val id: String,
    val request: GenerativeRequest,
    val state: JobState = JobState.QUEUED,
    val progress: Float = 0f,
    val message: String = "",
    val result: GenerativeResult? = null
)
