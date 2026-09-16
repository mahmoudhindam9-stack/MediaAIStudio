package com.example.ai.image.upscale

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.example.ai.core.AIError
import com.example.ai.core.AIProgress
import com.example.ai.core.AIResult
import com.example.ai.core.AIProviderType
import com.example.ai.model.ModelArtifactManager
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import kotlin.math.min

class RealEsrganUpscaleEngine(private val context: Context) {
    companion object {
        private const val TILE = 256
        private const val OVERLAP = 16
    }

    private val env = OrtEnvironment.getEnvironment()
    private val artifacts = ModelArtifactManager(context)
    private var session: OrtSession? = null
    private var inputName: String? = null
    private var outputName: String? = null

    private suspend fun initialize() {
        if (session != null) return
        val artifact = artifacts.artifact("realesrgan_x2plus")
            ?: error("MODEL_NOT_CONFIGURED: Real-ESRGAN artifact is missing")
        val file = artifacts.ensureInstalled(artifact)
        try {
            val created = env.createSession(file.absolutePath, OrtSession.SessionOptions())
            require(created.inputNames.isNotEmpty()) { "MODEL_SIGNATURE_UNSUPPORTED: no input" }
            require(created.outputNames.isNotEmpty()) { "MODEL_SIGNATURE_UNSUPPORTED: no output" }
            inputName = created.inputNames.first()
            outputName = created.outputNames.first()
            session = created
        } catch (t: Throwable) {
            session?.close()
            session = null
            throw IllegalStateException("MODEL_RUNTIME_ERROR: ${t.message}", t)
        }
    }

    suspend fun process(sourceUri: String, scaleFactor: Int, onProgress: (AIProgress) -> Unit): AIResult {
        if (scaleFactor != 2) return AIResult.Error(AIError.ModelUnavailable)
        return try {
            onProgress(AIProgress(0.05f, "Initializing Real-ESRGAN"))
            initialize()
            val sess = session ?: return AIResult.Error(AIError.ModelUnavailable)
            val source = loadBitmap(Uri.parse(sourceUri)) ?: return AIResult.Error(AIError.InvalidInput)
            val output = Bitmap.createBitmap(source.width * 2, source.height * 2, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)

            val step = TILE - OVERLAP
            var tileIndex = 0
            val tileCountX = ((source.width - 1) / step) + 1
            val tileCountY = ((source.height - 1) / step) + 1
            val totalTiles = tileCountX * tileCountY

            var top = 0
            while (top < source.height) {
                val bottom = min(top + TILE, source.height)
                val tileHeight = bottom - top
                var left = 0
                while (left < source.width) {
                    val right = min(left + TILE, source.width)
                    val tileWidth = right - left
                    val tile = Bitmap.createBitmap(source, left, top, tileWidth, tileHeight)
                    try {
                        val values = bitmapToNchw(tile)
                        val input = OnnxTensor.createTensor(
                            env,
                            FloatBuffer.wrap(values),
                            longArrayOf(1, 3, tileHeight.toLong(), tileWidth.toLong())
                        )
                        try {
                            val results = sess.run(mapOf(requireNotNull(inputName) to input))
                            try {
                                val raw = results.get(requireNotNull(outputName)).value
                                val outputValues = extractFloatArray(raw)
                                    ?: return AIResult.Error(AIError.ProcessingFailure)
                                val expected = tileWidth * tileHeight * 4 * 3
                                if (outputValues.size < expected) {
                                    return AIResult.Error(AIError.ProcessingFailure)
                                }
                                val outTile = tensorToBitmap(outputValues, tileWidth * 2, tileHeight * 2)
                                canvas.drawBitmap(outTile, left * 2f, top * 2f, null)
                                outTile.recycle()
                            } finally {
                                results.close()
                            }
                        } finally {
                            input.close()
                        }
                    } finally {
                        tile.recycle()
                    }
                    tileIndex++
                    onProgress(AIProgress(0.15f + 0.75f * tileIndex / totalTiles.toFloat(), "Upscaling ${tileIndex}/${totalTiles}"))
                    left += if (right == source.width) tileWidth else step
                }
                top += if (bottom == source.height) tileHeight else step
            }
            source.recycle()

            val file = File(context.cacheDir, "ai_esrgan_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                check(output.compress(Bitmap.CompressFormat.PNG, 100, out)) { "Failed to encode Real-ESRGAN result" }
            }
            output.recycle()
            onProgress(AIProgress(1f, "Complete"))
            AIResult.Success(Uri.fromFile(file).toString(), "Upscale", AIProviderType.ON_DEVICE)
        } catch (t: Throwable) {
            AIResult.Error(AIError.Unknown(t.message ?: "Real-ESRGAN inference failed"))
        }
    }

    private fun bitmapToNchw(bitmap: Bitmap): FloatArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val plane = pixels.size
        return FloatArray(plane * 3).also { out ->
            for (i in pixels.indices) {
                val c = pixels[i]
                out[i] = Color.red(c) / 255f
                out[plane + i] = Color.green(c) / 255f
                out[2 * plane + i] = Color.blue(c) / 255f
            }
        }
    }

    private fun tensorToBitmap(values: FloatArray, width: Int, height: Int): Bitmap {
        val pixels = IntArray(width * height)
        val plane = width * height
        for (i in pixels.indices) {
            val r = (values[i] * 255f).coerceIn(0f, 255f).toInt()
            val g = (values[plane + i] * 255f).coerceIn(0f, 255f).toInt()
            val b = (values[2 * plane + i] * 255f).coerceIn(0f, 255f).toInt()
            pixels[i] = Color.rgb(r, g, b)
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    private fun extractFloatArray(value: Any?): FloatArray? = when (value) {
        is FloatArray -> value
        is Array<*> -> value.flatMap { extractFloatValues(it) }.toFloatArray()
        else -> null
    }

    private fun extractFloatValues(value: Any?): List<Float> = when (value) {
        is FloatArray -> value.toList()
        is Array<*> -> value.flatMap { extractFloatValues(it) }
        else -> emptyList()
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
        session?.close()
        session = null
    }
}
