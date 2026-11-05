package com.example.videoeditor

/**
 * Represents a video segment on the timeline.
 */
data class VideoTrack(
    val id: String,
    val uri: String,
    val startTimeMs: Long = 0L,
    val endTimeMs: Long = 0L,
    val startOffsetMs: Long = 0L
)
