package com.example.ai.model

import android.content.Context
import java.io.File

interface ModelManager {
    fun getAvailableModels(): List<ModelInfo>
    suspend fun ensureModel(id: String): Result<File>
    suspend fun isInstalled(id: String): Boolean
    suspend fun downloadModel(id: String, onProgress: (Int) -> Unit = {}): Result<File>
    suspend fun deleteModel(id: String): Boolean
    fun getCapabilityStatus(capability: AICapability): AICapabilityStatus
}

class AppModelManager(context: Context) : ModelManager {
    private val appContext = context.applicationContext
    val artifacts = ModelArtifactManager(appContext)

    override fun getAvailableModels(): List<ModelInfo> {
        fun createModelInfo(
            id: String,
            name: String,
            description: String,
            version: String?,
            defaultSizeBytes: Long?,
            license: String?,
            sha256: String?,
            source: String?,
            runtime: String?
        ): ModelInfo {
            val artifact = artifacts.artifact(id)
            val stateHolder = artifacts.getArtifactState(id)
            return ModelInfo(
                id = id,
                name = name,
                description = description,
                sizeBytes = defaultSizeBytes,
                status = stateHolder.state,
                progress = stateHolder.progress,
                errorMessage = stateHolder.errorMessage,
                currentBytes = stateHolder.currentBytes,
                expectedBytes = stateHolder.expectedBytes ?: defaultSizeBytes,
                checksumValid = stateHolder.checksumValid,
                version = version,
                license = license,
                sha256 = sha256,
                source = source,
                runtime = runtime
            )
        }

        return listOf(
            ModelInfo(
                id = "mlkit-subject-segmentation",
                name = "Subject Segmentation",
                description = "Removes background from images.",
                status = ModelInstallState.READY,
                runtime = "ML Kit"
            ),
            ModelInfo(
                id = "mlkit-object-detection",
                name = "Object Detection",
                description = "Detects objects within an image.",
                status = ModelInstallState.READY,
                runtime = "ML Kit"
            ),
            createModelInfo(
                id = "llama/inpainting_lama_2025jan",
                name = "LaMa Inpainting",
                description = "Local object removal and inpainting.",
                version = "2025jan",
                defaultSizeBytes = 92_591_623L,
                license = "Apache-2.0",
                sha256 = "7df918ac3921d3daf0aae1d219776cf0dc4e4935f035af81841b40adcf74fdf2",
                source = "OpenCV Zoo / Hugging Face",
                runtime = "ONNX Runtime"
            ),
            createModelInfo(
                id = "realesrgan_x2plus",
                name = "Real-ESRGAN 2x",
                description = "2x Image Upscaling.",
                version = "x2plus",
                defaultSizeBytes = 67_200_000L,
                license = "BSD-3-Clause",
                sha256 = null,
                source = "Real-ESRGAN / QtMeshEditor model mirror",
                runtime = "ONNX Runtime"
            ),
            createModelInfo(
                id = "cpga_fp16",
                name = "CPGA-Net Low-Light",
                description = "Low-light enhancement.",
                version = "fp16",
                defaultSizeBytes = 93_820L,
                license = "MIT",
                sha256 = "8b125569618cbc342b3c0a095f712dfc899ac739e896208baf13cb1769c4c319",
                source = "LiteRT Community / CPGA-Net",
                runtime = "LiteRT/TFLite"
            )
        )
    }

    override suspend fun ensureModel(id: String): Result<File> = artifacts.ensureModel(id)

    override suspend fun isInstalled(id: String): Boolean = artifacts.isInstalled(id)

    override suspend fun downloadModel(id: String, onProgress: (Int) -> Unit): Result<File> =
        artifacts.downloadModel(id, onProgress)

    override suspend fun deleteModel(id: String): Boolean = artifacts.deleteModel(id)

    override fun getCapabilityStatus(capability: AICapability): AICapabilityStatus {
        fun installed(id: String): Boolean {
            val artifact = artifacts.artifact(id) ?: return false
            val file = artifacts.localFile(artifact)
            return file.exists() && artifacts.isValid(file, artifact)
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
