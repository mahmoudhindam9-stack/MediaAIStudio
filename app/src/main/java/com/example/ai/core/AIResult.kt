package com.example.ai.core
sealed class AIResult {
    data class Success(
        val outputUri: String, 
        val processingType: String,
        val providerUsed: AIProviderType,
        val durationMs: Long? = null,
        val metadata: Map<String, String> = emptyMap()
    ) : AIResult()
    
    data class Error(val error: AIError) : AIResult()
}
