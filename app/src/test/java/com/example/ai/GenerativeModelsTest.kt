package com.example.ai

import com.example.ai.generative.GenerativeType
import com.example.ai.generative.isVideoOutput
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerativeModelsTest {

    @Test
    fun videoGenerativeTypes_areClassifiedAsVideoOutputs() {
        assertTrue(GenerativeType.IMAGE_TO_VIDEO.isVideoOutput)
        assertTrue(GenerativeType.VIDEO_TO_VIDEO.isVideoOutput)
        assertTrue(GenerativeType.VIDEO_EXTENSION.isVideoOutput)
    }

    @Test
    fun imageGenerativeTypes_areNotClassifiedAsVideoOutputs() {
        assertFalse(GenerativeType.FILL.isVideoOutput)
        assertFalse(GenerativeType.OBJECT_REMOVAL.isVideoOutput)
        assertFalse(GenerativeType.OBJECT_REPLACEMENT.isVideoOutput)
        assertFalse(GenerativeType.IMAGE_TO_IMAGE.isVideoOutput)
    }
}
