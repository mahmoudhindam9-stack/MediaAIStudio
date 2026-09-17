package com.example.photoeditor

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.ai.assistant.AssistantAction
import com.example.ai.assistant.AssistantExecutionResult
import com.example.ai.assistant.AssistantToolExecutor
import com.example.ai.core.AIRequest
import com.example.ai.core.AIResult
import com.example.settings.SettingsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

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
                            brightness = (it.brightness + action.amount)
                                .coerceIn(-255f, 255f)
                        )
                    }
                    AssistantExecutionResult.Success
                }

                is AssistantAction.AdjustContrast -> {
                    viewModel.updateState {
                        it.copy(
                            contrast = (it.contrast + action.amount)
                                .coerceIn(0f, 2f)
                        )
                    }
                    AssistantExecutionResult.Success
                }

                is AssistantAction.AdjustSaturation -> {
                    viewModel.updateState {
                        it.copy(
                            saturation = (it.saturation + action.amount)
                                .coerceIn(0f, 2f)
                        )
                    }
                    AssistantExecutionResult.Success
                }

                is AssistantAction.AdjustTemperature -> {
                    viewModel.updateState {
                        it.copy(
                            temperature = (it.temperature + action.amount)
                                .coerceIn(-1f, 1f)
                        )
                    }
                    AssistantExecutionResult.Success
                }

                AssistantAction.RemoveBackground -> executeAi(
                    AIRequest.BackgroundRemoval(viewModel.state.value.uriString)
                )

                AssistantAction.EnhanceImage -> executeAi(
                    AIRequest.Enhance(viewModel.state.value.uriString, "auto")
                )

                AssistantAction.UpscaleImage -> executeAi(
                    AIRequest.Upscale(viewModel.state.value.uriString, 2)
                )

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
                        "Filter execution is not yet wired to the filter state model."
                    )
                }

                AssistantAction.ExportMedia -> {
                    AssistantExecutionResult.Unsupported(
                        "Export is deferred to the unified export phase."
                    )
                }

                is AssistantAction.Unknown -> {
                    AssistantExecutionResult.Unsupported("Unknown assistant action.")
                }
            }
        } catch (t: Throwable) {
            AssistantExecutionResult.Failed(
                t.message ?: "Assistant execution failed"
            )
        }
    }

    private suspend fun executeAi(request: AIRequest): AssistantExecutionResult {
        val sourceUri = request.sourceUri
        if (sourceUri.isBlank()) {
            return AssistantExecutionResult.Failed("No source media is open.")
        }

        val requiredModel = when (request) {
            is AIRequest.Upscale -> "realesrgan_x2plus"
            is AIRequest.Enhance -> "cpga_fp16"
            is AIRequest.ObjectRemoval -> "llama/inpainting_lama_2025jan"
            else -> null
        }

        if (requiredModel != null && !viewModel.modelManager.isInstalled(requiredModel)) {
            return AssistantExecutionResult.Failed(
                "Required AI model is not installed: $requiredModel"
            )
        }

        val preferences = SettingsPreferences(viewModel.getApplication())
        val providerType = preferences.aiMode.first()

        viewModel.aiError.value = null
        viewModel.aiProgress.value = com.example.ai.core.AIProgress(0f, "Preparing...")

        return try {
            when (val result = viewModel.aiEngine.processImage(request, providerType) { progress ->
                viewModel.aiProgress.value = progress
            }) {
                is AIResult.Success -> {
                    applyAiResult(result.outputUri)
                    AssistantExecutionResult.Success
                }
                is AIResult.Error -> {
                    val message = result.error.message
                    viewModel.aiError.value = message
                    AssistantExecutionResult.Failed(message)
                }
            }
        } catch (t: Throwable) {
            val message = t.message ?: "AI processing failed"
            viewModel.aiError.value = message
            AssistantExecutionResult.Failed(message)
        } finally {
            viewModel.aiProgress.value = null
        }
    }

    private suspend fun applyAiResult(outputUri: String) {
        val context = viewModel.getApplication<android.app.Application>()
        val uri = Uri.parse(outputUri)
        val bitmap = withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(context.contentResolver, uri)
                ) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }.copy(Bitmap.Config.ARGB_8888, true)
        }

        viewModel.state.value = viewModel.state.value.copy(uriString = outputUri)
        viewModel.originalBitmap.value = bitmap
        viewModel.previewAiResultUri.value = null
        viewModel.previewBitmap.value = null
    }
}
