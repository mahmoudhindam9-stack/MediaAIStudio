package com.example.videoeditor

import com.example.ai.video.VideoEnhancementSuggestion
import com.example.audio.AudioTrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VideoProjectSnapshotCodecTest {

    @Test
    fun roundTrip_preservesTimelineAndAiState() {
        val state = VideoEditorState(
            videoClips = listOf(
                VideoClip(
                    id = "video-1",
                    uri = "content://video/1",
                    startTimeMs = 0L,
                    durationMs = 8_000L,
                    startTrimMs = 1_000L,
                    volume = 0.8f,
                    isMuted = true,
                    originalDurationMs = 12_000L,
                    rotation = 90f
                )
            ),
            audioTracks = listOf(
                AudioClip(
                    id = "audio-1",
                    type = AudioTrackType.MUSIC,
                    uri = "content://audio/1",
                    startTimeMs = 1_500L,
                    durationMs = 4_000L,
                    startTrimMs = 500L,
                    volume = 0.55f,
                    fadeInDurationMs = 250L,
                    fadeOutDurationMs = 400L,
                    originalDurationMs = 7_000L
                )
            ),
            playheadMs = 3_250L,
            durationMs = 8_000L,
            selectedItemId = "video-1",
            aiReframeKeyframes = listOf(
                ReframeKeyframe(0L, 0.5f, 0.5f, 0.5f, 1f, 0.9f),
                ReframeKeyframe(4_000L, 0.6f, 0.45f, 0.5f, 1f, 0.85f)
            ),
            aiEnhancementSuggestion = VideoEnhancementSuggestion(
                brightness = 0.1f,
                contrast = 0.2f,
                saturation = -0.05f,
                sharpness = 0.15f,
                confidence = 0.9f
            )
        )

        val restored = VideoProjectSnapshotCodec.decode(
            VideoProjectSnapshotCodec.encode(state)
        )

        assertNotNull(restored)
        assertEquals(state.videoClips, restored!!.videoClips)
        assertEquals(state.audioTracks, restored.audioTracks)
        assertEquals(state.playheadMs, restored.playheadMs)
        assertEquals(state.durationMs, restored.durationMs)
        assertEquals(state.selectedItemId, restored.selectedItemId)
        assertEquals(state.aiReframeKeyframes, restored.aiReframeKeyframes)
        assertEquals(state.aiEnhancementSuggestion, restored.aiEnhancementSuggestion)
        assertFalse(restored.isPlaying)
        assertFalse(restored.isExporting)
    }

    @Test
    fun invalidJson_returnsNull() {
        assertNull(VideoProjectSnapshotCodec.decode("{not-json"))
    }
}
