package com.example.ai.video

import java.util.UUID

data class TrackingKeyframe(
    val timeMs: Long,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val confidence: Float
)

data class SubtitleSegment(
    val id: String = UUID.randomUUID().toString(),
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String
)

data class SubtitleTrack(
    val id: String = UUID.randomUUID().toString(),
    val language: String,
    val segments: List<SubtitleSegment>
)

data class SuggestedCut(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val reason: String
)

sealed class VideoAnalysisResult {
    data class Tracking(val keyframes: List<TrackingKeyframe>) : VideoAnalysisResult()
    data class SmartCuts(val suggestedCuts: List<SuggestedCut>) : VideoAnalysisResult()
    data class AutoCaptions(val track: SubtitleTrack) : VideoAnalysisResult()
    data class SmartReframe(val cropPaths: List<TrackingKeyframe>) : VideoAnalysisResult()
    data class Error(val message: String) : VideoAnalysisResult()
}
