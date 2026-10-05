package com.example.ai.video

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.abs

class ObjectTrackingEngine(private val context: Context) {
    private val objectDetector by lazy {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
        ObjectDetection.getClient(options)
    }

    suspend fun analyze(uriString: String, intervalMs: Long = 500L): VideoAnalysisResult =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                val uri = Uri.parse(uriString)
                retriever.setDataSource(context, uri)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.coerceAtLeast(0L)
                    ?: 0L
                if (durationMs == 0L) return@withContext VideoAnalysisResult.Error("Video has no readable duration")

                val tracks = LinkedHashMap<Int, MutableList<TrackingKeyframe>>()
                var previousSourceId: Int? = null
                var lastCenterX = 0f
                var lastCenterY = 0f

                for (timeMs in 0L until durationMs step intervalMs.coerceAtLeast(250L)) {
                    val frame = retriever.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                        ?: continue
                    try {
                        val image = InputImage.fromBitmap(frame, 0)
                        val objects = objectDetector.process(image).await()

                        val selected = objects
                            .filter { objectResult ->
                                objectResult.boundingBox.width() > 0 &&
                                    objectResult.boundingBox.height() > 0
                            }
                            .maxWithOrNull(
                                compareBy(
                                    { it.trackingId != null },
                                    { it.labels.maxOfOrNull { label -> label.confidence } ?: 0f },
                                    { it.boundingBox.width() * it.boundingBox.height() }
                                )
                            )

                        val trackingId = selected?.trackingId
                        if (selected != null && trackingId != null) {
                            val bounds = selected.boundingBox
                            val confidence = selected.labels.maxOfOrNull { it.confidence } ?: 0f
                            val centerX = bounds.centerX().toFloat()
                            val centerY = bounds.centerY().toFloat()

                            if (previousSourceId != null && trackingId != previousSourceId) {
                                val jump = abs(centerX - lastCenterX) + abs(centerY - lastCenterY)
                                if (jump > frame.width * 0.9f) continue
                            }

                            tracks.getOrPut(trackingId) { mutableListOf() }.add(
                                TrackingKeyframe(
                                    timeMs = timeMs,
                                    x = bounds.left.toFloat(),
                                    y = bounds.top.toFloat(),
                                    width = bounds.width().toFloat(),
                                    height = bounds.height().toFloat(),
                                    confidence = confidence
                                )
                            )
                            previousSourceId = trackingId
                            lastCenterX = centerX
                            lastCenterY = centerY
                        }
                    } finally {
                        frame.recycle()
                    }
                }

                val bestTrack = tracks.values
                    .filter { it.size >= 2 }
                    .maxByOrNull { keyframes ->
                        keyframes.size * 1000L +
                            (keyframes.map { it.confidence }.average() * 100.0).toLong()
                    }

                if (bestTrack.isNullOrEmpty()) {
                    VideoAnalysisResult.Error("No consistently tracked object found")
                } else {
                    VideoAnalysisResult.Tracking(interpolateMissing(bestTrack))
                }
            } catch (e: Exception) {
                VideoAnalysisResult.Error(e.message ?: "Object tracking failed")
            } finally {
                runCatching { retriever.release() }
            }
        }

    private fun interpolateMissing(keyframes: List<TrackingKeyframe>): List<TrackingKeyframe> {
        if (keyframes.size < 2) return keyframes
        val sorted = keyframes.sortedBy { it.timeMs }
        val result = ArrayList<TrackingKeyframe>(sorted.size)
        result += sorted.first()

        for (i in 1 until sorted.lastIndex) {
            val prev = sorted[i - 1]
            val current = sorted[i]
            val next = sorted[i + 1]
            if (next.timeMs - prev.timeMs > 1500L) {
                val midpointTime = (prev.timeMs + next.timeMs) / 2L
                result += current
                result += TrackingKeyframe(
                    timeMs = midpointTime,
                    x = (prev.x + next.x) / 2f,
                    y = (prev.y + next.y) / 2f,
                    width = (prev.width + next.width) / 2f,
                    height = (prev.height + next.height) / 2f,
                    confidence = minOf(prev.confidence, next.confidence)
                )
            } else {
                result += current
            }
        }

        if (result.last().timeMs != sorted.last().timeMs) result += sorted.last()
        return result.sortedBy { it.timeMs }
    }
}
