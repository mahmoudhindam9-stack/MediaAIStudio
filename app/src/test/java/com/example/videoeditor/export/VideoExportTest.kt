package com.example.videoeditor.export

import com.example.audio.AudioTrackType
import com.example.videoeditor.AudioClip
import com.example.videoeditor.VideoClip
import com.example.videoeditor.VideoEditorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoExportTest {

    @Test
    fun emptyTimeline_isRejected() {
        val state = VideoEditorState()
        val error = runCatching {
            validateVideoRenderPlan(state.toVideoRenderPlan(), state.durationMs)
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("Cannot export an empty video timeline"))
    }

    @Test
    fun singleVideoExportPlan_preservesTrimAndRotation() {
        val clip = VideoClip(
            uri = "content://video/1",
            durationMs = 2_000L,
            startTrimMs = 1_000L,
            rotation = 90f
        )
        val state = VideoEditorState(videoClips = listOf(clip), durationMs = 2_000L)
        val plan = state.toVideoRenderPlan().single()

        assertEquals(1_000L, plan.trimStartMs)
        assertEquals(3_000L, plan.trimEndMs)
        assertEquals(0L, plan.startTimeMs)
        assertEquals(90f, plan.rotation, 0.0001f)
        validateVideoRenderPlan(state.toVideoRenderPlan(), state.durationMs)
    }

    @Test
    fun multipleVideoClips_areSequentialWithoutGaps() {
        val clips = listOf(
            VideoClip(uri = "content://video/1", durationMs = 1_000L),
            VideoClip(uri = "content://video/2", startTimeMs = 1_000L, durationMs = 2_000L)
        )
        val state = VideoEditorState(videoClips = clips, durationMs = 3_000L)
        validateVideoRenderPlan(state.toVideoRenderPlan(), state.durationMs)
        assertEquals(0L, state.videoClips[0].startTimeMs)
        assertEquals(1_000L, state.videoClips[1].startTimeMs)
    }

    @Test
    fun imagePlusVideo_preservesConfiguredDurations() {
        val image = VideoClip(uri = "content://image/1", durationMs = 4_000L, originalDurationMs = 4_000L, isImage = true)
        val video = VideoClip(uri = "content://video/1", startTimeMs = 4_000L, durationMs = 3_000L, originalDurationMs = 3_000L)
        val state = VideoEditorState(videoClips = listOf(image, video), durationMs = 7_000L)
        val plan = state.toVideoRenderPlan()

        assertEquals(2, plan.size)
        assertTrue(plan[0].isImage)
        assertFalse(plan[1].isImage)
        assertEquals(4_000L, plan[0].trimEndMs - plan[0].trimStartMs)
        assertEquals(3_000L, plan[1].trimEndMs - plan[1].trimStartMs)
        validateVideoRenderPlan(plan, state.durationMs)
    }

    @Test
    fun invalidGap_isRejected() {
        val state = VideoEditorState(
            videoClips = listOf(
                VideoClip(uri = "content://video/1", durationMs = 1_000L),
                VideoClip(uri = "content://video/2", startTimeMs = 2_000L, durationMs = 2_000L)
            ),
            durationMs = 4_000L
        )
        val error = runCatching {
            validateVideoRenderPlan(state.toVideoRenderPlan(), state.durationMs)
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("not contiguous"))
    }

    @Test
    fun invalidDurationMismatch_isRejected() {
        val state = VideoEditorState(
            videoClips = listOf(VideoClip(uri = "content://video/1", durationMs = 1_000L)),
            durationMs = 5_000L
        )
        val error = runCatching {
            validateVideoRenderPlan(state.toVideoRenderPlan(), state.durationMs)
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("duration mismatch"))
    }

    @Test
    fun audioTrimAndTimelineOffset_areResolvedIndependently() {
        val track = AudioClip(
            type = AudioTrackType.MUSIC,
            uri = "content://audio/1",
            startTimeMs = 2_500L,
            startTrimMs = 3_000L,
            durationMs = 4_000L,
            originalDurationMs = 10_000L
        )
        val plan = track.toAudioRenderPlan(12_000L)
        requireNotNull(plan)

        assertEquals(2_500L, plan.startAtMs)
        assertEquals(3_000L, plan.sourceStartMs)
        assertEquals(7_000L, plan.sourceEndMs)
        assertEquals(4_000L, plan.durationMs)
    }

    @Test
    fun audioDuration_isClampedToProjectEnd() {
        val track = AudioClip(
            type = AudioTrackType.MUSIC,
            uri = "content://audio/1",
            startTimeMs = 8_000L,
            durationMs = 5_000L,
            originalDurationMs = 10_000L
        )
        val plan = track.toAudioRenderPlan(10_000L)
        requireNotNull(plan)
        assertEquals(2_000L, plan.durationMs)
        assertEquals(2_000L, plan.sourceEndMs)
    }

    @Test
    fun audioDuration_isClampedToRemainingSource() {
        val track = AudioClip(
            type = AudioTrackType.MUSIC,
            uri = "content://audio/1",
            startTimeMs = 1_000L,
            startTrimMs = 8_000L,
            durationMs = 5_000L,
            originalDurationMs = 10_000L
        )
        val plan = track.toAudioRenderPlan(20_000L)
        requireNotNull(plan)
        assertEquals(2_000L, plan.durationMs)
        assertEquals(10_000L, plan.sourceEndMs)
    }

    @Test
    fun mutedAudio_isNotExported() {
        val track = AudioClip(
            type = AudioTrackType.MUSIC,
            uri = "content://audio/1",
            durationMs = 2_000L,
            originalDurationMs = 2_000L,
            isMuted = true
        )
        assertNull(track.toAudioRenderPlan(10_000L))
    }

    @Test
    fun exactSilenceOffset_usesSamplePreciseWholeMilliseconds() {
        assertEquals(48L, silenceFrameCount(1L))
        assertEquals(816L, silenceFrameCount(17L))
        assertEquals(48_000L, silenceFrameCount(1_000L))
    }
}
