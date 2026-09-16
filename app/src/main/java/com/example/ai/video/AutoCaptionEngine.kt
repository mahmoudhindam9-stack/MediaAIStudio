package com.example.ai.video
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class AutoCaptionEngine(private val context: Context) {
    suspend fun generate(uriString: String): VideoAnalysisResult {
        return withContext(Dispatchers.IO) {
            // Android doesn't have local file SpeechRecognizer without specific intents or APIs.
            // Honestly report as Requires Cloud or Error for now, or we can provide an empty track
            // since we do not fake results.
            delay(1000)
            VideoAnalysisResult.Error("Pre-recorded Auto Captions requires Cloud Model (Not Available)")
        }
    }
}
