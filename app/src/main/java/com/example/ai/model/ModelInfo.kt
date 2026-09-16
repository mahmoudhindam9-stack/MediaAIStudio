package com.example.ai.model

data class ModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val sizeBytes: Long? = null,
    val status: ModelState,
    val version: String? = null,
    val license: String? = null,
    val sha256: String? = null,
    val source: String? = null,
    val runtime: String? = null
)

enum class ModelState {
    INSTALLED, NOT_INSTALLED, DOWNLOADING, UNAVAILABLE
}
