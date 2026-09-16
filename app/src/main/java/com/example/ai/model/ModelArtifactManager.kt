package com.example.ai.model

import android.content.Context
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

data class ArtifactState(
    val state: ModelInstallState = ModelInstallState.NOT_INSTALLED,
    val progress: Int = 0,
    val errorMessage: String? = null,
    val currentBytes: Long = 0L,
    val expectedBytes: Long? = null,
    val checksumValid: Boolean? = null
)

class ModelArtifactManager(
    context: Context,
    private val client: OkHttpClient = OkHttpClient()
) {
    private val appContext = context.applicationContext
    private val downloadMutexes = ConcurrentHashMap<String, Mutex>()
    private val _states = MutableStateFlow<Map<String, ArtifactState>>(emptyMap())
    val states: StateFlow<Map<String, ArtifactState>> = _states.asStateFlow()

    companion object {
        private const val LAMA_SHA256 = "7df918ac3921d3daf0aae1d219776cf0dc4e4935f035af81841b40adcf74fdf2"
        private const val CPGA_SHA256 = "8b125569618cbc342b3c0a095f712dfc899ac739e896208baf13cb1769c4c319"
        private val ESRGAN_SHA256: String? = null

        val ARTIFACTS = listOf(
            ModelArtifact(
                id = "llama/inpainting_lama_2025jan",
                filename = "inpainting_lama_2025jan.onnx",
                downloadUrl = "https://huggingface.co/opencv/opencv_zoo/resolve/main/models/inpainting_lama/inpainting_lama_2025jan.onnx?download=true",
                expectedSha256 = LAMA_SHA256,
                minimumBytes = 80L * 1024L * 1024L,
                license = "Apache-2.0",
                source = "OpenCV Zoo / Hugging Face",
                runtime = "ONNX Runtime",
                expectedInputs = setOf("image", "mask"),
                expectedOutputs = setOf("output")
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

    fun artifact(id: String): ModelArtifact? {
        val normalized = when (id.lowercase()) {
            "lama", "inpainting", "inpainting_lama", "llama/inpainting_lama_2025jan" -> "llama/inpainting_lama_2025jan"
            "realesrgan", "realesrgan_x2plus", "realesrgan-x2plus", "upscale" -> "realesrgan_x2plus"
            "cpga", "cpga_fp16", "cpganet", "lowlight", "low_light" -> "cpga_fp16"
            else -> id
        }
        return ARTIFACTS.firstOrNull {
            it.id.equals(normalized, ignoreCase = true) || it.id.equals(id, ignoreCase = true)
        }
    }

    fun localFile(artifact: ModelArtifact): File =
        File(File(appContext.filesDir, "models"), artifact.filename)

    fun getArtifactState(id: String): ArtifactState {
        val current = _states.value[id]
        if (current != null) return current
        val art = artifact(id) ?: return ArtifactState(state = ModelInstallState.NOT_INSTALLED)
        val file = localFile(art)
        return if (file.exists() && isValid(file, art)) {
            ArtifactState(
                state = ModelInstallState.READY,
                progress = 100,
                currentBytes = file.length(),
                expectedBytes = file.length(),
                checksumValid = if (art.expectedSha256 != null) true else null
            )
        } else {
            ArtifactState(state = ModelInstallState.NOT_INSTALLED)
        }
    }

    private fun updateState(id: String, state: ArtifactState) {
        _states.value = _states.value.toMutableMap().apply { put(id, state) }
    }

    suspend fun isInstalled(id: String): Boolean = withContext(Dispatchers.IO) {
        val art = artifact(id) ?: return@withContext false
        val file = localFile(art)
        file.exists() && isValid(file, art)
    }

    suspend fun ensureModel(id: String): Result<File> = runCatching {
        val art = artifact(id) ?: throw IllegalArgumentException("Unknown model artifact id: $id")
        ensureInstalled(art)
    }

    suspend fun downloadModel(id: String, onProgress: (Int) -> Unit = {}): Result<File> = runCatching {
        val art = artifact(id) ?: throw IllegalArgumentException("Unknown model artifact id: $id")
        ensureInstalled(art, onProgress)
    }

    suspend fun deleteModel(id: String): Boolean = withContext(Dispatchers.IO) {
        val art = artifact(id) ?: return@withContext false
        val file = localFile(art)
        val deleted = if (file.exists()) file.delete() else true
        val partial = File(file.parentFile, "${file.name}.part")
        if (partial.exists()) partial.delete()
        updateState(art.id, ArtifactState(state = ModelInstallState.NOT_INSTALLED))
        deleted
    }

    suspend fun ensureInstalled(artifact: ModelArtifact, onProgress: (Int) -> Unit = {}): File =
        withContext(Dispatchers.IO) {
            val target = localFile(artifact)
            if (isValid(target, artifact)) {
                updateState(
                    artifact.id,
                    ArtifactState(
                        state = ModelInstallState.READY,
                        progress = 100,
                        currentBytes = target.length(),
                        expectedBytes = target.length(),
                        checksumValid = if (artifact.expectedSha256 != null) true else null
                    )
                )
                return@withContext target
            }

            val mutex = downloadMutexes.getOrPut(artifact.id) { Mutex() }
            mutex.withLock {
                if (isValid(target, artifact)) {
                    updateState(
                        artifact.id,
                        ArtifactState(
                            state = ModelInstallState.READY,
                            progress = 100,
                            currentBytes = target.length(),
                            expectedBytes = target.length(),
                            checksumValid = if (artifact.expectedSha256 != null) true else null
                        )
                    )
                    return@withContext target
                }

                val partial = File(target.parentFile, "${target.name}.part")
                target.parentFile?.mkdirs()
                if (partial.exists()) partial.delete()

                updateState(
                    artifact.id,
                    ArtifactState(
                        state = ModelInstallState.DOWNLOADING,
                        progress = 0,
                        currentBytes = 0L,
                        expectedBytes = null
                    )
                )

                try {
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
                                    coroutineContext.ensureActive()
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    output.write(buffer, 0, read)
                                    downloaded += read
                                    val pct = if (total != null && total > 0) {
                                        ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                                    } else 0
                                    onProgress(pct)
                                    updateState(
                                        artifact.id,
                                        ArtifactState(
                                            state = ModelInstallState.DOWNLOADING,
                                            progress = pct,
                                            currentBytes = downloaded,
                                            expectedBytes = total
                                        )
                                    )
                                }
                            }
                        }
                    }

                    updateState(
                        artifact.id,
                        ArtifactState(
                            state = ModelInstallState.VERIFYING,
                            progress = 100,
                            currentBytes = partial.length(),
                            expectedBytes = partial.length()
                        )
                    )

                    coroutineContext.ensureActive()

                    if (!partial.isFile || partial.length() < artifact.minimumBytes) {
                        val actualSize = partial.length()
                        partial.delete()
                        throw IllegalStateException(
                            "MODEL_INVALID: File size too small ($actualSize < ${artifact.minimumBytes})"
                        )
                    }

                    if (artifact.expectedSha256 != null) {
                        val computed = sha256(partial)
                        if (!computed.equals(artifact.expectedSha256, ignoreCase = true)) {
                            partial.delete()
                            throw IllegalStateException("Model SHA-256 mismatch")
                        }
                    }

                    val structuralValid = when (artifact.runtime) {
                        "ONNX Runtime" -> validateOnnxModel(
                            partial,
                            artifact.expectedInputs,
                            artifact.expectedOutputs
                        )
                        "LiteRT/TFLite" -> validateTfliteModel(partial)
                        else -> true
                    }

                    if (!structuralValid) {
                        partial.delete()
                        throw IllegalStateException("MODEL_INVALID: Failed structural graph validation")
                    }

                    if (target.exists()) target.delete()
                    if (!partial.renameTo(target)) {
                        partial.delete()
                        throw IOException("MODEL_INSTALL_FAILED: Unable to activate ${artifact.filename}")
                    }

                    updateState(
                        artifact.id,
                        ArtifactState(
                            state = ModelInstallState.READY,
                            progress = 100,
                            currentBytes = target.length(),
                            expectedBytes = target.length(),
                            checksumValid = if (artifact.expectedSha256 != null) true else null
                        )
                    )
                    target
                } catch (e: CancellationException) {
                    if (partial.exists()) partial.delete()
                    updateState(
                        artifact.id,
                        ArtifactState(
                            state = ModelInstallState.FAILED,
                            errorMessage = "Download cancelled"
                        )
                    )
                    throw e
                } catch (t: Throwable) {
                    if (partial.exists()) partial.delete()
                    updateState(
                        artifact.id,
                        ArtifactState(
                            state = ModelInstallState.FAILED,
                            errorMessage = t.message ?: "Download failed"
                        )
                    )
                    throw t
                }
            }
        }

    fun isValid(file: File, artifact: ModelArtifact): Boolean {
        if (!file.isFile || file.length() < artifact.minimumBytes) return false

        if (artifact.expectedSha256 != null) {
            val hash = try {
                runSha256Sync(file)
            } catch (_: Exception) {
                return false
            }
            if (!hash.equals(artifact.expectedSha256, ignoreCase = true)) return false
        }

        return when (artifact.runtime) {
            "ONNX Runtime" -> validateOnnxModel(
                file,
                artifact.expectedInputs,
                artifact.expectedOutputs
            )
            "LiteRT/TFLite" -> validateTfliteModel(file)
            else -> true
        }
    }

    private suspend fun sha256(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun runSha256Sync(file: File): String {
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

    private fun validateOnnxModel(
        modelFile: File,
        expectedInputs: Set<String>,
        expectedOutputs: Set<String>
    ): Boolean {
        return try {
            val env = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions()
            try {
                val session = env.createSession(modelFile.absolutePath, opts)
                try {
                    val inputs = session.inputNames
                    val outputs = session.outputNames
                    if (inputs.isEmpty() || outputs.isEmpty()) return false

                    val inputsMatch = expectedInputs.isEmpty() || expectedInputs.all { expected ->
                        inputs.any { actual ->
                            actual.equals(expected, ignoreCase = true)
                        }
                    }
                    val outputsMatch = expectedOutputs.isEmpty() || expectedOutputs.all { expected ->
                        outputs.any { actual ->
                            actual.equals(expected, ignoreCase = true)
                        }
                    }
                    inputsMatch && outputsMatch
                } finally {
                    session.close()
                }
            } finally {
                opts.close()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun validateTfliteModel(modelFile: File): Boolean {
        return try {
            val interpreter = Interpreter(modelFile)
            try {
                val input = interpreter.getInputTensor(0)
                val output = interpreter.getOutputTensor(0)
                input.dataType() == DataType.FLOAT32 &&
                    output.dataType() == DataType.FLOAT32 &&
                    input.shape().contentEquals(intArrayOf(1, 3, 256, 256)) &&
                    output.shape().contentEquals(intArrayOf(1, 3, 256, 256))
            } finally {
                interpreter.close()
            }
        } catch (_: Exception) {
            false
        }
    }
}
