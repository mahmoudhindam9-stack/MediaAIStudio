package com.example.ai.image.inpainting

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.example.ai.core.AIError
import com.example.ai.core.AIProgress
import com.example.ai.core.AIResult
import com.example.ai.core.AIProviderType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import kotlin.coroutines.coroutineContext

class LaMaInpaintingEngine(context: Context) {
    companion object {
        private const val MODEL_WIDTH = 512
        private const val MODEL_HEIGHT = 512
    }

    private val appContext = context.applicationContext
    private val env = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var imageInputName: String? = null
    private var maskInputName: String? = null
    private var outputName: String? = null

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (session != null) return@withContext
        val file = LaMaModelLoader(appContext).getModelFile()
        if (!file.isFile || file.length() < 80L * 1024L * 1024L) {
            throw IllegalStateException("MODEL_INVALID: LaMa binary is missing or incomplete")
        }

        try {
            val opts = OrtSession.SessionOptions()
            val created = env.createSession(file.absolutePath, opts)
            val inputs = created.inputNames.toList()
            val outputs = created.outputNames.toList()
            require(inputs.size >= 2) { "MODEL_SIGNATURE_UNSUPPORTED: LaMa needs image and mask inputs" }
            require(outputs.isNotEmpty()) { "MODEL_SIGNATURE_UNSUPPORTED: LaMa has no outputs" }

            imageInputName = inputs.firstOrNull { it.equals("img", ignoreCase = true) || it.equals("image", ignoreCase = true) } ?: inputs[0]
            maskInputName = inputs.firstOrNull { it.equals("mask", ignoreCase = true) } ?: inputs[1]
            outputName = outputs[0]
            session = created
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            session?.close()
            session = null
            throw IllegalStateException("MODEL_RUNTIME_ERROR: ${t.message}", t)
        }
    }

    suspend fun process(
        sourceUri: String,
        maskData: String,
        onProgress: (AIProgress) -> Unit
    ): AIResult = withContext(Dispatchers.Default) {
        var imageTensor: OnnxTensor? = null
        var maskTensor: OnnxTensor? = null
        try {
            coroutineContext.ensureActive()
            onProgress(AIProgress(0.05f, "Initializing LaMa"))
            initialize()
            val sess = session ?: return@withContext AIResult.Error(AIError.ModelUnavailable)

            coroutineContext.ensureActive()
            onProgress(AIProgress(0.15f, "Loading image"))
            val source = loadBitmap(Uri.parse(sourceUri))
                ?: return@withContext AIResult.Error(AIError.InvalidInput)

            coroutineContext.ensureActive()
            val imageInput = prepareImage(source)
            val maskInput = prepareMask(maskData)

            imageTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(imageInput),
                longArrayOf(1, 3, MODEL_HEIGHT.toLong(), MODEL_WIDTH.toLong())
            )
            maskTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(maskInput),
                longArrayOf(1, 1, MODEL_HEIGHT.toLong(), MODEL_WIDTH.toLong())
            )

            coroutineContext.ensureActive()
            onProgress(AIProgress(0.45f, "Running inpainting"))

            val results = sess.run(
                mapOf(
                    requireNotNull(imageInputName) to imageTensor,
                    requireNotNull(maskInputName) to maskTensor
                )
            )

            val output = try {
                val opt = results.get(requireNotNull(outputName))
                val value = if (opt.isPresent) opt.get().value else null
                val extracted = extractFloatArray(value)
                    ?: return@withContext AIResult.Error(AIError.ProcessingFailure)
                if (extracted.size < MODEL_WIDTH * MODEL_HEIGHT * 3) {
                    return@withContext AIResult.Error(AIError.ProcessingFailure)
                }
                extracted
            } finally {
                results.close()
            }

            coroutineContext.ensureActive()
            onProgress(AIProgress(0.75f, "Rendering result"))
            val resultBitmap = outputToBitmap(output, MODEL_WIDTH, MODEL_HEIGHT)
            val restored = Bitmap.createScaledBitmap(resultBitmap, source.width, source.height, true)
            resultBitmap.recycle()
            source.recycle()

            val outputFile = writeResult(restored)
            val contentUri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                outputFile
            )

            onProgress(AIProgress(1f, "Complete"))
            AIResult.Success(
                outputUri = contentUri.toString(),
                processingType = "ObjectRemoval",
                providerUsed = AIProviderType.ON_DEVICE
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            AIResult.Error(AIError.Unknown(t.message ?: "LaMa inference failed"))
        } finally {
            imageTensor?.close()
            maskTensor?.close()
        }
    }

    private fun prepareImage(source: Bitmap): FloatArray {
        val scaled = Bitmap.createScaledBitmap(source, MODEL_WIDTH, MODEL_HEIGHT, true)
        val pixels = IntArray(MODEL_WIDTH * MODEL_HEIGHT)
        scaled.getPixels(pixels, 0, MODEL_WIDTH, 0, 0, MODEL_WIDTH, MODEL_HEIGHT)
        scaled.recycle()
        val out = FloatArray(pixels.size * 3)
        val plane = pixels.size
        for (i in pixels.indices) {
            val c = pixels[i]
            out[i] = Color.red(c) / 255f
            out[plane + i] = Color.green(c) / 255f
            out[2 * plane + i] = Color.blue(c) / 255f
        }
        return out
    }

    private fun prepareMask(maskData: String): FloatArray {
        val mask = Bitmap.createBitmap(MODEL_WIDTH, MODEL_HEIGHT, Bitmap.Config.ALPHA_8)
        Canvas(mask).apply {
            drawColor(Color.BLACK)
            if (maskData.isNotBlank() && maskData.startsWith("[")) {
                val parts = maskData.removeSurrounding("[", "]").split(',')
                if (parts.size == 4) {
                    val left = parts[0].toFloatOrNull() ?: 0f
                    val top = parts[1].toFloatOrNull() ?: 0f
                    val right = parts[2].toFloatOrNull() ?: 1f
                    val bottom = parts[3].toFloatOrNull() ?: 1f
                    val paint = Paint().apply {
                        color = Color.WHITE
                        style = Paint.Style.FILL
                    }
                    drawRect(
                        RectF(
                            left.coerceIn(0f, 1f) * MODEL_WIDTH,
                            top.coerceIn(0f, 1f) * MODEL_HEIGHT,
                            right.coerceIn(0f, 1f) * MODEL_WIDTH,
                            bottom.coerceIn(0f, 1f) * MODEL_HEIGHT
                        ),
                        paint
                    )
                }
            }
        }
        val alpha = ByteArray(MODEL_WIDTH * MODEL_HEIGHT)
        mask.copyPixelsToBuffer(java.nio.ByteBuffer.wrap(alpha))
        mask.recycle()
        val out = FloatArray(alpha.size)
        for (i in alpha.indices) {
            out[i] = (alpha[i].toInt() and 0xFF) / 255f
        }
        return out
    }

    private fun outputToBitmap(values: FloatArray, width: Int, height: Int): Bitmap {
        val pixels = IntArray(width * height)
        val plane = width * height
        // Calculate the output scale ONCE before pixel conversion to avoid O(n^2) repeated scanning
        val maxValue = values.maxOrNull() ?: 1f
        val scale = if (maxValue <= 1.5f) 255f else 1f
        for (i in pixels.indices) {
            val r = (values[i] * scale).coerceIn(0f, 255f).toInt()
            val g = (values[plane + i] * scale).coerceIn(0f, 255f).toInt()
            val b = (values[2 * plane + i] * scale).coerceIn(0f, 255f).toInt()
            pixels[i] = Color.rgb(r, g, b)
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    private fun extractFloatArray(value: Any?): FloatArray? {
        return when (value) {
            is FloatArray -> value
            is Array<*> -> value.flatMap { extractFloatValues(it).asIterable() }.toFloatArray()
            else -> null
        }
    }

    private fun extractFloatValues(value: Any?): List<Float> = when (value) {
        is FloatArray -> value.toList()
        is Array<*> -> value.flatMap { extractFloatValues(it) }
        else -> emptyList()
    }

    private fun loadBitmap(uri: Uri): Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(appContext.contentResolver, uri)) { decoder, _, _ ->
            decoder.isMutableRequired = false
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(appContext.contentResolver, uri)
    }

    private fun writeResult(bitmap: Bitmap): File {
        val file = File(appContext.cacheDir, "ai_lama_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Failed to encode LaMa result" }
        }
        bitmap.recycle()
        return file
    }

    fun release() {
        session?.close()
        session = null
    }
}
