package com.example.ai.image
import com.example.ai.core.AIRequest

object ImageRestyle {
    fun createRequest(uri: String, prompt: String): AIRequest = AIRequest.Restyle(uri, prompt)
}
