package com.example.ai.image
import com.example.ai.core.AIRequest

object BackgroundRemoval {
    fun createRequest(uri: String): AIRequest = AIRequest.BackgroundRemoval(uri)
}
