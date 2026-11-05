package com.example.ai.image
import com.example.ai.core.AIRequest

object ImageEnhancement {
    fun createRequest(uri: String, enhanceType: String): AIRequest = AIRequest.Enhance(uri, enhanceType)
}
