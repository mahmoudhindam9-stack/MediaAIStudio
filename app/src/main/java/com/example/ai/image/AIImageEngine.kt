package com.example.ai.image

import com.example.ai.core.*
import com.example.ai.provider.AIProviderManager

class AIImageEngine(private val providerManager: AIProviderManager) {
    suspend fun processImage(
        request: AIRequest,
        providerType: AIProviderType = AIProviderType.AUTO,
        onProgress: (AIProgress) -> Unit
    ): AIResult {
        // Restyling is a hosted generative operation; the on-device provider does not
        // implement it. AUTO must route it to Cloud instead of silently selecting ML Kit.
        val effectiveProvider = when {
            providerType != AIProviderType.AUTO -> providerType
            request is AIRequest.Restyle -> AIProviderType.CLOUD
            else -> AIProviderType.AUTO
        }
        val provider = providerManager.getProvider(effectiveProvider)
        return provider.process(request, onProgress)
    }
}
