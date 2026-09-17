package com.example.ai.assistant

sealed interface AssistantAction {

    data class AdjustBrightness(
        val amount: Float
    ) : AssistantAction

    data class AdjustContrast(
        val amount: Float
    ) : AssistantAction

    data class AdjustSaturation(
        val amount: Float
    ) : AssistantAction

    data class AdjustTemperature(
        val amount: Float
    ) : AssistantAction

    data class ApplyFilter(
        val filterId: String
    ) : AssistantAction

    data object RemoveBackground : AssistantAction

    data object EnhanceImage : AssistantAction

    data object UpscaleImage : AssistantAction

    data object Undo : AssistantAction

    data object Redo : AssistantAction

    data object ExportMedia : AssistantAction

    data class Unknown(
        val rawPrompt: String
    ) : AssistantAction
}
