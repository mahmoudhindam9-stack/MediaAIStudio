package com.example.ai.assistant

interface AssistantToolExecutor {

    suspend fun execute(
        action: AssistantAction
    ): AssistantExecutionResult
}

sealed interface AssistantExecutionResult {

    data object Success : AssistantExecutionResult

    data class Failed(
        val message: String
    ) : AssistantExecutionResult

    data class Unsupported(
        val message: String
    ) : AssistantExecutionResult
}
