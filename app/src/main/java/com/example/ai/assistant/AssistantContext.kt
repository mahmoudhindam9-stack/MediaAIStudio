package com.example.ai.assistant

data class AssistantContext(
    val mediaType: MediaType = MediaType.PHOTO,
    val hasSourceMedia: Boolean = false,
    val currentBrightness: Float = 0f,
    val currentContrast: Float = 1f,
    val currentSaturation: Float = 1f,
    val currentTemperature: Float = 0f,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isBusy: Boolean = false
)

enum class MediaType {
    PHOTO,
    VIDEO
}
