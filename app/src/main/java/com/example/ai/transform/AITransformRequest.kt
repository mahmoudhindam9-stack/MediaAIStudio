package com.example.ai.transform
import com.example.ai.core.AIProviderType

data class AITransformRequest(
    val sourceUri: String,
    val prompt: String,
    val maskData: String? = null,
    val providerPreference: AIProviderType = AIProviderType.AUTO
)
