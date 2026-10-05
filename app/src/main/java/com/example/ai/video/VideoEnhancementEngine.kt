package com.example.ai.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt

data class VideoEnhancementSuggestion(
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val sharpness: Float,
    val confidence: Float
)

class VideoEnhancementEngine(private val context: Context) {

    suspend fun enhance(
        uriString: String,
        sampleEveryMs: Long = 1_000L
    ): VideoAnalysisResult = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(uriString))

            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L)
                ?: 0L

            if (durationMs <= 0L) {
                return@withContext VideoAnalysisResult.Error("Video duration could not be read")
            }

            val step = sampleEveryMs.coerceIn(250L, 2_000L)
            val samples = ArrayList<FrameStats>()

            var timeMs = 0L
            while (timeMs < durationMs && samples.size < MAX_SAMPLES) {
                val frame = retriever.getFrameAtTime(
                    timeMs * 1_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )
                if (frame != null) {
                    try {
                        samples += analyzeFrame(frame)
                    } finally {
                        frame.recycle()
                    }
                }
                timeMs += step
            }

            if (samples.isEmpty()) {
                return@withContext VideoAnalysisResult.Error("No readable frames for enhancement analysis")
            }

            val meanLuma = samples.map { it.meanLuma }.average().toFloat()
            val meanContrast = samples.map { it.contrast }.average().toFloat()
            val meanSaturation = samples.map { it.saturation }.average().toFloat()
            val temporalVariance = samples.map { abs(it.meanLuma - meanLuma) }.average().toFloat()

            val brightness = ((TARGET_LUMA - meanLuma) * 0.8f)
                .coerceIn(-MAX_BRIGHTNESS, MAX_BRIGHTNESS)

            val contrast = ((TARGET_CONTRAST - meanContrast) * 1.25f)
                .coerceIn(-MAX_CONTRAST, MAX_CONTRAST)

            val saturation = ((TARGET_SATURATION - meanSaturation) * 0.65f)
                .coerceIn(-MAX_SATURATION, MAX_SATURATION)

            val sharpness = (
                (TARGET_CONTRAST - meanContrast).coerceAtLeast(0f) * 0.65f +
                    (1f - meanSaturation).coerceAtLeast(0f) * 0.15f
                ).coerceIn(0f, MAX_SHARPNESS)

            val confidence = (
                0.55f +
                    (1f - temporalVariance * 2f).coerceIn(0f, 0.25f) +
                    (if (meanLuma in 0.12f..0.85f) 0.10f else 0f) +
                    (if (meanContrast > 0.10f) 0.10f else 0f)
                ).coerceIn(0.55f, 0.95f)

            VideoAnalysisResult.Enhancement(
                VideoEnhancementSuggestion(
                    brightness = brightness,
                    contrast = contrast,
                    saturation = saturation,
                    sharpness = sharpness,
                    confidence = confidence
                )
            )
        } catch (e: Exception) {
            VideoAnalysisResult.Error(e.message ?: "Video enhancement analysis failed")
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun analyzeFrame(frame: Bitmap): FrameStats {
        val width = 80
        val height = 45
        val scaled = Bitmap.createScaledBitmap(frame, width, height, true)
        return try {
            val pixels = IntArray(width * height)
            scaled.getPixels(pixels, 0, width, 0, 0, width, height)

            var lumaSum = 0f
            var lumaSquaredSum = 0f
            var saturationSum = 0f

            for (pixel in pixels) {
                val r = ((pixel shr 16) and 0xFF) / 255f
                val g = ((pixel shr 8) and 0xFF) / 255f
                val b = (pixel and 0xFF) / 255f

                val maxChannel = maxOf(r, g, b)
                val minChannel = minOf(r, g, b)
                val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                val saturation = if (maxChannel <= 0f) {
                    0f
                } else {
                    (maxChannel - minChannel) / maxChannel
                }

                lumaSum += luma
                lumaSquaredSum += luma * luma
                saturationSum += saturation
            }

            val count = pixels.size.toFloat().coerceAtLeast(1f)
            val meanLuma = lumaSum / count
            val variance = (lumaSquaredSum / count - meanLuma * meanLuma).coerceAtLeast(0f)

            FrameStats(
                meanLuma = meanLuma,
                contrast = sqrt(variance),
                saturation = saturationSum / count
            )
        } finally {
            scaled.recycle()
        }
    }

    private data class FrameStats(
        val meanLuma: Float,
        val contrast: Float,
        val saturation: Float
    )

    companion object {
        private const val TARGET_LUMA = 0.50f
        private const val TARGET_CONTRAST = 0.20f
        private const val TARGET_SATURATION = 0.35f

        private const val MAX_BRIGHTNESS = 0.30f
        private const val MAX_CONTRAST = 0.30f
        private const val MAX_SATURATION = 0.25f
        private const val MAX_SHARPNESS = 0.40f
        private const val MAX_SAMPLES = 60
    }
}
