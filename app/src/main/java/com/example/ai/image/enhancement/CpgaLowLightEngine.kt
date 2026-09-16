package com.example.ai.image.enhancement

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.ai.core.AIError
import com.example.ai.core.AIProgress
import com.example.ai.core.AIResult
import com.example.ai.core.AIProviderType
import com.example.ai.model.ModelArtifactManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

class CpgaLowLightEngine(context: Context) {
    companion object {
        private const val MODEL_SIZE = 256
    }

    private val appContext = context.applicationContext
    private val artifacts = ModelArtifactManager(appContext)
    private var interpreter: Interpreter? = null
    private var inputShape: IntArray = intArrayOf()
    private var outputShape: IntArray = intArrayOf()

    private suspend fun initialize() = withContext(Dispatchers.IO) {
        if (interpreter != null) return@withContext
        val modelFile = artifacts.ensureModel("cpga_fp16").getOrThrow()
        try {
            val created = Interpreter(modelFile)
            val input = created.getInputTensor(0)
            val output = created.getOutputTensor(0)
            inputShape = input.shape()
            outputShape = output.shape()
            require(input.dataType() == DataType.FLOAT32) {
                "MODEL_SIGNATURE_UNSUPPORTED: CPGA input is not FLOAT32"
            }
            require(output.dataType() == DataType.FLOAT32) {
                "MODEL_SIGNATURE_UNSUPPORTED: CPGA output is not FLOAT32"
            }
            require(inputShape.contentEquals(intArrayOf(1, 3, MODEL_SIZE, MODEL_SIZE))) {
                "MODEL_SIGNATURE_UNSUPPORTED: expected [1,3,256,256], got ${inputShape.contentToString()}"
            }
            require(outputShape.contentEquals(intArrayOf(1, 3, MODEL_SIZE, MODEL_SIZE))) {
                "MODEL_SIGNATURE_UNSUPPORTED: expected [1,3,256,256] output, got ${outputShape.contentToString()}"
            }
            interpreter = created
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            interpreter?.close()
            interpreter = null
            throw IllegalStateException("MODEL_RUNTIME_ERROR: ${t.message}", t)
        }
    }

    suspend fun process(sourceUri: String, onProgress: (AIProgress) -> Unit): AIResult = withContext(Dispatchers.Default) {
        try {
            coroutineContext.ensureActive()
            onProgress(AIProgress(0.05f, "Initializing CPGA"))
            initialize()
            val tflite = interpreter ?: return@withContext AIResult.Error(AIError.ModelUnavailable)

            coroutineContext.ensureActive()
            val source = loadBitmap(Uri.parse(sourceUri))
                ?: return@withContext AIResult.Error(AIError.InvalidInput)

            onProgress(AIProgress(0.2f, "Preparing low-light enhancement"))
            val originalWidth = source.width
            val originalHeight = source.height

            // Preserve aspect ratio by cropping the largest square or scaling appropriately
            val cropSize = minOf(originalWidth, originalHeight)
            val cropLeft = (originalWidth - cropSize) / 2
            val cropTop = (originalHeight - cropSize) / 2

            val croppedSquare = Bitmap.createBitmap(source, cropLeft, cropTop, cropSize, cropSize)
            val modelInput = Bitmap.createScaledBitmap(croppedSquare, MODEL_SIZE, MODEL_SIZE, true)
            if (croppedSquare !== source && croppedSquare !== modelInput) {
                croppedSquare.recycle()
            }

            coroutineContext.ensureActive()
            val input = bitmapToNchwBuffer(modelInput)
            modelInput.recycle()

            val output = ByteBuffer.allocateDirect(1 * 3 * MODEL_SIZE * MODEL_SIZE * 4)
                .order(ByteOrder.nativeOrder())

            coroutineContext.ensureActive()
            onProgress(AIProgress(0.5f, "Running AI enhancement"))
            tflite.run(input, output)
            output.rewind()

            coroutineContext.ensureActive()
            onProgress(AIProgress(0.8f, "Reconstructing result"))
            val enhancedTile = outputToBitmap(output)

            // Scale enhanced tile back to original square dimensions
            val enhancedSquare = Bitmap.createScaledBitmap(enhancedTile, cropSize, cropSize, true)
            enhancedTile.recycle()

            // Reconstruct back onto the original dimensions preserving aspect ratio
            val resultBitmap = source.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(resultBitmap)
            canvas.drawBitmap(enhancedSquare, cropLeft.toFloat(), cropTop.toFloat(), null)
            enhancedSquare.recycle()
            source.recycle()

            val file = File(appContext.cacheDir, "ai_cpga_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { stream ->
                check(resultBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "Failed to encode CPGA result" }
            }
            resultBitmap.recycle()

            val contentUri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file
            )

            onProgress(AIProgress(1f, "Complete"))
            AIResult.Success(contentUri.toString(), "Enhance", AIProviderType.ON_DEVICE)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            AIResult.Error(AIError.Unknown(t.message ?: "CPGA inference failed"))
        }
    }

    private fun bitmapToNchwBuffer(bitmap: Bitmap): ByteBuffer {
        val pixels = IntArray(MODEL_SIZE * MODEL_SIZE)
        bitmap.getPixels(pixels, 0, MODEL_SIZE, 0, 0, MODEL_SIZE, MODEL_SIZE)
        val buffer = ByteBuffer.allocateDirect(1 * 3 * MODEL_SIZE * MODEL_SIZE * 4)
            .order(ByteOrder.nativeOrder())
        val plane = pixels.size
        for (i in pixels.indices) buffer.putFloat((Color.red(pixels[i]) / 255f).coerceIn(0f, 1f))
        for (i in pixels.indices) buffer.putFloat((Color.green(pixels[i]) / 255f).coerceIn(0f, 1f))
        for (i in pixels.indices) buffer.putFloat((Color.blue(pixels[i]) / 255f).coerceIn(0f, 1f))
        check(buffer.position() == plane * 3 * 4)
        buffer.rewind()
        return buffer
    }

    private fun outputToBitmap(buffer: ByteBuffer): Bitmap {
        val pixels = IntArray(MODEL_SIZE * MODEL_SIZE)
        val plane = pixels.size
        for (i in pixels.indices) {
            val r = (buffer.getFloat(i * 4).coerceIn(0f, 1f) * 255f).toInt()
            val g = (buffer.getFloat((plane + i) * 4).coerceIn(0f, 1f) * 255f).toInt()
            val b = (buffer.getFloat((2 * plane + i) * 4).coerceIn(0f, 1f) * 255f).toInt()
            pixels[i] = Color.rgb(r, g, b)
        }
        return Bitmap.createBitmap(MODEL_SIZE, MODEL_SIZE, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, MODEL_SIZE, 0, 0, MODEL_SIZE, MODEL_SIZE)
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(appContext.contentResolver, uri)) { decoder, _, _ ->
            decoder.isMutableRequired = false
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(appContext.contentResolver, uri)
    }

    fun release() {
        interpreter?.close()
        interpreter = null
    }
}
