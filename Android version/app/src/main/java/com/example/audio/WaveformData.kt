package com.example.audio

/**
 * Visual representation of the audio amplitudes for UI.
 */
data class WaveformData(
    val trackId: String,
    val amplitudes: List<Int>, // Normalized amplitudes
    val durationMs: Long
)
