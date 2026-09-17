package com.example.ai.assistant

class AIAssistant(
    private val planner: AssistantPlanner = FallbackAssistantPlanner()
) {

    suspend fun plan(
        prompt: String,
        context: AssistantContext
    ): AssistantResult {
        return planner.createPlan(prompt, context)
    }
}
