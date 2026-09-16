package com.example.ai.model
import android.content.Context

interface ModelManager {
    fun getAvailableModels(): List<ModelInfo>
    fun downloadModel(id: String)
    fun deleteModel(id: String)
    fun getCapabilityStatus(capability: AICapability): AICapabilityStatus
}

class AppModelManager(private val context: Context) : ModelManager {
    override fun getAvailableModels(): List<ModelInfo> {
        val lamaInstalled = isModelInstalled("inpainting_lama_2025jan.onnx")
        val esrganInstalled = isModelInstalled("RealESRGAN_x2plus.onnx")
        val cpgaInstalled = isModelInstalled("cpga_fp16.tflite")

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
                status = if (lamaInstalled) ModelState.INSTALLED else ModelState.NOT_INSTALLED,
                version = "2025jan",
                sizeBytes = 88300000L,
                license = "Apache License 2.0",
                sha256 = "7df918ac3921d3daf0aae1d219776cf0dc4e4935f035af81841b40adcf74fdf2",
                source = "OpenCV Zoo",
                runtime = "ONNX Runtime"
            ),
            ModelInfo(
                id = "realesrgan_x2plus",
                name = "Real-ESRGAN 2x",
                description = "2x Image Upscaling.",
                status = if (esrganInstalled) ModelState.INSTALLED else ModelState.NOT_INSTALLED,
                version = "x2plus",
                license = "BSD-3-Clause",
                sha256 = "d9d1c876b9253ee6a7bb4b7a8472da59df97e4461c4ef7bf2b3172dcea044c63",
                source = "Real-ESRGAN",
                runtime = "ONNX Runtime"
            ),
            ModelInfo(
                id = "cpga_fp16",
                name = "CPGA-Net Low-Light",
                description = "Low-light enhancement.",
                status = if (cpgaInstalled) ModelState.INSTALLED else ModelState.NOT_INSTALLED,
                version = "fp16",
                sizeBytes = 263000L,
                license = "MIT",
                sha256 = "4254d1d94e70b5a6862514e4a4ceaf04eb76044d7ab7732f31e5ac1ff0980501",
                source = "LiteRT-Models",
                runtime = "LiteRT/TFLite"
            )
        )
    }

    private fun isModelInstalled(filename: String): Boolean {
        return try {
            context.assets.open("models/$filename").use { true }
        } catch (e: Exception) {
            false
        }
    }

    override fun downloadModel(id: String) {}
    override fun deleteModel(id: String) {}

    override fun getCapabilityStatus(capability: AICapability): AICapabilityStatus {
        val lamaInstalled = isModelInstalled("inpainting_lama_2025jan.onnx")
        val esrganInstalled = isModelInstalled("RealESRGAN_x2plus.onnx")
        val cpgaInstalled = isModelInstalled("cpga_fp16.tflite")

        return when (capability) {
            AICapability.BACKGROUND_REMOVAL -> AICapabilityStatus.AVAILABLE
            AICapability.OBJECT_DETECTION -> AICapabilityStatus.AVAILABLE
            AICapability.OBJECT_REMOVAL -> if (lamaInstalled) AICapabilityStatus.AVAILABLE else AICapabilityStatus.REQUIRES_MODEL
            AICapability.UPSCALE -> if (esrganInstalled) AICapabilityStatus.AVAILABLE else AICapabilityStatus.REQUIRES_MODEL
            AICapability.ENHANCEMENT -> if (cpgaInstalled) AICapabilityStatus.AVAILABLE else AICapabilityStatus.REQUIRES_MODEL
            
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
