package com.example.ai.assistant

sealed interface EditorAction {
    data class AdjustBrightness(val value: Float) : EditorAction
    data class AdjustContrast(val value: Float) : EditorAction
    data class AdjustSaturation(val value: Float) : EditorAction
    data class ApplyFilter(val filterId: String) : EditorAction
    object AIBackgroundRemoval : EditorAction
    object AIEnhancement : EditorAction
    data class Unknown(val rawPrompt: String) : EditorAction
}
