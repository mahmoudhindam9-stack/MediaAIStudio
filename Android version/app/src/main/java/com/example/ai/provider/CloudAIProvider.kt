package com.example.ai.provider

import android.content.Context
import com.example.ai.core.*
import com.example.ai.generative.CloudGenerativeClient

class CloudAIProvider(context: Context) : AIProvider {
    private val client = CloudGenerativeClient(context.applicationContext)

    override val type = AIProviderType.CLOUD
    override val isAvailable: Boolean
        get() = client.isConfigured

    override suspend fun process(
        request: AIRequest,
        onProgress: (AIProgress) -> Unit
    ): AIResult {
        if (!isAvailable) return AIResult.Error(AIError.ProviderUnavailable)
        return client.processImageRequest(request, onProgress)
    }
}