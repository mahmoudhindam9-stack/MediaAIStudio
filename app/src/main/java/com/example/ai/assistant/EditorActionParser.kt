package com.example.ai.assistant

class EditorActionParser {
    fun parse(prompt: String): EditorAction {
        val lower = prompt.lowercase()
        return when {
            lower.contains("brightness") -> EditorAction.AdjustBrightness(0.2f)
            lower.contains("contrast") -> EditorAction.AdjustContrast(0.2f)
            lower.contains("saturation") -> EditorAction.AdjustSaturation(0.2f)
            lower.contains("background") || lower.contains("bg") -> EditorAction.AIBackgroundRemoval
            lower.contains("enhance") || lower.contains("sharpen") -> EditorAction.AIEnhancement
            else -> EditorAction.Unknown(prompt)
        }
    }
}
