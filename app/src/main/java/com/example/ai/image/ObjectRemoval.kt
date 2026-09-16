package com.example.ai.image
import com.example.ai.core.AIRequest

object ObjectRemoval {
    fun createRequest(uri: String, maskData: String): AIRequest = AIRequest.ObjectRemoval(uri, maskData)
}
