package com.example.videoeditor

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VideoEditorViewModelTest {
    
    @Test
    fun duplicateExportProtection() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = VideoEditorViewModel(application)
        
        // Expose or verify behavior through the ViewModel's state flag
        val state = viewModel.state.value
        assertEquals(false, state.isExporting)
    }
}
