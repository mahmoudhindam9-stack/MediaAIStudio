package com.example.ai.video

import android.content.Context
import android.net.Uri
import com.example.ai.generative.CloudGenerativeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class AutoCaptionEngine(context: Context) {
    private val cloudClient = CloudGenerativeClient(context.applicationContext)

    suspend fun generate(
        uriString: String,
        language: String = "auto"
    ): VideoAnalysisResult = withContext(Dispatchers.IO) {
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

            val segments = parseSegments(response)
            if (segments.isEmpty()) {
                return@withContext VideoAnalysisResult.Error(
                    response.optString("error", "Caption service returned no subtitle segments.")
                )
            }

            VideoAnalysisResult.AutoCaptions(
                SubtitleTrack(
                    language = response.optString("language", language),
                    segments = segments
                )
            )
        } catch (e: Exception) {
            VideoAnalysisResult.Error(e.message ?: "Auto caption generation failed")
        }
    }

    private fun parseSegments(response: JSONObject): List<SubtitleSegment> {
        val segmentsArray = response.optJSONArray("segments")
            ?: response.optJSONObject("result")?.optJSONArray("segments")
            ?: JSONArray()

        val parsed = ArrayList<SubtitleSegment>(segmentsArray.length())
        for (index in 0 until segmentsArray.length()) {
            val segment = segmentsArray.optJSONObject(index) ?: continue
            val startMs = readTimeMs(segment, "startMs", "start")
            val endMs = readTimeMs(segment, "endMs", "end")
            val text = segment.optString("text", "").trim()
            if (text.isNotBlank() && endMs > startMs) {
                parsed += SubtitleSegment(
                    startTimeMs = startMs,
                    endTimeMs = endMs,
                    text = text
                )
            }
        }
        return parsed.sortedBy { it.startTimeMs }
    }

    private fun readTimeMs(segment: JSONObject, vararg keys: String): Long {
        for (key in keys) {
            if (!segment.has(key)) continue
            when (val value = segment.opt(key)) {
                is Number -> {
                    return if (key.endsWith("Ms")) value.toLong()
                    else (value.toDouble() * 1000.0).toLong()
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
