package com.example.photoeditor

import com.example.ai.assistant.AssistantAction
import com.example.ai.assistant.AssistantExecutionResult
import com.example.ai.assistant.AssistantToolExecutor
import com.example.ai.core.AIRequest

class PhotoEditorAssistantExecutor(
    private val viewModel: PhotoEditorViewModel
) : AssistantToolExecutor {

    override suspend fun execute(
        action: AssistantAction
    ): AssistantExecutionResult {

        return try {
            when (action) {

                is AssistantAction.AdjustBrightness -> {
                    viewModel.updateState {
                        it.copy(
                            brightness =
                                (it.brightness + action.amount)
                                    .coerceIn(-255f, 255f)
                        )
                    }
                    AssistantExecutionResult.Success
                }

                is AssistantAction.AdjustContrast -> {
                    viewModel.updateState {
                        it.copy(
                            contrast =
                                (it.contrast + action.amount)
                                    .coerceIn(0f, 2f)
                        )
                    }
                    AssistantExecutionResult.Success
                }

                is AssistantAction.AdjustSaturation -> {
                    viewModel.updateState {
                        it.copy(
                            saturation =
                                (it.saturation + action.amount)
                                    .coerceIn(0f, 2f)
                        )
                    }
                    AssistantExecutionResult.Success
                }

                is AssistantAction.AdjustTemperature -> {
                    viewModel.updateState {
                        it.copy(
                            temperature =
                                (it.temperature + action.amount)
                                    .coerceIn(-1f, 1f)
                        )
                    }
                    AssistantExecutionResult.Success
                }

                AssistantAction.RemoveBackground -> {
                    viewModel.processAITool(
                        AIRequest.BackgroundRemoval(
                            viewModel.state.value.uriString
                        )
                    )
                    AssistantExecutionResult.Success
                }

                AssistantAction.EnhanceImage -> {
                    viewModel.processAITool(
                        AIRequest.Enhance(
                            viewModel.state.value.uriString,
                            "auto"
                        )
                    )
                    AssistantExecutionResult.Success
                }

                AssistantAction.UpscaleImage -> {
                    viewModel.processAITool(
                        AIRequest.Upscale(
                            viewModel.state.value.uriString,
                            2
                        )
                    )
                    AssistantExecutionResult.Success
                }

                AssistantAction.Undo -> {
                    viewModel.undo()
                    AssistantExecutionResult.Success
                }

                AssistantAction.Redo -> {
                    viewModel.redo()
                    AssistantExecutionResult.Success
                }

                is AssistantAction.ApplyFilter -> {
                    AssistantExecutionResult.Unsupported(
                        "Filter execution must use the Photo Editor filter model."
                    )
                }

                AssistantAction.ExportMedia -> {
                    AssistantExecutionResult.Unsupported(
                        "Export will be connected in the unified export phase."
                    )
                }

                is AssistantAction.Unknown -> {
                    AssistantExecutionResult.Unsupported(
                        "Unknown assistant action."
                    )
                }
            }
        } catch (t: Throwable) {
            AssistantExecutionResult.Failed(
                t.message ?: "Assistant execution failed"
            )
        }
    }
}
