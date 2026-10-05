package com.example.ai.video

import android.content.Context
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

class SmartReframeEngine(
    private val context: Context,
    private val trackingEngine: ObjectTrackingEngine
) {
    suspend fun process(
        uriString: String,
        targetAspectRatio: Float = 9f / 16f,
        padding: Float = 0.14f
    ): VideoAnalysisResult {
        val trackResult = trackingEngine.analyze(uriString, intervalMs = 500L)
        if (trackResult !is VideoAnalysisResult.Tracking) return trackResult

        return withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(uriString))
                val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: return@withContext VideoAnalysisResult.Error("Unable to read video frame")
                val sourceWidth = frame.width.toFloat()
                val sourceHeight = frame.height.toFloat()
                frame.recycle()

                val output = trackResult.keyframes.map { keyframe ->
                    val centerX = keyframe.x + keyframe.width / 2f
                    val centerY = keyframe.y + keyframe.height / 2f
                    val cropWidthFromTarget = sourceHeight * targetAspectRatio
                    val cropHeightFromTarget = sourceWidth / targetAspectRatio

                    val baseWidth = if (sourceWidth / sourceHeight > targetAspectRatio) {
                        cropWidthFromTarget
                    } else {
                        sourceWidth
                    }
                    val baseHeight = if (sourceWidth / sourceHeight > targetAspectRatio) {
                        sourceHeight
                    } else {
                        cropHeightFromTarget
                    }

                    val desiredWidth = min(sourceWidth, max(keyframe.width * (1f + padding * 2f), baseWidth * 0.78f))
                    val desiredHeight = min(sourceHeight, max(keyframe.height * (1f + padding * 2f), baseHeight * 0.78f))
                    val aspectCorrected = fitAspect(
                        desiredWidth,
                        desiredHeight,
                        targetAspectRatio,
                        sourceWidth,
                        sourceHeight
                    )

                    ReframePoint(
                        timeMs = keyframe.timeMs,
                        centerX = centerX / sourceWidth,
                        centerY = centerY / sourceHeight,
                        width = aspectCorrected.first / sourceWidth,
                        height = aspectCorrected.second / sourceHeight,
                        confidence = keyframe.confidence
                    )
                }

                val smoothed = smooth(output, 0.78f)
                VideoAnalysisResult.SmartReframe(
                    smoothed.map {
                        TrackingKeyframe(
                            timeMs = it.timeMs,
                            x = (it.centerX - it.width / 2f).coerceIn(0f, 1f),
                            y = (it.centerY - it.height / 2f).coerceIn(0f, 1f),
                            width = it.width.coerceIn(0.05f, 1f),
                            height = it.height.coerceIn(0.05f, 1f),
                            confidence = it.confidence
                        )
                    }
                )
            } catch (e: Exception) {
                VideoAnalysisResult.Error(e.message ?: "Smart reframe failed")
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    private fun fitAspect(
        width: Float,
        height: Float,
        aspect: Float,
        sourceWidth: Float,
        sourceHeight: Float
    ): Pair<Float, Float> {
        var w = width.coerceIn(1f, sourceWidth)
        var h = height.coerceIn(1f, sourceHeight)
        if (w / h > aspect) {
            w = min(sourceWidth, h * aspect)
        } else {
            h = min(sourceHeight, w / aspect)
        }
        if (w < 1f || h < 1f) return sourceWidth to sourceHeight
        return w to h
    }

    private fun smooth(points: List<ReframePoint>, factor: Float): List<ReframePoint> {
        if (points.size < 2) return points
        val result = ArrayList<ReframePoint>(points.size)
        var x = points.first().centerX
        var y = points.first().centerY
        result += points.first()
        for (i in 1 until points.size) {
            val current = points[i]
            x = x * factor + current.centerX * (1f - factor)
            y = y * factor + current.centerY * (1f - factor)
            result += current.copy(centerX = x, centerY = y)
        }
        return result
    }

    private data class ReframePoint(
        val timeMs: Long,
        val centerX: Float,
        val centerY: Float,
        val width: Float,
        val height: Float,
        val confidence: Float
    )
}
