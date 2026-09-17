package com.example.videoeditor.export

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioTrackType
import com.example.videoeditor.AudioClip
import com.example.videoeditor.VideoClip
import com.example.videoeditor.VideoEditorState
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
class VideoExportTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val exporter by lazy { VideoExport(context) }

    @Test
    fun emptyTimeline_throwsException() {
        val state = VideoEditorState()
        val exception = assertThrows(IllegalArgumentException::class.java) {
            exporter.export(state, {}, {}, {})
        }
        assertTrue(exception.message!!.contains("Cannot export an empty timeline"))
    }

    @Test
    fun contiguousVideoClips_passValidation() {
        // Will throw a Transformer error instead of IllegalArgumentException if validation passes
        val state = VideoEditorState(
            videoClips = listOf(
                VideoClip(uri = "test1", durationMs = 1000L),
                VideoClip(uri = "test2", startTimeMs = 1000L, durationMs = 2000L)
            ),
            durationMs = 3000L
        )
        val thrown = runCatching { exporter.export(state, {}, {}, {}) }.exceptionOrNull()
        assertTrue(thrown !is IllegalArgumentException)
    }

    @Test
    fun invalidTimelineDetection_gap_throwsException() {
        val state = VideoEditorState(
            videoClips = listOf(
                VideoClip(uri = "test1", durationMs = 1000L),
                VideoClip(uri = "test2", startTimeMs = 2000L, durationMs = 2000L) // Gap of 1000ms
            ),
            durationMs = 4000L
        )
        val exception = assertThrows(IllegalArgumentException::class.java) {
            exporter.export(state, {}, {}, {})
        }
        assertTrue(exception.message!!.contains("not contiguous"))
    }

    @Test
    fun invalidTimelineDetection_negativeTrim_throwsException() {
        val state = VideoEditorState(
            videoClips = listOf(
                VideoClip(uri = "test1", durationMs = 1000L, startTrimMs = -100L)
            ),
            durationMs = 1000L
        )
        val exception = assertThrows(IllegalArgumentException::class.java) {
            exporter.export(state, {}, {}, {})
        }
        assertTrue(exception.message!!.contains("negative trim"))
    }

    @Test
    fun invalidTimelineDetection_durationMismatch_throwsException() {
        val state = VideoEditorState(
            videoClips = listOf(
                VideoClip(uri = "test1", durationMs = 1000L)
            ),
            durationMs = 5000L // Mismatch
        )
        val exception = assertThrows(IllegalArgumentException::class.java) {
            exporter.export(state, {}, {}, {})
        }
        assertTrue(exception.message!!.contains("duration mismatch"))
    }

    @Test
    fun terminalStateCleanup_isExecutedOnError() {
        val state = VideoEditorState(
            videoClips = listOf(VideoClip(uri = "test1", durationMs = 1000L)),
            durationMs = 1000L
        )
        val didError = AtomicBoolean(false)
        exporter.export(
            state,
            onProgress = {},
            onSuccess = {},
            onError = {
                didError.set(true)
            }
        )
        assertTrue(didError.get())
        // In robolectric, the transformer setup fails and hits our cleanup block
        // meaning duplicate-prevention locks will release.
    }
}
