package com.example.ai.assistant

data class AssistantActionPlan(
    val originalPrompt: String,
    val actions: List<AssistantAction>,
    val explanation: String,
    val requiresConfirmation: Boolean = false
)
