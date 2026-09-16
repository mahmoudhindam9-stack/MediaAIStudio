package com.example.ai.provider

import android.content.Context
import com.example.ai.core.AIProviderType

class AIProviderManager(context: Context) {
    private val providers = listOf(OnDeviceAIProvider(context), CloudAIProvider())

    fun getProvider(type: AIProviderType): AIProvider {
        if (type == AIProviderType.AUTO) {
            return providers.firstOrNull { it.isAvailable } ?: providers.first()
        }
        return providers.firstOrNull { it.type == type } ?: providers.first()
    }
}
