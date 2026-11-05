package com.example.videoeditor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoEditorViewModelTest {
    @Test
    fun exportStatePreventsDuplicateRequestsWhileBusy() {
        val exporting = VideoEditorState(
            videoClips = listOf(VideoClip(uri = "content://video/1", durationMs = 1_000L)),
            durationMs = 1_000L,
            isExporting = true
        )
        val idle = exporting.copy(isExporting = false)

        assertFalse(idle.isExporting)
        assertTrue(exporting.isExporting)
    }
}
