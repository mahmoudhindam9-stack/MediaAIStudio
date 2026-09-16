package com.example.ai.model

enum class ModelInstallState {
    NOT_INSTALLED,
    DOWNLOADING,
    VERIFYING,
    READY,
    FAILED
}

// Kept for backward compatibility
typealias ModelState = ModelInstallState

data class ModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val sizeBytes: Long? = null,
    val status: ModelInstallState,
    val progress: Int = 0,
    val errorMessage: String? = null,
    val currentBytes: Long = 0L,
    val expectedBytes: Long? = null,
    val checksumValid: Boolean? = null,
    val version: String? = null,
    val license: String? = null,
    val sha256: String? = null,
    val source: String? = null,
    val runtime: String? = null
)
