package com.example.videoeditor

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE)
class TimelineTest {

    private lateinit var viewModel: VideoEditorViewModel
    private lateinit var application: Application

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        viewModel = VideoEditorViewModel(application)
    }

    @Test
    fun testTimelineOperations() {
        val clip1 = VideoClip(id = "1", uri = "uri1", originalDurationMs = 10000, durationMs = 10000)
        val clip2 = VideoClip(id = "2", uri = "uri2", originalDurationMs = 5000, durationMs = 5000, startTimeMs = 10000)
        
        viewModel.updateState(VideoEditorState(videoClips = listOf(clip1, clip2)))
        
        // Let's test delete
        viewModel.selectItem("1")
        viewModel.deleteSelectedClip()
        
        var state = viewModel.state.value
        assertEquals(1, state.videoClips.size)
        assertEquals("2", state.videoClips[0].id)
        assertEquals(0L, state.videoClips[0].startTimeMs) // Recalculated!
        
        // Let's test split
        viewModel.updateState(VideoEditorState(videoClips = listOf(clip1)))
        viewModel.selectItem("1")
        viewModel.seekTo(5000)
        viewModel.splitSelectedClip()
        
        state = viewModel.state.value
        assertEquals(2, state.videoClips.size)
        assertEquals(5000L, state.videoClips[0].durationMs)
        assertEquals(5000L, state.videoClips[1].durationMs)
        assertEquals(5000L, state.videoClips[1].startTrimMs)
        assertEquals(5000L, state.videoClips[1].startTimeMs) // Recalculated!
        
        // Let's test trim
        viewModel.selectItem(state.videoClips[0].id)
        viewModel.trimSelectedClip(1000L, 4000L)
        
        state = viewModel.state.value
        assertEquals(3000L, state.videoClips[0].durationMs)
        assertEquals(1000L, state.videoClips[0].startTrimMs)
        assertEquals(3000L, state.videoClips[1].startTimeMs) // Recalculated!
    }
}
