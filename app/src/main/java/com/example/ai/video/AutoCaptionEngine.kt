package com.example.ai.video

import android.content.Context
import android.net.Uri
import com.example.ai.generative.CloudGenerativeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

class AutoCaptionEngine(context: Context) {
    private val cloudClient = CloudGenerativeClient(context.applicationContext)

    suspend fun generate(uriString: String, language: String = "auto"): VideoAnalysisResult =
        withContext(Dispatchers.IO) {
            if (!cloudClient.isConfigured) {
                return@withContext VideoAnalysisResult.Error(
                    "Auto Captions require a configured Cloud AI backend."
                )
            }

            try {
                val response = cloudClient.transcribeVideo(Uri.parse(uriString), language)
                    ?: return@withContext VideoAnalysisResult.Error(
                        "Caption service is unavailable or rejected the video."
                    )

                val languageResult = response.optString("language", language)
                val segments = parseSegments(response)
                if (segments.isEmpty()) {
                    return@withContext VideoAnalysisResult.Error(
                        response.optString("error", "Caption service returned no segments.")
                    )
                }

                VideoAnalysisResult.AutoCaptions(
                    SubtitleTrack(
                        language = languageResult,
                        segments = segments
                    )
                )
            } catch (e: Exception) {
                VideoAnalysisResult.Error(e.message ?: "Auto caption generation failed")
            }
        }

    private fun parseSegments(response: org.json.JSONObject): List<SubtitleSegment> {
        val array = response.optJSONArray("segments")
            ?: response.optJSONObject("result")?.optJSONArray("segments")
            ?: JSONArray()

        val segments = mutableListOf<SubtitleSegment>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val start = readTimeMs(item, "startMs", "start")
            val end = readTimeMs(item, "endMs", "end")
            val text = item.optString("text", "").trim()
            if (end > start && text.isNotBlank()) {
                segments += SubtitleSegment(
                    startTimeMs = start,
                    endTimeMs = end,
                    text = text
                )
            }
        }
        return segments.sortedBy { it.startTimeMs }
    }

    private fun readTimeMs(item: org.json.JSONObject, vararg keys: String): Long {
        for (key in keys) {
            if (!item.has(key)) continue
            val value = item.opt(key)
            when (value) {
                is Number -> return if (key.endsWith("Ms")) {
                    value.toLong()
                } else {
                    (value.toDouble() * 1000.0).toLong()
                }
                is String -> {
                    value.toLongOrNull()?.let { return it }
                    value.toDoubleOrNull()?.let { return (it * 1000.0).toLong() }
                }
            }
        }
        return 0L
    }
}
