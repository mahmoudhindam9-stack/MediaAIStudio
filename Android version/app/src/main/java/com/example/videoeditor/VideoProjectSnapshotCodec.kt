package com.example.videoeditor

import com.example.ai.video.SuggestedCut
import com.example.ai.video.SubtitleSegment
import com.example.ai.video.SubtitleTrack
import com.example.ai.video.TrackingKeyframe
import com.example.ai.video.VideoEnhancementSuggestion
import com.example.audio.AudioTrackType
import org.json.JSONArray
import org.json.JSONObject

object VideoProjectSnapshotCodec {
    private const val VERSION = 1

    fun encode(state: VideoEditorState): String {
        val root = JSONObject().apply {
            put("version", VERSION)
            put("playheadMs", state.playheadMs)
            put("durationMs", state.durationMs)
            put("selectedItemId", state.selectedItemId ?: JSONObject.NULL)
            put("videoClips", JSONArray().apply {
                state.videoClips.forEach { put(encodeVideoClip(it)) }
            })
            put("audioTracks", JSONArray().apply {
                state.audioTracks.forEach { put(encodeAudioClip(it)) }
            })
            put("aiSubtitleTrack", state.aiSubtitleTrack?.let(::encodeSubtitleTrack) ?: JSONObject.NULL)
            put("aiTrackingData", encodeTracking(state.aiTrackingData))
            put("aiSuggestedCuts", encodeCuts(state.aiSuggestedCuts))
            put("aiReframeKeyframes", encodeReframe(state.aiReframeKeyframes))
            put(
                "aiEnhancementSuggestion",
                state.aiEnhancementSuggestion?.let(::encodeEnhancement) ?: JSONObject.NULL
            )
        }
        return root.toString()
    }

    fun decode(raw: String): VideoEditorState? {
        return runCatching {
            val root = JSONObject(raw)
            val version = root.optInt("version", 1)
            require(version in 1..VERSION) { "Unsupported video project snapshot version: $version" }

            val videoClips = decodeArray(root.optJSONArray("videoClips")) { decodeVideoClip(it) }
            val audioTracks = decodeArray(root.optJSONArray("audioTracks")) { decodeAudioClip(it) }

            VideoEditorState(
                videoClips = videoClips,
                audioTracks = audioTracks,
                playheadMs = root.optLong("playheadMs", 0L),
                durationMs = root.optLong("durationMs", 0L),
                isPlaying = false,
                selectedItemId = root.optStringOrNull("selectedItemId"),
                aiSubtitleTrack = root.optJSONObjectOrNull("aiSubtitleTrack")?.let(::decodeSubtitleTrack),
                aiTrackingData = decodeTracking(root.optJSONArray("aiTrackingData")),
                aiSuggestedCuts = decodeCuts(root.optJSONArray("aiSuggestedCuts")),
                aiReframeKeyframes = decodeReframe(root.optJSONArray("aiReframeKeyframes")),
                aiEnhancementSuggestion =
                    root.optJSONObjectOrNull("aiEnhancementSuggestion")?.let(::decodeEnhancement),
                isExporting = false
            )
        }.getOrNull()
    }

    private fun encodeVideoClip(clip: VideoClip) = JSONObject().apply {
        put("id", clip.id)
        put("uri", clip.uri)
        put("startTimeMs", clip.startTimeMs)
        put("durationMs", clip.durationMs)
        put("startTrimMs", clip.startTrimMs)
        put("volume", clip.volume)
        put("isMuted", clip.isMuted)
        put("isDuckingEnabled", clip.isDuckingEnabled)
        put("originalDurationMs", clip.originalDurationMs)
        put("rotation", clip.rotation)
        put("isImage", clip.isImage)
    }

    private fun decodeVideoClip(json: JSONObject): VideoClip {
        return VideoClip(
            id = json.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
            uri = json.getString("uri"),
            startTimeMs = json.optLong("startTimeMs", 0L),
            durationMs = json.optLong("durationMs", 0L),
            startTrimMs = json.optLong("startTrimMs", 0L),
            volume = json.optDouble("volume", 1.0).toFloat().coerceIn(0f, 1f),
            isMuted = json.optBoolean("isMuted", false),
            isDuckingEnabled = json.optBoolean("isDuckingEnabled", false),
            originalDurationMs = json.optLong("originalDurationMs", 0L),
            rotation = json.optDouble("rotation", 0.0).toFloat(),
            isImage = json.optBoolean("isImage", false)
        )
    }

    private fun encodeAudioClip(clip: AudioClip) = JSONObject().apply {
        put("id", clip.id)
        put("type", clip.type.name)
        put("uri", clip.uri)
        put("startTimeMs", clip.startTimeMs)
        put("durationMs", clip.durationMs)
        put("startTrimMs", clip.startTrimMs)
        put("volume", clip.volume)
        put("isMuted", clip.isMuted)
        put("isDuckingEnabled", clip.isDuckingEnabled)
        put("originalDurationMs", clip.originalDurationMs)
        put("fadeInDurationMs", clip.fadeInDurationMs)
        put("fadeOutDurationMs", clip.fadeOutDurationMs)
    }

