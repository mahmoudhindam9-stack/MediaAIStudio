package com.example.ai.provider
import com.example.ai.core.AIRequest
import com.example.ai.core.AIResult
import com.example.ai.core.AIProgress
import kotlinx.coroutines.flow.Flow

interface AIProvider {
    val type: com.example.ai.core.AIProviderType
    val isAvailable: Boolean
    suspend fun process(request: AIRequest, onProgress: (AIProgress) -> Unit = {}): AIResult
}
