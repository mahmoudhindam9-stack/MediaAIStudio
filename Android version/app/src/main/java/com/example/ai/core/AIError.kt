package com.example.ai.core
sealed class AIError(val message: String) {
    object ProviderUnavailable : AIError("AI provider unavailable")
    object ModelUnavailable : AIError("AI model unavailable")
    object InvalidInput : AIError("Invalid input parameters")
    object ProcessingFailure : AIError("AI processing failed")
    object Cancelled : AIError("AI operation cancelled")
    object InsufficientStorage : AIError("Insufficient storage for AI result")
    object NetworkFailure : AIError("Network error occurred")
    object Timeout : AIError("AI processing timed out")
    class Unknown(message: String) : AIError(message)
}
