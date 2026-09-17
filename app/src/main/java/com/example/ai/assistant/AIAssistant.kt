package com.example.ai.assistant

class AIAssistant(
    private val planner: AssistantPlanner = FallbackAssistantPlanner()
) {

    suspend fun plan(
        prompt: String,
        context: AssistantContext
    ): AssistantResult {
        return when (val result = planner.createPlan(prompt, context)) {
            is AssistantResult.Planned -> {
                if (result.plan.requiresConfirmation) {
                    AssistantResult.NeedsConfirmation(result.plan)
                } else {
                    result
                }
            }
            else -> result
        }
    }
}
