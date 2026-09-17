package com.example.ai.assistant

sealed interface AssistantResult {

    data class Planned(
        val plan: AssistantActionPlan
    ) : AssistantResult

    data class Executed(
        val plan: AssistantActionPlan
    ) : AssistantResult

    data class NeedsConfirmation(
        val plan: AssistantActionPlan
    ) : AssistantResult

    data class Unsupported(
        val prompt: String,
        val reason: String
    ) : AssistantResult

    data class Failed(
        val message: String,
        val cause: Throwable? = null
    ) : AssistantResult
}
