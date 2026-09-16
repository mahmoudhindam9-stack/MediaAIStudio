package com.example.audio

/**
 * Represents a single audio track on the timeline.
 */
data class AudioTrack(
    val id: String,
    val type: AudioTrackType,
    val uri: String,
    var volume: Float = 1.0f,
    var isMuted: Boolean = false,
    var isSolo: Boolean = false,
    
    // Timeline positioning
    var startTimeMs: Long = 0L,
    var endTimeMs: Long = 0L, // Trim end
    var startOffsetMs: Long = 0L, // Where it starts on the global timeline
    
    // Fades
    var fadeInDurationMs: Long = 0L,
    var fadeOutDurationMs: Long = 0L,
    
    // Ducking (e.g. lower volume when Voice Over plays)
    var isDuckingEnabled: Boolean = false
)
