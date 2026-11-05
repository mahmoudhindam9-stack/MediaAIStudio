package com.example.videoeditor

import com.example.audio.AudioTrack

/**
 * Represents the complete timeline of a video editing project, explicitly supporting
 * multiple audio tracks coexisting with the video.
 */
data class Timeline(
    val videoTracks: List<VideoTrack> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList()
)
