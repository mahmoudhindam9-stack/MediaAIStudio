package com.example.ai.model

import android.content.Context
import java.io.File

interface ModelManager {
    fun getAvailableModels(): List<ModelInfo>
    fun downloadModel(id: String)
    fun deleteModel(id: String)
    fun getCapabilityStatus(capability: AICapability): AICapabilityStatus
}

class AppModelManager(private val context: Context) : ModelManager {
    private val artifacts = ModelArtifactManager(context)

    override fun getAvailableModels(): List<ModelInfo> {
        val artifactsById = ModelArtifactManager.ARTIFACTS.associateBy { it.id }
        fun status(id: String): ModelState {
            val artifact = artifactsById[id] ?: return ModelState.UNAVAILABLE
            return if (artifacts.isValid(artifacts.localFile(artifact), artifact)) {
                ModelState.INSTALLED
            } else {
                ModelState.NOT_INSTALLED
            }
        }

        return listOf(
            ModelInfo(
                id = "mlkit-subject-segmentation",
                name = "Subject Segmentation",
                description = "Removes background from images.",
                status = ModelState.INSTALLED,
                runtime = "ML Kit"
            ),
            ModelInfo(
                id = "mlkit-object-detection",
                name = "Object Detection",
                description = "Detects objects within an image.",
                status = ModelState.INSTALLED,
                runtime = "ML Kit"
            ),
            ModelInfo(
                id = "llama/inpainting_lama_2025jan",
                name = "LaMa Inpainting",
                description = "Local object removal and inpainting.",
                status = status("llama/inpainting_lama_2025jan"),
                version = "2025jan",
                sizeBytes = 92_591_623L,
                license = "Apache-2.0",
                sha256 = "7df918ac3921d3daf0aae1d219776cf0dc4e4935f035af81841b40adcf74fdf2",
                source = "OpenCV Zoo / Hugging Face",
                runtime = "ONNX Runtime"
            ),
            ModelInfo(
                id = "realesrgan_x2plus",
                name = "Real-ESRGAN 2x",
                description = "2x Image Upscaling.",
                status = status("realesrgan_x2plus"),
                version = "x2plus",
                sizeBytes = 67_200_000L,
                license = "BSD-3-Clause",
                sha256 = null,
                source = "Real-ESRGAN / QtMeshEditor model mirror",
                runtime = "ONNX Runtime"
            ),
            ModelInfo(
                id = "cpga_fp16",
                name = "CPGA-Net Low-Light",
                description = "Low-light enhancement.",
                status = status("cpga_fp16"),
                version = "fp16",
                sizeBytes = 93_820L,
                license = "MIT",
                sha256 = "8b125569618cbc342b3c0a095f712dfc899ac739e896208baf13cb1769c4c319",
                source = "LiteRT Community / CPGA-Net",
                runtime = "LiteRT/TFLite"
            )
        )
    }

    override fun downloadModel(id: String) {
        // UI-triggered downloads should call ModelArtifactManager.ensureInstalled(...) from a
        // coroutine. This synchronous interface remains for compatibility with existing callers.
    }

    override fun deleteModel(id: String) {
        val artifact = ModelArtifactManager.ARTIFACTS.firstOrNull { it.id == id } ?: return
        artifacts.localFile(artifact).delete()
    }

    override fun getCapabilityStatus(capability: AICapability): AICapabilityStatus {
        fun installed(id: String): Boolean {
            val artifact = ModelArtifactManager.ARTIFACTS.firstOrNull { it.id == id } ?: return false
            return artifacts.isValid(artifacts.localFile(artifact), artifact)
        }

        return when (capability) {
            AICapability.BACKGROUND_REMOVAL -> AICapabilityStatus.AVAILABLE
            AICapability.OBJECT_DETECTION -> AICapabilityStatus.AVAILABLE
            AICapability.OBJECT_REMOVAL -> if (installed("llama/inpainting_lama_2025jan")) AICapabilityStatus.AVAILABLE else AICapabilityStatus.REQUIRES_MODEL
            AICapability.UPSCALE -> if (installed("realesrgan_x2plus")) AICapabilityStatus.AVAILABLE else AICapabilityStatus.REQUIRES_MODEL
            AICapability.ENHANCEMENT -> if (installed("cpga_fp16")) AICapabilityStatus.AVAILABLE else AICapabilityStatus.REQUIRES_MODEL
            AICapability.RESTYLE,
            AICapability.GENERATIVE_FILL,
            AICapability.OBJECT_REPLACEMENT,
            AICapability.IMAGE_TO_IMAGE -> AICapabilityStatus.REQUIRES_PROVIDER
            AICapability.VIDEO_OBJECT_DETECTION,
            AICapability.VIDEO_OBJECT_TRACKING,
            AICapability.SMART_CUT,
            AICapability.SMART_REFRAME -> AICapabilityStatus.AVAILABLE
            AICapability.AUTO_CAPTIONS,
            AICapability.IMAGE_TO_VIDEO,
            AICapability.VIDEO_TO_VIDEO,
            AICapability.VIDEO_EXTENSION -> AICapabilityStatus.REQUIRES_CLOUD
            AICapability.VIDEO_ENHANCEMENT -> AICapabilityStatus.DEVICE_NOT_SUPPORTED
        }
    }
}
