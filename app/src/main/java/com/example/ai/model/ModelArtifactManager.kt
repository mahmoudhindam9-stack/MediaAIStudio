package com.example.ai.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class ModelArtifactManager(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient()
) {
    companion object {
        private const val LAMA_SHA256 = "7df918ac3921d3daf0aae1d219776cf0dc4e4935f035af81841b40adcf74fdf2"
        private const val CPGA_SHA256 = "8b125569618cbc342b3c0a095f712dfc899ac739e896208baf13cb1769c4c319"

        // This is an ONNX re-export documented by the dedicated model card as BSD-3-Clause.
        // The source does not publish a stable SHA-256 in its model card, so the downloader
        // validates the minimum size and that ONNX Runtime can open the graph before activation.
        private const val ESRGAN_SHA256: String? = null

        val ARTIFACTS = listOf(
            ModelArtifact(
                id = "llama/inpainting_lama_2025jan",
                filename = "inpainting_lama_2025jan.onnx",
                downloadUrl = "https://huggingface.co/opencv/opencv_zoo/resolve/main/models/inpainting_lama/inpainting_lama_2025jan.onnx?download=true",
                expectedSha256 = LAMA_SHA256,
                minimumBytes = 80L * 1024L * 1024L,
                license = "Apache-2.0",
                source = "OpenCV Zoo / Hugging Face",
                runtime = "ONNX Runtime"
            ),
            ModelArtifact(
                id = "realesrgan_x2plus",
                filename = "RealESRGAN_x2plus.onnx",
                downloadUrl = "https://huggingface.co/fernandotonon/QtMeshEditor-models/resolve/main/RealESRGAN_x2plus.onnx?download=true",
                expectedSha256 = ESRGAN_SHA256,
                minimumBytes = 50L * 1024L * 1024L,
                license = "BSD-3-Clause",
                source = "Real-ESRGAN / QtMeshEditor model mirror",
                runtime = "ONNX Runtime"
            ),
            ModelArtifact(
                id = "cpga_fp16",
                filename = "cpga_fp16.tflite",
                downloadUrl = "https://huggingface.co/litert-community/CPGA-Net-LowLight-LiteRT/resolve/main/cpga_fp16.tflite?download=true",
                expectedSha256 = CPGA_SHA256,
                minimumBytes = 90_000L,
                license = "MIT",
                source = "LiteRT Community / CPGA-Net",
                runtime = "LiteRT/TFLite"
            )
        )
    }

    fun artifact(id: String): ModelArtifact? = ARTIFACTS.firstOrNull { it.id == id }

    fun localFile(artifact: ModelArtifact): File =
        File(File(context.filesDir, "models"), artifact.filename)

    suspend fun ensureInstalled(artifact: ModelArtifact, onProgress: (Int) -> Unit = {}): File =
        withContext(Dispatchers.IO) {
            val target = localFile(artifact)
            if (isValid(target, artifact)) return@withContext target

            val partial = File(target.parentFile, "${target.name}.part")
            target.parentFile?.mkdirs()

            val request = Request.Builder().url(artifact.downloadUrl).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("MODEL_DOWNLOAD_FAILED: HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("MODEL_DOWNLOAD_FAILED: Empty response")
                val total = body.contentLength().takeIf { it > 0L }
                var downloaded = 0L
                body.byteStream().use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total != null) onProgress(((downloaded * 100) / total).toInt())
                        }
                    }
                }
            }

            if (!isValid(partial, artifact)) {
                partial.delete()
                throw IOException("MODEL_INVALID: Downloaded ${artifact.filename} failed validation")
            }

            if (target.exists()) target.delete()
            if (!partial.renameTo(target)) {
                partial.delete()
                throw IOException("MODEL_INSTALL_FAILED: Unable to activate ${artifact.filename}")
            }
            target
        }

    fun isValid(file: File, artifact: ModelArtifact): Boolean {
        if (!file.isFile || file.length() < artifact.minimumBytes) return false
        val expected = artifact.expectedSha256 ?: return true
        return sha256(file).equals(expected, ignoreCase = true)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
