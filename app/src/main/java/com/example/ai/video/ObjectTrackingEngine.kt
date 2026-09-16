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

class ObjectTrackingEngine(private val context: Context) {
    private val objectDetector by lazy {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .build()
        ObjectDetection.getClient(options)
    }

    suspend fun analyze(uriString: String, intervalMs: Long = 1000): VideoAnalysisResult {
        return withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(uriString)
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val durationMs = durationStr?.toLongOrNull() ?: 0L
                val keyframes = mutableListOf<TrackingKeyframe>()
                
                for (timeMs in 0 until durationMs step intervalMs) {
                    val frame = retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                    if (frame != null) {
                        val image = InputImage.fromBitmap(frame, 0)
                        val detectedObjects = objectDetector.process(image).await()
                        
                        // We track the most confident object for simplicity in this baseline
                        val bestObj = detectedObjects.maxByOrNull { it.labels.firstOrNull()?.confidence ?: 0f }
                        if (bestObj != null) {
                            val bounds = bestObj.boundingBox
                            val conf = bestObj.labels.firstOrNull()?.confidence ?: 1.0f
                            keyframes.add(
                                TrackingKeyframe(
                                    timeMs = timeMs,
                                    x = bounds.left.toFloat(),
                                    y = bounds.top.toFloat(),
                                    width = bounds.width().toFloat(),
                                    height = bounds.height().toFloat(),
                                    confidence = conf
                                )
                            )
                        }
                    }
                }
                retriever.release()
                if (keyframes.isEmpty()) VideoAnalysisResult.Error("No objects detected")
                else VideoAnalysisResult.Tracking(keyframes)
            } catch (e: Exception) {
                e.printStackTrace()
                VideoAnalysisResult.Error(e.message ?: "Tracking Failed")
            }
        }
    }
}
