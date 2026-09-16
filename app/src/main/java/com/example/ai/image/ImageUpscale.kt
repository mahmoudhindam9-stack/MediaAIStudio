package com.example.ai.image
import com.example.ai.core.AIRequest

object ImageUpscale {
    fun createRequest(uri: String, scaleFactor: Int): AIRequest = AIRequest.Upscale(uri, scaleFactor)
}
