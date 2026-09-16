package com.example.ai.model

/** Metadata for an on-device model that can be fetched on first use. */
data class ModelArtifact(
    val id: String,
    val filename: String,
    val downloadUrl: String,
    val expectedSha256: String?,
    val minimumBytes: Long,
    val license: String,
    val source: String,
    val runtime: String
)
