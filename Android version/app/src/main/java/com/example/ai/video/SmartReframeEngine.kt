package com.example.ai.video

import android.content.Context
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
        paddingRatio: Float = 0.18f
    ): VideoAnalysisResult {
        require(targetAspectRatio > 0f) { "Target aspect ratio must be positive" }

        val tracking = trackingEngine.analyze(uriString, intervalMs = 500L)
        if (tracking !is VideoAnalysisResult.Tracking) return tracking

        val retriever = MediaMetadataRetriever()
        return withContext(Dispatchers.IO) {
            try {
                retriever.setDataSource(context, Uri.parse(uriString))
                val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: return@withContext VideoAnalysisResult.Error("Unable to read source video dimensions")

                val sourceWidth = frame.width.toFloat()
                val sourceHeight = frame.height.toFloat()
                frame.recycle()

                if (sourceWidth <= 0f || sourceHeight <= 0f) {
                    return@withContext VideoAnalysisResult.Error("Invalid source video dimensions")
                }

                val cropWidth = min(sourceWidth, sourceHeight * targetAspectRatio)
                val cropHeight = min(sourceHeight, sourceWidth / targetAspectRatio)

                val points = tracking.keyframes
                    .sortedBy { it.timeMs }
                    .map { keyframe ->
                        val objectCenterX = keyframe.x + keyframe.width / 2f
                        val objectCenterY = keyframe.y + keyframe.height / 2f

                        val desiredHalfWidth = max(
                            cropWidth / 2f,
                            keyframe.width * (0.5f + paddingRatio)
                        )
                        val desiredHalfHeight = max(
                            cropHeight / 2f,
                            keyframe.height * (0.5f + paddingRatio)
                        )

                        val centerX = clampCenter(
                            objectCenterX,
                            desiredHalfWidth,
                            sourceWidth
                        )
                        val centerY = clampCenter(
                            objectCenterY,
                            desiredHalfHeight,
                            sourceHeight
                        )

                        TrackingKeyframe(
                            timeMs = keyframe.timeMs,
                            x = ((centerX - cropWidth / 2f) / sourceWidth).coerceIn(0f, 1f),
                            y = ((centerY - cropHeight / 2f) / sourceHeight).coerceIn(0f, 1f),
                            width = (cropWidth / sourceWidth).coerceIn(0f, 1f),
                            height = (cropHeight / sourceHeight).coerceIn(0f, 1f),
                            confidence = keyframe.confidence
                        )
                    }

                VideoAnalysisResult.SmartReframe(
                    smooth(points)
                )
            } catch (e: Exception) {
                VideoAnalysisResult.Error(e.message ?: "Smart Reframe failed")
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    private fun clampCenter(center: Float, halfSize: Float, totalSize: Float): Float {
        val minCenter = min(halfSize, totalSize / 2f)
        val maxCenter = max(minCenter, totalSize - halfSize)
        return center.coerceIn(minCenter, maxCenter)
    }

    private fun smooth(
        points: List<TrackingKeyframe>,
        responseFactor: Float = 0.70f
    ): List<TrackingKeyframe> {
        if (points.size < 2) return points

        var centerX = points.first().x + points.first().width / 2f
        var centerY = points.first().y + points.first().height / 2f

        return points.mapIndexed { index, point ->
            if (index == 0) return@mapIndexed point

            val nextCenterX = point.x + point.width / 2f
            val nextCenterY = point.y + point.height / 2f
            centerX = centerX * responseFactor + nextCenterX * (1f - responseFactor)
            centerY = centerY * responseFactor + nextCenterY * (1f - responseFactor)

            point.copy(
                x = (centerX - point.width / 2f).coerceIn(0f, 1f - point.width),
                y = (centerY - point.height / 2f).coerceIn(0f, 1f - point.height)
            )
        }
    }
}
