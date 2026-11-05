package com.example.ai.assistant

interface AssistantPlanner {

    suspend fun createPlan(
        prompt: String,
        context: AssistantContext
    ): AssistantResult
}
