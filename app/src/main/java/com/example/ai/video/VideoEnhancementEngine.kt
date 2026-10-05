package com.example.ai.video

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class VideoEnhancementSuggestion(
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val confidence: Float
)

class VideoEnhancementEngine(private val context: Context) {
    suspend fun enhance(uriString: String): VideoAnalysisResult =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(uriString))
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.coerceAtLeast(0L)
                    ?: 0L
                if (durationMs <= 0L) return@withContext VideoAnalysisResult.Error("Unreadable video")

                val sampleTimes = (0L..durationMs step 1000L).take(MAX_SAMPLES)
                var lumaSum = 0f
                var contrastSum = 0f
                var count = 0

                sampleTimes.forEach { timeMs ->
                    val frame = retriever.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                        ?: return@forEach
                    try {
                        val scaled = android.graphics.Bitmap.createScaledBitmap(frame, 64, 36, true)
                        val pixels = IntArray(scaled.width * scaled.height)
                        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)

                        var mean = 0f
                        pixels.forEach { p ->
                            val r = (p shr 16) and 0xff
                            val g = (p shr 8) and 0xff
                            val b = p and 0xff
                            mean += (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
                        }
                        mean /= pixels.size.coerceAtLeast(1)

                        var variance = 0f
                        pixels.forEach { p ->
                            val r = (p shr 16) and 0xff
                            val g = (p shr 8) and 0xff
                            val b = p and 0xff
                            val l = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
                            variance += (l - mean) * (l - mean)
                        }
                        variance /= pixels.size.coerceAtLeast(1)

                        lumaSum += mean
                        contrastSum += kotlin.math.sqrt(variance)
                        count++
                        scaled.recycle()
                    } finally {
                        frame.recycle()
                    }
                }

                if (count == 0) return@withContext VideoAnalysisResult.Error("No readable frames for enhancement analysis")

                val meanLuma = lumaSum / count
                val contrast = contrastSum / count
                val brightness = ((0.50f - meanLuma) * 0.75f).coerceIn(-0.28f, 0.28f)
                val contrastAdjust = ((0.20f - contrast) * 1.5f).coerceIn(-0.15f, 0.28f)
                val saturation = if (meanLuma < 0.42f) 0.10f else 0.04f
                val confidence = (0.55f + kotlin.math.abs(brightness) + kotlin.math.abs(contrastAdjust))
                    .coerceIn(0.55f, 0.97f)

                VideoAnalysisResult.Enhancement(
                    VideoEnhancementSuggestion(
                        brightness = brightness,
                        contrast = contrastAdjust,
                        saturation = saturation,
                        confidence = confidence
                    )
                )
            } catch (e: Exception) {
                VideoAnalysisResult.Error(e.message ?: "Video enhancement analysis failed")
            } finally {
                runCatching { retriever.release() }
            }
        }

    companion object {
        private const val MAX_SAMPLES = 60
    }
}
