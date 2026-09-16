package com.example.videoeditor

import com.example.audio.AudioTrackType
import java.util.UUID
import com.example.ai.video.SubtitleTrack
import com.example.ai.video.TrackingKeyframe
import com.example.ai.video.SuggestedCut

/**
 * A timeline consists of a single sequence of VideoClips (VideoTrack) and multiple AudioTracks.
 */
data class VideoEditorState(
    val videoClips: List<VideoClip> = emptyList(),
    val audioTracks: List<AudioClip> = emptyList(),
    val playheadMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val selectedItemId: String? = null,
    val aiSubtitleTrack: SubtitleTrack? = null,
    val aiTrackingData: List<TrackingKeyframe>? = null,
    val aiSuggestedCuts: List<SuggestedCut>? = null
)

/**
 * Common properties for items on the timeline
 */
interface TimelineItem {
    val id: String
    val uri: String
    var startTimeMs: Long // Start position on the timeline
    var durationMs: Long // Trimmed duration
    var startTrimMs: Long // Trim start in original media
    var volume: Float
    var isMuted: Boolean
    var isDuckingEnabled: Boolean
    
    val endTimeMs: Long get() = startTimeMs + durationMs
}

data class VideoClip(
    override val id: String = UUID.randomUUID().toString(),
    override val uri: String,
    override var startTimeMs: Long = 0L,
    override var durationMs: Long = 0L,
    override var startTrimMs: Long = 0L,
    override var volume: Float = 1.0f,
    override var isMuted: Boolean = false,
    override var isDuckingEnabled: Boolean = false, // Not usually used for video, but part of interface
    
    val originalDurationMs: Long = 0L,
    val rotation: Float = 0f
) : TimelineItem

data class AudioClip(
    override val id: String = UUID.randomUUID().toString(),
    val type: AudioTrackType,
    override val uri: String,
    override var startTimeMs: Long = 0L,
    override var durationMs: Long = 0L,
    override var startTrimMs: Long = 0L,
    override var volume: Float = 1.0f,
    override var isMuted: Boolean = false,
    override var isDuckingEnabled: Boolean = false,
    
    val originalDurationMs: Long = 0L,
    var fadeInDurationMs: Long = 0L,
    var fadeOutDurationMs: Long = 0L,
) : TimelineItem
