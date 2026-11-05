package com.example.ai.image.upscale

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
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
import com.example.ai.model.ModelArtifactManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import kotlin.coroutines.coroutineContext
import kotlin.math.min

data class TileConfig(
    val tileSize: Int = 256,
    val overlap: Int = 16
)

class RealEsrganUpscaleEngine(context: Context) {
    private val appContext = context.applicationContext
    private val env = OrtEnvironment.getEnvironment()
    private val artifacts = ModelArtifactManager(appContext)
    private var session: OrtSession? = null
    private var inputName: String? = null
    private var outputName: String? = null

    private suspend fun initialize() = withContext(Dispatchers.IO) {
        if (session != null) return@withContext
        val modelFile = artifacts.ensureModel("realesrgan_x2plus").getOrThrow()
        try {
            val opts = OrtSession.SessionOptions()
            val created = env.createSession(modelFile.absolutePath, opts)
            require(created.inputNames.isNotEmpty()) { "MODEL_SIGNATURE_UNSUPPORTED: no input" }
            require(created.outputNames.isNotEmpty()) { "MODEL_SIGNATURE_UNSUPPORTED: no output" }
            inputName = created.inputNames.first()
            outputName = created.outputNames.first()
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
        scaleFactor: Int,
        onProgress: (AIProgress) -> Unit,
        tileConfig: TileConfig = TileConfig()
    ): AIResult = withContext(Dispatchers.Default) {
        if (scaleFactor != 2) return@withContext AIResult.Error(AIError.ModelUnavailable)
        try {
            coroutineContext.ensureActive()
            onProgress(AIProgress(0.05f, "Initializing Real-ESRGAN"))
            initialize()
            val sess = session ?: return@withContext AIResult.Error(AIError.ModelUnavailable)

            coroutineContext.ensureActive()
            val source = loadBitmap(Uri.parse(sourceUri))
                ?: return@withContext AIResult.Error(AIError.InvalidInput)

            val outputWidth = source.width * 2
            val outputHeight = source.height * 2

            // Accumulation buffers for feathered overlap blending
            val totalPixels = outputWidth * outputHeight
            val accumR = FloatArray(totalPixels)
            val accumG = FloatArray(totalPixels)
            val accumB = FloatArray(totalPixels)
            val accumWeight = FloatArray(totalPixels)

            val tileSize = tileConfig.tileSize
            val overlap = tileConfig.overlap
            val step = (tileSize - overlap).coerceAtLeast(1)

            val tileCountX = ((source.width - 1) / step) + 1
            val tileCountY = ((source.height - 1) / step) + 1
            val totalTiles = tileCountX * tileCountY
            var tileIndex = 0

            var top = 0
            while (top < source.height) {
                val bottom = min(top + tileSize, source.height)
                val tileHeight = bottom - top
                val isTop = (top == 0)
                val isBottom = (bottom == source.height)

                var left = 0
                while (left < source.width) {
                    coroutineContext.ensureActive()
                    val right = min(left + tileSize, source.width)
                    val tileWidth = right - left
                    val isLeft = (left == 0)
                    val isRight = (right == source.width)

                    val tile = Bitmap.createBitmap(source, left, top, tileWidth, tileHeight)
                    try {
                        val inputValues = bitmapToNchw(tile)
                        // Input validation: NCHW, 1x3xHxW, FLOAT32, [0,1]
                        val inputTensor = OnnxTensor.createTensor(
                            env,
                            FloatBuffer.wrap(inputValues),
                            longArrayOf(1, 3, tileHeight.toLong(), tileWidth.toLong())
                        )
                        try {
                            val results = sess.run(mapOf(requireNotNull(inputName) to inputTensor))
                            try {
                                val opt = results.get(requireNotNull(outputName))
                                val raw = if (opt.isPresent) opt.get().value else null
                                val outputValues = extractFloatArray(raw)
                                    ?: return@withContext AIResult.Error(AIError.ProcessingFailure)

                                val outTileW = tileWidth * 2
                                val outTileH = tileHeight * 2
                                val expectedSize = outTileW * outTileH * 3
                                if (outputValues.size < expectedSize) {
                                    return@withContext AIResult.Error(AIError.ProcessingFailure)
                                }

                                // Accumulate into full output buffers with feather weights
                                val dstLeft = left * 2
                                val dstTop = top * 2
                                val blendMargin = (overlap * 2).coerceAtLeast(1)
                                val plane = outTileW * outTileH

                                for (ty in 0 until outTileH) {
                                    val gy = dstTop + ty
                                    if (gy >= outputHeight) continue

                                    // Compute vertical feather weight
                                    val distTop = if (isTop) blendMargin.toFloat() else ty.toFloat()
                                    val distBottom = if (isBottom) blendMargin.toFloat() else (outTileH - 1 - ty).toFloat()
                                    val wy = (min(distTop, distBottom) / blendMargin.toFloat()).coerceIn(0.01f, 1f)

                                    for (tx in 0 until outTileW) {
                                        val gx = dstLeft + tx
                                        if (gx >= outputWidth) continue

                                        // Compute horizontal feather weight
                                        val distLeft = if (isLeft) blendMargin.toFloat() else tx.toFloat()
                                        val distRight = if (isRight) blendMargin.toFloat() else (outTileW - 1 - tx).toFloat()
                                        val wx = (min(distLeft, distRight) / blendMargin.toFloat()).coerceIn(0.01f, 1f)

                                        val weight = wx * wy
                                        val localIndex = ty * outTileW + tx
                                        val globalIndex = gy * outputWidth + gx

                                        val rVal = (outputValues[localIndex] * 255f).coerceIn(0f, 255f)
                                        val gVal = (outputValues[plane + localIndex] * 255f).coerceIn(0f, 255f)
                                        val bVal = (outputValues[2 * plane + localIndex] * 255f).coerceIn(0f, 255f)

                                        accumR[globalIndex] += rVal * weight
                                        accumG[globalIndex] += gVal * weight
                                        accumB[globalIndex] += bVal * weight
                                        accumWeight[globalIndex] += weight
                                    }
                                }
                            } finally {
                                results.close()
                            }
                        } finally {
                            inputTensor.close()
                        }
                    } finally {
                        tile.recycle()
                    }

                    tileIndex++
                    val progressRatio = 0.15f + (0.75f * tileIndex / totalTiles.toFloat())
                    onProgress(AIProgress(progressRatio, "Upscaling tile $tileIndex of $totalTiles"))
                    left += if (right == source.width) tileWidth else step
                }
                top += if (bottom == source.height) tileHeight else step
            }
            source.recycle()

            coroutineContext.ensureActive()
            onProgress(AIProgress(0.92f, "Finalizing upscaled image"))

            // Construct final bitmap from accumulated colors and weights
            val finalPixels = IntArray(totalPixels)
            for (i in 0 until totalPixels) {
                val w = accumWeight[i]
                val safeWeight = if (w > 0.0001f) w else 1f
                val r = (accumR[i] / safeWeight).toInt().coerceIn(0, 255)
                val g = (accumG[i] / safeWeight).toInt().coerceIn(0, 255)
                val b = (accumB[i] / safeWeight).toInt().coerceIn(0, 255)
                finalPixels[i] = Color.rgb(r, g, b)
            }

            val outputBitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
            outputBitmap.setPixels(finalPixels, 0, outputWidth, 0, 0, outputWidth, outputHeight)

            val file = File(appContext.cacheDir, "ai_esrgan_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                check(outputBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "Failed to encode Real-ESRGAN result" }
            }
            outputBitmap.recycle()

            val contentUri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file
            )

            onProgress(AIProgress(1f, "Complete"))
            AIResult.Success(contentUri.toString(), "Upscale", AIProviderType.ON_DEVICE)
        } catch (e: CancellationException) {
            throw e
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
                out[i] = (Color.red(c) / 255f).coerceIn(0f, 1f)
                out[plane + i] = (Color.green(c) / 255f).coerceIn(0f, 1f)
                out[2 * plane + i] = (Color.blue(c) / 255f).coerceIn(0f, 1f)
            }
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
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(appContext.contentResolver, uri)) { decoder, _, _ ->
            decoder.isMutableRequired = false
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(appContext.contentResolver, uri)
    }

    fun release() {
        session?.close()
        session = null
    }
}
