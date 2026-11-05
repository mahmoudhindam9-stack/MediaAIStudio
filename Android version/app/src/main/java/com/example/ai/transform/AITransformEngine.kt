package com.example.ai.transform
import com.example.ai.core.*
import com.example.ai.provider.AIProviderManager

class AITransformEngine(private val providerManager: AIProviderManager) {
    suspend fun executeTransform(request: AITransformRequest, onProgress: (TransformProgress) -> Unit): AIResult {
        val provider = providerManager.getProvider(request.providerPreference)
        return provider.process(AIRequest.Restyle(request.sourceUri, request.prompt)) { p ->
            onProgress(TransformProgress(p.percentage, p.statusMessage))
        }
    }
}
