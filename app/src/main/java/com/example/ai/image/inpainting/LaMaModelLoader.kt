package com.example.ai.image.inpainting

import android.content.Context
import java.io.File

class LaMaModelLoader(private val context: Context) {
    fun getModelFile(): File {
        val output = File(context.filesDir, "models/inpainting_lama_2025jan.onnx")

        if (!output.exists() || output.length() == 0L) {
            output.parentFile?.mkdirs()
            try {
                context.assets.open("models/inpainting_lama_2025jan.onnx").use { input ->
                    output.outputStream().use { outputStream ->
                        input.copyTo(outputStream)
                    }
                }
            } catch (e: Exception) {
                // Ignore if not present in assets during testing
            }
        }
        return output
    }
}
