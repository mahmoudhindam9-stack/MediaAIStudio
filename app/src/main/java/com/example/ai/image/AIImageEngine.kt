package com.example.ai.image
import com.example.ai.core.*
import com.example.ai.provider.AIProviderManager

class AIImageEngine(private val providerManager: AIProviderManager) {
    suspend fun processImage(request: AIRequest, providerType: AIProviderType = AIProviderType.AUTO, onProgress: (AIProgress) -> Unit): AIResult {
        val provider = providerManager.getProvider(providerType)
        return provider.process(request, onProgress)
    }
}
