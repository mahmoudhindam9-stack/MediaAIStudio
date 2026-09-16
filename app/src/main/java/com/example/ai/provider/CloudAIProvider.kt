package com.example.ai.provider

import com.example.ai.core.*

class CloudAIProvider : AIProvider {
    override val type = AIProviderType.CLOUD
    // To honestly reflect backend existence in architecture, we mark it true, 
    // but the backend will return auth error since keys are not configured.
    override val isAvailable = true 

    override suspend fun process(request: AIRequest, onProgress: (AIProgress) -> Unit): AIResult {
        // In the full architecture, legacy synchronous AI tasks could also be routed here.
        // For Advanced Generative Jobs, we use GenerativeEngine instead.
        return AIResult.Error(AIError.ProviderUnavailable)
    }
}
