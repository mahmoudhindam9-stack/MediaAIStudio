package com.example.ai.assistant

class FallbackAssistantPlanner : AssistantPlanner {

    override suspend fun createPlan(
        prompt: String,
        context: AssistantContext
    ): AssistantResult {

        val normalized = prompt.trim().lowercase()

        if (normalized.isBlank()) {
            return AssistantResult.Unsupported(
                prompt = prompt,
                reason = "Empty instruction"
            )
        }

        val actions = mutableListOf<AssistantAction>()

        if (
            normalized.contains("background") ||
            normalized.contains("remove bg") ||
            normalized.contains("remove the bg")
        ) {
            actions += AssistantAction.RemoveBackground
        }

        if (
            normalized.contains("bright") ||
            normalized.contains("brighter")
        ) {
            actions += AssistantAction.AdjustBrightness(0.10f)
        }

        if (
            normalized.contains("contrast")
        ) {
            actions += AssistantAction.AdjustContrast(0.10f)
        }

        if (
            normalized.contains("saturation") ||
            normalized.contains("more colorful")
        ) {
            actions += AssistantAction.AdjustSaturation(0.10f)
        }

        if (
            normalized.contains("warmer") ||
            normalized.contains("warm")
        ) {
            actions += AssistantAction.AdjustTemperature(0.10f)
        }

        if (
            normalized.contains("enhance") ||
            normalized.contains("improve quality") ||
            normalized.contains("sharpen")
        ) {
            actions += AssistantAction.EnhanceImage
        }

        if (
            normalized.contains("upscale") ||
            normalized.contains("higher resolution")
        ) {
            actions += AssistantAction.UpscaleImage
        }

        if (actions.isEmpty()) {
            return AssistantResult.Unsupported(
                prompt = prompt,
                reason = "The instruction could not be mapped to a supported editing action."
            )
        }

        val explanation = actions.joinToString(
            separator = "\n"
        ) { action ->
            when (action) {
                is AssistantAction.AdjustBrightness ->
                    "Adjust brightness by ${action.amount}"

                is AssistantAction.AdjustContrast ->
                    "Adjust contrast by ${action.amount}"

                is AssistantAction.AdjustSaturation ->
                    "Adjust saturation by ${action.amount}"

                is AssistantAction.AdjustTemperature ->
                    "Adjust temperature by ${action.amount}"

                is AssistantAction.ApplyFilter ->
                    "Apply filter ${action.filterId}"

                AssistantAction.RemoveBackground ->
                    "Remove background"

                AssistantAction.EnhanceImage ->
                    "Enhance image"

                AssistantAction.UpscaleImage ->
                    "Upscale image"

                AssistantAction.Undo ->
                    "Undo"

                AssistantAction.Redo ->
                    "Redo"

                AssistantAction.ExportMedia ->
                    "Export media"

                is AssistantAction.Unknown ->
                    "Unsupported instruction"
            }
        }

        return AssistantResult.Planned(
            AssistantActionPlan(
                originalPrompt = prompt,
                actions = actions,
                explanation = explanation,
                requiresConfirmation = actions.any {
                    it is AssistantAction.RemoveBackground ||
                    it is AssistantAction.UpscaleImage ||
                    it is AssistantAction.ExportMedia
                }
            )
        )
    }
}
