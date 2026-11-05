package com.example.ai.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SmartCutEngine(private val context: Context) {

    suspend fun analyze(
        uriString: String,
        sampleEveryMs: Long = 500L
    ): VideoAnalysisResult = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            val uri = Uri.parse(uriString)
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L)
                ?: 0L

            if (durationMs < MIN_ANALYZABLE_DURATION_MS) {
                return@withContext VideoAnalysisResult.Error("Video is too short for Smart Cut analysis")
            }

            val interval = sampleEveryMs.coerceIn(MIN_SAMPLE_INTERVAL_MS, MAX_SAMPLE_INTERVAL_MS)
            val samples = ArrayList<FrameSample>()
            var timeMs = 0L

            while (timeMs < durationMs) {
                val frame = retriever.getFrameAtTime(
                    timeMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )
                if (frame != null) {
                    try {
                        samples += analyzeFrame(timeMs, frame)
                    } finally {
                        frame.recycle()
                    }
                }
                timeMs += interval
            }

            if (samples.size < 3) {
                return@withContext VideoAnalysisResult.Error("Not enough readable frames for Smart Cut")
            }

            val suggestions = ArrayList<SuggestedCut>()

            detectBlackOrLowInformationRuns(samples, durationMs, suggestions)
            detectAbruptLowInformationTransitions(samples, durationMs, suggestions)

            val merged = mergeSuggestions(suggestions)
                .filter { it.endTimeMs - it.startTimeMs >= MIN_CUT_DURATION_MS }
                .sortedBy { it.startTimeMs }

            if (merged.isEmpty()) {
                VideoAnalysisResult.Error("No inactive segments were detected")
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
        val width = 64
        val height = 36
        val scaled = Bitmap.createScaledBitmap(frame, width, height, true)
        return try {
            val pixels = IntArray(width * height)
            scaled.getPixels(pixels, 0, width, 0, 0, width, height)

            var sum = 0f
            val histogram = FloatArray(HISTOGRAM_BINS)

            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
                sum += luma
                histogram[(luma * (HISTOGRAM_BINS - 1)).toInt().coerceIn(0, HISTOGRAM_BINS - 1)] += 1f
            }

            val mean = sum / pixels.size.coerceAtLeast(1)
            for (index in histogram.indices) {
                histogram[index] /= pixels.size.coerceAtLeast(1).toFloat()
            }

            FrameSample(timeMs, mean, histogram)
        } finally {
            scaled.recycle()
        }
    }

    private fun detectBlackOrLowInformationRuns(
        samples: List<FrameSample>,
        durationMs: Long,
        target: MutableList<SuggestedCut>
    ) {
        var runStart: Long? = null

        fun closeRun(endMs: Long) {
            val start = runStart ?: return
            if (endMs - start >= MIN_CUT_DURATION_MS) {
                target += SuggestedCut(
                    startTimeMs = start,
                    endTimeMs = endMs,
                    reason = "Low-information or black segment"
                )
            }
            runStart = null
        }

        for (sample in samples) {
            val inactive = sample.meanLuma <= LOW_LUMA_THRESHOLD ||
                dominantHistogramMass(sample.histogram) >= LOW_VARIATION_HISTOGRAM_MASS

            if (inactive) {
                if (runStart == null) runStart = sample.timeMs
            } else {
                closeRun(sample.timeMs)
            }
        }

        closeRun(durationMs)
    }

    private fun detectAbruptLowInformationTransitions(
        samples: List<FrameSample>,
        durationMs: Long,
        target: MutableList<SuggestedCut>
    ) {
        for (index in 1 until samples.size) {
            val previous = samples[index - 1]
            val current = samples[index]
            val histogramDelta = histogramDistance(previous.histogram, current.histogram)
            val lumaDelta = abs(previous.meanLuma - current.meanLuma)

            if (histogramDelta >= SCENE_CHANGE_THRESHOLD &&
                lumaDelta >= MIN_LUMA_DELTA &&
                current.meanLuma <= TRANSITION_LUMA_THRESHOLD
            ) {
                val start = max(0L, current.timeMs - TRANSITION_PADDING_MS)
                val end = min(durationMs, current.timeMs + TRANSITION_PADDING_MS)
                if (end - start >= MIN_CUT_DURATION_MS) {
                    target += SuggestedCut(
                        startTimeMs = start,
                        endTimeMs = end,
                        reason = "Low-information transition"
                    )
                }
            }
        }
    }

    private fun dominantHistogramMass(histogram: FloatArray): Float {
        val peak = histogram.maxOrNull() ?: 0f
        val first = histogram.take(2).sum()
        val last = histogram.takeLast(2).sum()
        return max(peak, max(first, last))
    }

    private fun histogramDistance(a: FloatArray, b: FloatArray): Float =
        a.indices.sumOf { index -> abs(a[index] - b[index]).toDouble() }.toFloat()

    private fun mergeSuggestions(input: List<SuggestedCut>): List<SuggestedCut> =
        input
            .sortedBy { it.startTimeMs }
            .fold(ArrayList()) { merged, item ->
                val previous = merged.lastOrNull()
                if (previous == null || item.startTimeMs > previous.endTimeMs + MERGE_GAP_MS) {
                    merged += item
                } else {
                    merged[merged.lastIndex] = previous.copy(
                        endTimeMs = max(previous.endTimeMs, item.endTimeMs)
                    )
                }
                merged
            }

    private data class FrameSample(
        val timeMs: Long,
        val meanLuma: Float,
        val histogram: FloatArray
    )

    companion object {
        private const val HISTOGRAM_BINS = 16
        private const val LOW_LUMA_THRESHOLD = 0.045f
        private const val TRANSITION_LUMA_THRESHOLD = 0.12f
        private const val MIN_LUMA_DELTA = 0.08f
        private const val LOW_VARIATION_HISTOGRAM_MASS = 0.72f
        private const val SCENE_CHANGE_THRESHOLD = 0.80f
        private const val TRANSITION_PADDING_MS = 300L
        private const val MIN_CUT_DURATION_MS = 700L
        private const val MERGE_GAP_MS = 450L
        private const val MIN_ANALYZABLE_DURATION_MS = 1200L
        private const val MIN_SAMPLE_INTERVAL_MS = 250L
        private const val MAX_SAMPLE_INTERVAL_MS = 1500L
    }
}
