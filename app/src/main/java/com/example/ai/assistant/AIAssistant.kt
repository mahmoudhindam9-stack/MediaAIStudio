package com.example.ai.assistant

class AIAssistant(private val parser: EditorActionParser = EditorActionParser()) {
    fun processInstruction(prompt: String): EditorAction {
        // Validate instruction and check capabilities
        val action = parser.parse(prompt)
        if (action is EditorAction.Unknown) {
            // Log rejection
        }
        return action
    }
}
