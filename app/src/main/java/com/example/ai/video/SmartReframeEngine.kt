package com.example.ai.video
import android.content.Context

class SmartReframeEngine(private val context: Context, private val trackingEngine: ObjectTrackingEngine) {
    suspend fun process(uriString: String): VideoAnalysisResult {
        // Uses object tracking to determine crop path bounding boxes
        val trackResult = trackingEngine.analyze(uriString, intervalMs = 1000)
        return if (trackResult is VideoAnalysisResult.Tracking) {
            VideoAnalysisResult.SmartReframe(trackResult.keyframes)
        } else {
            trackResult
        }
    }
}
