package com.example.ai.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt

class SmartCutEngine(private val context: Context) {
    suspend fun analyze(uriString: String, sampleEveryMs: Long = 500L): VideoAnalysisResult =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                val uri = Uri.parse(uriString)
                retriever.setDataSource(context, uri)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.coerceAtLeast(0L)
                    ?: 0L

                if (durationMs < 1000L) {
                    return@withContext VideoAnalysisResult.Error("Video is too short for Smart Cut")
                }

                val samples = mutableListOf<FrameSample>()
                for (timeMs in 0L until durationMs step sampleEveryMs.coerceAtLeast(250L)) {
                    val frame = retriever.getFrameAtTime(
                        timeMs * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST
                    ) ?: continue
                    try {
                        samples += analyzeFrame(timeMs, frame)
                    } finally {
                        frame.recycle()
                    }
                }

                if (samples.size < 3) {
                    return@withContext VideoAnalysisResult.Error("Not enough readable frames for Smart Cut")
                }

                val suggestions = mutableListOf<SuggestedCut>()
                var darkRunStart: Long? = null

                samples.forEach { sample ->
                    if (sample.meanLuma < DARK_FRAME_THRESHOLD) {
                        if (darkRunStart == null) darkRunStart = sample.timeMs
                    } else if (darkRunStart != null) {
                        addInactiveSegment(suggestions, darkRunStart!!, sample.timeMs, "Low-information dark segment")
                        darkRunStart = null
                    }
                }
                darkRunStart?.let { addInactiveSegment(suggestions, it, durationMs, "Low-information dark segment") }

                val first = samples.first()
                if (first.meanLuma < BLACK_FRAME_THRESHOLD) {
                    val end = samples.firstOrNull { it.meanLuma >= BLACK_FRAME_THRESHOLD }?.timeMs ?: 0L
                    addInactiveSegment(suggestions, 0L, end, "Trim black opening")
                }

                val last = samples.last()
                if (last.meanLuma < BLACK_FRAME_THRESHOLD) {
                    addInactiveSegment(suggestions, last.timeMs, durationMs, "Trim black ending")
                }

                for (i in 1 until samples.size) {
                    val previous = samples[i - 1]
                    val current = samples[i]
                    val change = histogramDistance(previous.histogram, current.histogram)
                    if (change >= SCENE_CHANGE_THRESHOLD) {
                        val before = maxOf(0L, current.timeMs - SCENE_PADDING_MS)
                        val after = minOf(durationMs, current.timeMs + SCENE_PADDING_MS)
                        if (after - before >= 500L && current.meanLuma < DARK_FRAME_THRESHOLD + 0.08f) {
                            suggestions += SuggestedCut(
                                before,
                                after,
                                "Low-information scene transition"
                            )
                        }
                    }
                }

                val merged = mergeSuggestions(suggestions, durationMs)
                if (merged.isEmpty()) {
                    VideoAnalysisResult.Error("No inactive or low-information segments found")
                } else {
                    VideoAnalysisResult.SmartCuts(merged)
                }
            } catch (e: Exception) {
                VideoAnalysisResult.Error(e.message ?: "Smart Cut analysis failed")
            } finally {
                runCatching { retriever.release() }
            }
        }

    private fun analyzeFrame(timeMs: Long, frame: Bitmap): FrameSample {
        val small = Bitmap.createScaledBitmap(frame, 64, 36, true)
        val pixels = IntArray(small.width * small.height)
        small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)

        var sum = 0f
        val histogram = FloatArray(16)
        pixels.forEach { pixel ->
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
            sum += luma
            val bin = (luma * 15f).toInt().coerceIn(0, 15)
            histogram[bin] += 1f
        }
        small.recycle()

        val total = pixels.size.toFloat().coerceAtLeast(1f)
        for (i in histogram.indices) histogram[i] /= total

        val mean = sum / total
        val variance = histogram.mapIndexed { index, weight ->
            val center = index / 15f
            weight * (center - mean) * (center - mean)
        }.sum()

        return FrameSample(timeMs, mean, histogram, sqrt(variance))
    }

    private fun histogramDistance(a: FloatArray, b: FloatArray): Float =
        a.indices.sumOf { abs(a[it] - b[it]).toDouble() }.toFloat()

    private fun addInactiveSegment(
        target: MutableList<SuggestedCut>,
        startMs: Long,
        endMs: Long,
        reason: String
    ) {
        val start = startMs.coerceAtLeast(0L)
        val end = endMs.coerceAtLeast(start)
        if (end - start >= MIN_CUT_DURATION_MS) {
            target += SuggestedCut(start, end, reason)
        }
    }

    private fun mergeSuggestions(
        input: List<SuggestedCut>,
        durationMs: Long
    ): List<SuggestedCut> =
        input
            .map {
                it.copy(
                    startTimeMs = it.startTimeMs.coerceIn(0L, durationMs),
                    endTimeMs = it.endTimeMs.coerceIn(0L, durationMs)
                )
            }
            .filter { it.endTimeMs > it.startTimeMs }
            .sortedBy { it.startTimeMs }
            .fold(mutableListOf()) { acc, cut ->
                val previous = acc.lastOrNull()
                if (previous == null || cut.startTimeMs > previous.endTimeMs + MERGE_GAP_MS) {
                    acc += cut
                } else {
                    acc[acc.lastIndex] = previous.copy(endTimeMs = maxOf(previous.endTimeMs, cut.endTimeMs))
                }
                acc
            }

    private data class FrameSample(
        val timeMs: Long,
        val meanLuma: Float,
        val histogram: FloatArray,
        val textureVariance: Float
    )

    companion object {
        private const val DARK_FRAME_THRESHOLD = 0.10f
        private const val BLACK_FRAME_THRESHOLD = 0.035f
        private const val SCENE_CHANGE_THRESHOLD = 0.85f
        private const val SCENE_PADDING_MS = 250L
        private const val MIN_CUT_DURATION_MS = 700L
        private const val MERGE_GAP_MS = 500L
    }
}
