package com.example.ai.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

class ObjectTrackingEngine(private val context: Context) {
    private val objectDetector by lazy {
        ObjectDetection.getClient(
            ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .build()
        )
    }

    suspend fun analyze(
        uriString: String,
        intervalMs: Long = DEFAULT_INTERVAL_MS
    ): VideoAnalysisResult = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(uriString))
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L)
                ?: 0L

            if (durationMs <= 0L) {
                return@withContext VideoAnalysisResult.Error("Video duration could not be read")
            }

            val step = intervalMs.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)
            val history = LinkedHashMap<Int, MutableList<TrackingKeyframe>>()
            var lastSelectedId: Int? = null
            var lastCenterX = 0f
            var lastCenterY = 0f
            var sourceWidth = 0
            var sourceHeight = 0

            var timeMs = 0L
            while (timeMs < durationMs) {
                val frame = retriever.getFrameAtTime(
                    timeMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                ) ?: run {
                    timeMs += step
                    continue
                }

                try {
                    sourceWidth = frame.width
                    sourceHeight = frame.height
                    val image = InputImage.fromBitmap(frame, 0)
                    val detections = objectDetector.process(image).await()

                    val candidate = chooseTarget(
                        detections = detections,
                        previousTrackingId = lastSelectedId,
                        previousCenterX = lastCenterX,
                        previousCenterY = lastCenterY,
                        frameWidth = sourceWidth,
                        frameHeight = sourceHeight
                    )

                    if (candidate != null && candidate.trackingId != null) {
                        val box = candidate.boundingBox
                        val confidence = candidate.labels.maxOfOrNull { it.confidence } ?: 0f
                        val centerX = box.centerX().toFloat()
                        val centerY = box.centerY().toFloat()

                        val keyframe = TrackingKeyframe(
                            timeMs = timeMs,
                            x = box.left.toFloat(),
                            y = box.top.toFloat(),
                            width = box.width().toFloat(),
                            height = box.height().toFloat(),
                            confidence = confidence
                        )

                        history.getOrPut(candidate.trackingId!!) { ArrayList() }.add(keyframe)
                        lastSelectedId = candidate.trackingId
                        lastCenterX = centerX
                        lastCenterY = centerY
                    }
                } finally {
                    frame.recycle()
                }

                timeMs += step
            }

            val bestTrack = history.values
                .filter { it.size >= MIN_TRACK_POINTS }
                .maxByOrNull { track ->
                    val confidence = track.map { it.confidence }.average()
                    track.size * 1000 + (confidence * 100).toInt()
                }

            if (bestTrack.isNullOrEmpty()) {
                VideoAnalysisResult.Error("No consistently tracked object found")
            } else {
                VideoAnalysisResult.Tracking(smoothTrack(bestTrack, sourceWidth, sourceHeight))
            }
        } catch (e: Exception) {
            VideoAnalysisResult.Error(e.message ?: "Object tracking failed")
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun chooseTarget(
        detections: List<com.google.mlkit.vision.objects.DetectedObject>,
        previousTrackingId: Int?,
        previousCenterX: Float,
        previousCenterY: Float,
        frameWidth: Int,
        frameHeight: Int
    ): com.google.mlkit.vision.objects.DetectedObject? {
        if (detections.isEmpty()) return null

        val tracked = previousTrackingId?.let { id ->
            detections.firstOrNull { it.trackingId == id }
        }
        if (tracked != null) return tracked

        val diagonal = max(1f, kotlin.math.sqrt(
            frameWidth.toFloat() * frameWidth + frameHeight.toFloat() * frameHeight
        ))

        return detections.maxByOrNull { objectResult ->
            val box = objectResult.boundingBox
            val confidence = objectResult.labels.maxOfOrNull { it.confidence } ?: 0f
            val areaRatio = (box.width().toFloat() * box.height().toFloat()) /
                max(1f, frameWidth.toFloat() * frameHeight.toFloat())
            val centerDistance = if (previousTrackingId == null) {
                0f
            } else {
                val dx = box.centerX() - previousCenterX
                val dy = box.centerY() - previousCenterY
                kotlin.math.sqrt(dx * dx + dy * dy) / diagonal
            }

            confidence * 0.55f + areaRatio * 0.30f + (1f - centerDistance.coerceIn(0f, 1f)) * 0.15f
        }
    }

    private fun smoothTrack(
        track: List<TrackingKeyframe>,
        sourceWidth: Int,
        sourceHeight: Int
    ): List<TrackingKeyframe> {
        if (track.size < 3) return track.sortedBy { it.timeMs }

        val sorted = track.sortedBy { it.timeMs }
        val result = ArrayList<TrackingKeyframe>(sorted.size)
        var x = sorted.first().x
        var y = sorted.first().y
        var width = sorted.first().width
        var height = sorted.first().height
        result += sorted.first()

        for (index in 1 until sorted.size) {
            val current = sorted[index]
            val alpha = if (current.confidence >= 0.70f) 0.35f else 0.20f
            x += (current.x - x) * alpha
            y += (current.y - y) * alpha
            width += (current.width - width) * alpha
            height += (current.height - height) * alpha

            result += current.copy(
                x = x.coerceIn(0f, sourceWidth.toFloat()),
                y = y.coerceIn(0f, sourceHeight.toFloat()),
                width = width.coerceIn(1f, sourceWidth.toFloat()),
                height = height.coerceIn(1f, sourceHeight.toFloat())
            )
        }
        return result
    }

    companion object {
        private const val DEFAULT_INTERVAL_MS = 500L
        private const val MIN_INTERVAL_MS = 250L
        private const val MAX_INTERVAL_MS = 1500L
        private const val MIN_TRACK_POINTS = 2
    }
}
