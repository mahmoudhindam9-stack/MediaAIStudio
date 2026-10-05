package com.example.ai.video
import android.content.Context

class AIVideoEngine(private val context: Context) {
    val tracking = ObjectTrackingEngine(context)
    val smartReframe = SmartReframeEngine(context, tracking)
    val smartCut = SmartCutEngine(context)
    val autoCaption = AutoCaptionEngine(context)
    val enhancement = VideoEnhancementEngine(context)
}
