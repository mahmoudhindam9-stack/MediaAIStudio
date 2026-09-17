package com.example.videoeditor

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.ai.video.SuggestedCut
import com.example.audio.AudioTrackType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class TimelineTest {

    private lateinit var viewModel: VideoEditorViewModel
    private lateinit var application: Application

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        viewModel = VideoEditorViewModel(application)
    }

    @After
    fun tearDown() {
        viewModel.exoPlayer.release()
    }

    private fun clip(
        id: String,
        durationMs: Long,
        startTimeMs: Long = 0L,
        startTrimMs: Long = 0L,
        originalDurationMs: Long = durationMs
    ) = VideoClip(
        id = id,
        uri = "content://$id",
        startTimeMs = startTimeMs,
        durationMs = durationMs,
        startTrimMs = startTrimMs,
        originalDurationMs = originalDurationMs
    )

    @Test
    fun addMultipleClipsProducesAValidTimelineAfterNormalization() {
        val first = clip("1", 4_000)
        val second = clip("2", 5_000, startTimeMs = 4_000)
        viewModel.updateState(VideoEditorState(videoClips = listOf(first, second), durationMs = 9_000))

        viewModel.selectItem("2")
        viewModel.moveSelectedClipLeft()

        val state = viewModel.state.value
        assertEquals(listOf("2", "1"), state.videoClips.map { it.id })
        assertEquals(0L, state.videoClips[0].startTimeMs)
        assertEquals(5_000L, state.videoClips[1].startTimeMs)
        assertEquals(9_000L, state.durationMs)
    }

    @Test
    fun trimRecalculatesDurationAndFollowingClipPosition() {
        val first = clip("1", 10_000)
        val second = clip("2", 5_000, startTimeMs = 10_000)
        viewModel.updateState(VideoEditorState(videoClips = listOf(first, second), durationMs = 15_000))
        viewModel.selectItem("1")

        viewModel.trimSelectedClip(1_000, 4_000)

        val state = viewModel.state.value
        assertEquals(3_000L, state.videoClips[0].durationMs)
        assertEquals(1_000L, state.videoClips[0].startTrimMs)
        assertEquals(3_000L, state.videoClips[1].startTimeMs)
        assertEquals(8_000L, state.durationMs)
    }

    @Test
    fun splitCreatesTwoValidClipsWithCorrectTrimRanges() {
        val source = clip("1", 10_000)
        viewModel.updateState(VideoEditorState(videoClips = listOf(source), durationMs = 10_000))
        viewModel.selectItem("1")
        viewModel.seekTo(4_000)

        viewModel.splitSelectedClip()

        val state = viewModel.state.value
        assertEquals(2, state.videoClips.size)
        assertEquals(4_000L, state.videoClips[0].durationMs)
        assertEquals(0L, state.videoClips[0].startTrimMs)
        assertEquals(6_000L, state.videoClips[1].durationMs)
        assertEquals(4_000L, state.videoClips[1].startTrimMs)
        assertEquals(4_000L, state.videoClips[1].startTimeMs)
    }

    @Test
    fun deleteSelectedClipSelectsNeighborAndClampsDuration() {
        val first = clip("1", 4_000)
        val second = clip("2", 5_000, startTimeMs = 4_000)
        viewModel.updateState(VideoEditorState(videoClips = listOf(first, second), durationMs = 9_000, playheadMs = 8_500))
        viewModel.selectItem("1")

        viewModel.deleteSelectedClip()

        val state = viewModel.state.value
        assertEquals(1, state.videoClips.size)
        assertEquals("2", state.videoClips[0].id)
        assertEquals(0L, state.videoClips[0].startTimeMs)
        assertEquals(5_000L, state.durationMs)
        assertEquals("2", state.selectedItemId)
        assertEquals(5_000L, state.playheadMs)
    }

    @Test
    fun seekClampsToTimelineAndUpdatesPlayhead() {
        val first = clip("1", 5_000)
        viewModel.updateState(VideoEditorState(videoClips = listOf(first), durationMs = 5_000))

        viewModel.seekTo(99_000)
        assertEquals(5_000L, viewModel.state.value.playheadMs)

        viewModel.seekTo(-10)
        assertEquals(0L, viewModel.state.value.playheadMs)
    }

    @Test
    fun smartCutSplitsTargetIntoRemainingSegments() {
        val source = clip("1", 20_000)
        viewModel.updateState(
            VideoEditorState(
                videoClips = listOf(source),
                durationMs = 20_000,
                selectedItemId = "1",
                aiSuggestedCuts = listOf(
                    SuggestedCut(0, 2_000, "remove start"),
                    SuggestedCut(18_000, 20_000, "remove end")
                )
            )
        )

        viewModel.applySmartCuts()

        val state = viewModel.state.value
        assertEquals(1, state.videoClips.size)
        assertEquals(2_000L, state.videoClips[0].startTrimMs)
        assertEquals(16_000L, state.videoClips[0].durationMs)
        assertEquals(16_000L, state.durationMs)
        assertTrue(state.aiSuggestedCuts == null)
    }

    @Test
    fun invalidTrimIsRejectedWithoutChangingClip() {
        val source = clip("1", 10_000)
        viewModel.updateState(VideoEditorState(videoClips = listOf(source), durationMs = 10_000, selectedItemId = "1"))

        viewModel.trimSelectedClip(8_000, 3_000)

        val state = viewModel.state.value
        assertEquals(0L, state.videoClips[0].startTrimMs)
        assertEquals(10_000L, state.videoClips[0].durationMs)
        assertEquals(10_000L, state.durationMs)
    }

    @Test
    fun moveRightReordersClipsAndRecalculatesStarts() {
        val first = clip("1", 3_000)
        val second = clip("2", 7_000, startTimeMs = 3_000)
        val third = clip("3", 2_000, startTimeMs = 10_000)
        viewModel.updateState(VideoEditorState(videoClips = listOf(first, second, third), durationMs = 12_000, selectedItemId = "2"))

        viewModel.moveSelectedClipRight()

        val state = viewModel.state.value
        assertEquals(listOf("1", "3", "2"), state.videoClips.map { it.id })
        assertEquals(0L, state.videoClips[0].startTimeMs)
        assertEquals(3_000L, state.videoClips[1].startTimeMs)
        assertEquals(5_000L, state.videoClips[2].startTimeMs)
        assertEquals(12_000L, state.durationMs)
    }

    @Test
    fun selectedVolumeAndMuteApplyToTheSelectedItem() {
        val source = clip("1", 5_000)
        viewModel.updateState(VideoEditorState(videoClips = listOf(source), durationMs = 5_000, selectedItemId = "1"))

        viewModel.setSelectedVolume(0.35f)
        viewModel.toggleSelectedMute()

        val state = viewModel.state.value
        assertEquals(0.35f, state.videoClips[0].volume, 0.001f)
        assertTrue(state.videoClips[0].isMuted)
    }

    @Test
    fun undoRedoRestoresTimelineStates() {
        val source = clip("1", 10_000)
        viewModel.updateState(VideoEditorState(videoClips = listOf(source), durationMs = 10_000, selectedItemId = "1"))
        viewModel.commitState()

        viewModel.trimSelectedClip(1_000, 6_000)
        assertEquals(5_000L, viewModel.state.value.durationMs)

        viewModel.undo()
        assertEquals(10_000L, viewModel.state.value.durationMs)

        viewModel.redo()
        assertEquals(5_000L, viewModel.state.value.durationMs)
    }

    @Test
    fun timelineValidationDetectsBrokenOrderingAndSelection() {
        val brokenFirst = clip("1", 5_000, startTimeMs = 2_000)
        val brokenSecond = clip("1", 4_000, startTimeMs = 7_000, startTrimMs = 4_000, originalDurationMs = 5_000)
        viewModel.updateState(
            VideoEditorState(
                videoClips = listOf(brokenFirst, brokenSecond),
                durationMs = 9_000,
                playheadMs = 10_000,
                selectedItemId = "missing"
            )
        )

        val errors = viewModel.validateTimelineState()
        assertTrue(errors.any { it.contains("Duplicate") })
        assertTrue(errors.any { it.contains("Invalid timeline ordering") })
        assertTrue(errors.any { it.contains("Invalid trim range") })
        assertTrue(errors.any { it.contains("Playhead") })
        assertTrue(errors.any { it.contains("selectedItemId") })
    }

    @Test
    fun audioTrimDoesNotChangeVideoDuration() {
        val video = clip("video", 8_000)
        val audio = AudioClip(
            id = "audio",
            type = AudioTrackType.MUSIC,
            uri = "content://audio",
            startTimeMs = 1_000,
            durationMs = 5_000,
            originalDurationMs = 5_000
        )
        viewModel.updateState(
            VideoEditorState(
                videoClips = listOf(video),
                audioTracks = listOf(audio),
                durationMs = 8_000,
                selectedItemId = "audio"
            )
        )

        viewModel.trimSelectedClip(500, 2_500)

        val state = viewModel.state.value
        assertEquals(8_000L, state.durationMs)
        assertEquals(2_000L, state.audioTracks[0].durationMs)
        assertEquals(500L, state.audioTracks[0].startTrimMs)
    }
}
