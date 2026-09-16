package com.example.ai.image.enhancement

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.ai.core.AIError
import com.example.ai.core.AIProgress
import com.example.ai.core.AIResult
import com.example.ai.core.AIProviderType
import com.example.ai.model.ModelArtifactManager
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CpgaLowLightEngine(private val context: Context) {
    companion object {
        private const val MODEL_SIZE = 256
    }

    private val artifacts = ModelArtifactManager(context)
    private var interpreter: Interpreter? = null
    private var inputShape: IntArray = intArrayOf()
    private var outputShape: IntArray = intArrayOf()

    private suspend fun initialize() {
        if (interpreter != null) return
        val artifact = artifacts.artifact("cpga_fp16")
            ?: error("MODEL_NOT_CONFIGURED: CPGA artifact is missing")
        val file = artifacts.ensureInstalled(artifact)
        try {
            val created = Interpreter(file)
            val input = created.getInputTensor(0)
            val output = created.getOutputTensor(0)
            inputShape = input.shape()
            outputShape = output.shape()
            require(input.dataType() == org.tensorflow.lite.DataType.FLOAT32) {
                "MODEL_SIGNATURE_UNSUPPORTED: CPGA input is not FLOAT32"
            }
            require(output.dataType() == org.tensorflow.lite.DataType.FLOAT32) {
                "MODEL_SIGNATURE_UNSUPPORTED: CPGA output is not FLOAT32"
            }
            require(inputShape.contentEquals(intArrayOf(1, 3, MODEL_SIZE, MODEL_SIZE))) {
                "MODEL_SIGNATURE_UNSUPPORTED: expected [1,3,256,256], got ${inputShape.contentToString()}"
            }
            require(outputShape.contentEquals(intArrayOf(1, 3, MODEL_SIZE, MODEL_SIZE))) {
                "MODEL_SIGNATURE_UNSUPPORTED: expected [1,3,256,256] output, got ${outputShape.contentToString()}"
            }
            interpreter = created
        } catch (t: Throwable) {
            interpreter?.close()
            interpreter = null
            throw IllegalStateException("MODEL_RUNTIME_ERROR: ${t.message}", t)
        }
    }

    suspend fun process(sourceUri: String, onProgress: (AIProgress) -> Unit): AIResult {
        return try {
            onProgress(AIProgress(0.05f, "Initializing CPGA"))
            initialize()
            val tflite = interpreter ?: return AIResult.Error(AIError.ModelUnavailable)
            val source = loadBitmap(Uri.parse(sourceUri)) ?: return AIResult.Error(AIError.InvalidInput)

            onProgress(AIProgress(0.2f, "Preparing low-light enhancement"))
            val cropped = centerCrop(source, MODEL_SIZE, MODEL_SIZE)
            source.recycle()
            val input = bitmapToNchwBuffer(cropped)
            cropped.recycle()
            val output = ByteBuffer.allocateDirect(1 * 3 * MODEL_SIZE * MODEL_SIZE * 4)
                .order(ByteOrder.nativeOrder())

            onProgress(AIProgress(0.5f, "Running AI enhancement"))
            tflite.run(input, output)
            output.rewind()

            onProgress(AIProgress(0.8f, "Rendering result"))
            val result = outputToBitmap(output)
            val file = File(context.cacheDir, "ai_cpga_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { stream ->
                check(result.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "Failed to encode CPGA result" }
            }
            result.recycle()

            onProgress(AIProgress(1f, "Complete"))
            AIResult.Success(Uri.fromFile(file).toString(), "Enhance", AIProviderType.ON_DEVICE)
        } catch (t: Throwable) {
            AIResult.Error(AIError.Unknown(t.message ?: "CPGA inference failed"))
        }
    }

    private fun centerCrop(source: Bitmap, width: Int, height: Int): Bitmap {
        val scale = maxOf(width.toFloat() / source.width, height.toFloat() / source.height)
        val scaledW = (source.width * scale).toInt().coerceAtLeast(width)
        val scaledH = (source.height * scale).toInt().coerceAtLeast(height)
        val scaled = Bitmap.createScaledBitmap(source, scaledW, scaledH, true)
        val left = (scaledW - width) / 2
        val top = (scaledH - height) / 2
        val cropped = Bitmap.createBitmap(scaled, left, top, width, height)
        if (cropped !== scaled) scaled.recycle()
        return cropped
    }

    private fun bitmapToNchwBuffer(bitmap: Bitmap): ByteBuffer {
        val pixels = IntArray(MODEL_SIZE * MODEL_SIZE)
        bitmap.getPixels(pixels, 0, MODEL_SIZE, 0, 0, MODEL_SIZE, MODEL_SIZE)
        val buffer = ByteBuffer.allocateDirect(1 * 3 * MODEL_SIZE * MODEL_SIZE * 4)
            .order(ByteOrder.nativeOrder())
        val plane = pixels.size
        for (i in pixels.indices) buffer.putFloat(Color.red(pixels[i]) / 255f)
        for (i in pixels.indices) buffer.putFloat(Color.green(pixels[i]) / 255f)
        for (i in pixels.indices) buffer.putFloat(Color.blue(pixels[i]) / 255f)
        check(buffer.position() == plane * 3 * 4)
        buffer.rewind()
        return buffer
    }

    private fun outputToBitmap(buffer: ByteBuffer): Bitmap {
        val pixels = IntArray(MODEL_SIZE * MODEL_SIZE)
        val plane = pixels.size
        for (i in pixels.indices) {
            val r = (buffer.getFloat(i * 4) * 255f).coerceIn(0f, 255f).toInt()
            val g = (buffer.getFloat((plane + i) * 4) * 255f).coerceIn(0f, 255f).toInt()
            val b = (buffer.getFloat((2 * plane + i) * 4) * 255f).coerceIn(0f, 255f).toInt()
            pixels[i] = Color.rgb(r, g, b)
        }
        return Bitmap.createBitmap(MODEL_SIZE, MODEL_SIZE, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, MODEL_SIZE, 0, 0, MODEL_SIZE, MODEL_SIZE)
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
            decoder.isMutableRequired = false
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }

    fun release() {
        interpreter?.close()
        interpreter = null
    }
}