    private fun decodeAudioClip(json: JSONObject): AudioClip {
        val type = runCatching {
            AudioTrackType.valueOf(json.optString("type", AudioTrackType.MUSIC.name))
        }.getOrDefault(AudioTrackType.MUSIC)
        return AudioClip(
            id = json.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
            type = type,
            uri = json.getString("uri"),
            startTimeMs = json.optLong("startTimeMs", 0L),
            durationMs = json.optLong("durationMs", 0L),
            startTrimMs = json.optLong("startTrimMs", 0L),
            volume = json.optDouble("volume", 1.0).toFloat().coerceIn(0f, 1f),
            isMuted = json.optBoolean("isMuted", false),
            isDuckingEnabled = json.optBoolean("isDuckingEnabled", false),
            originalDurationMs = json.optLong("originalDurationMs", 0L),
            fadeInDurationMs = json.optLong("fadeInDurationMs", 0L),
            fadeOutDurationMs = json.optLong("fadeOutDurationMs", 0L)
        )
    }

    private fun encodeSubtitleTrack(track: SubtitleTrack) = JSONObject().apply {
        put("id", track.id)
        put("language", track.language)
        put("segments", JSONArray().apply {
            track.segments.forEach {
                put(
                    JSONObject().apply {
                        put("id", it.id)
                        put("startTimeMs", it.startTimeMs)
                        put("endTimeMs", it.endTimeMs)
                        put("text", it.text)
                    }
                )
            }
        })
    }

    private fun decodeSubtitleTrack(json: JSONObject): SubtitleTrack {
        val segments = decodeArray(json.optJSONArray("segments")) {
            SubtitleSegment(
                id = it.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                startTimeMs = it.optLong("startTimeMs", 0L),
                endTimeMs = it.optLong("endTimeMs", 0L),
                text = it.optString("text")
            )
        }
        return SubtitleTrack(
            id = json.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
            language = json.optString("language", "auto"),
            segments = segments
        )
    }

    private fun encodeTracking(items: List<TrackingKeyframe>?): JSONArray? =
        items?.let {
            JSONArray().apply {
                it.forEach { item ->
                    put(
                        JSONObject().apply {
                            put("timeMs", item.timeMs)
                            put("x", item.x)
                            put("y", item.y)
                            put("width", item.width)
                            put("height", item.height)
                            put("confidence", item.confidence)
                        }
                    )
                }
            }
        }

    private fun decodeTracking(array: JSONArray?): List<TrackingKeyframe>? =
        array?.let {
            decodeArray(it) { json ->
                TrackingKeyframe(
                    timeMs = json.optLong("timeMs", 0L),
                    x = json.optDouble("x", 0.0).toFloat(),
                    y = json.optDouble("y", 0.0).toFloat(),
                    width = json.optDouble("width", 0.0).toFloat(),
                    height = json.optDouble("height", 0.0).toFloat(),
                    confidence = json.optDouble("confidence", 0.0).toFloat()
                )
            }
        }

    private fun encodeCuts(items: List<SuggestedCut>?): JSONArray? =
        items?.let {
            JSONArray().apply {
                it.forEach { item ->
                    put(
                        JSONObject().apply {
                            put("startTimeMs", item.startTimeMs)
                            put("endTimeMs", item.endTimeMs)
                            put("reason", item.reason)
                        }
                    )
                }
            }
        }

    private fun decodeCuts(array: JSONArray?): List<SuggestedCut>? =
        array?.let {
            decodeArray(it) { json ->
                SuggestedCut(
                    startTimeMs = json.optLong("startTimeMs", 0L),
                    endTimeMs = json.optLong("endTimeMs", 0L),
                    reason = json.optString("reason")
                )
            }
        }

    private fun encodeReframe(items: List<ReframeKeyframe>?): JSONArray? =
        items?.let {
            JSONArray().apply {
                it.forEach { item ->
                    put(
                        JSONObject().apply {
                            put("timeMs", item.timeMs)
                            put("centerX", item.centerX)
                            put("centerY", item.centerY)
                            put("width", item.width)
                            put("height", item.height)
                            put("confidence", item.confidence)
                        }
                    )
                }
            }
        }

    private fun decodeReframe(array: JSONArray?): List<ReframeKeyframe>? =
        array?.let {
            decodeArray(it) { json ->
                ReframeKeyframe(
                    timeMs = json.optLong("timeMs", 0L),
                    centerX = json.optDouble("centerX", 0.0).toFloat(),
                    centerY = json.optDouble("centerY", 0.0).toFloat(),
                    width = json.optDouble("width", 1.0).toFloat(),
                    height = json.optDouble("height", 1.0).toFloat(),
                    confidence = json.optDouble("confidence", 0.0).toFloat()
                )
            }
        }

    private fun encodeEnhancement(item: VideoEnhancementSuggestion) = JSONObject().apply {
        put("brightness", item.brightness)
        put("contrast", item.contrast)
        put("saturation", item.saturation)
        put("sharpness", item.sharpness)
        put("confidence", item.confidence)
    }

    private fun decodeEnhancement(json: JSONObject) = VideoEnhancementSuggestion(
        brightness = json.optDouble("brightness", 0.0).toFloat(),
        contrast = json.optDouble("contrast", 0.0).toFloat(),
        saturation = json.optDouble("saturation", 0.0).toFloat(),
        sharpness = json.optDouble("sharpness", 0.0).toFloat(),
        confidence = json.optDouble("confidence", 0.0).toFloat()
    )

    private inline fun <T> decodeArray(
        array: JSONArray?,
        decodeItem: (JSONObject) -> T
    ): List<T> {
        if (array == null) return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                add(decodeItem(array.getJSONObject(index)))
            }
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optJSONObjectOrNull(key: String): JSONObject? {
        if (!has(key) || isNull(key)) return null
        return optJSONObject(key)
    }
}
