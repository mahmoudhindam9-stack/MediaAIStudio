package com.example.videoeditor

import android.app.Application
import android.content.ContentResolver
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.ai.generative.GenerativeEngine
import com.example.ai.generative.GenerativeRequest
import com.example.ai.generative.GenerativeType
import com.example.ai.generative.JobState
import com.example.ai.video.AIVideoEngine
import com.example.ai.video.SuggestedCut
import com.example.ai.video.VideoAnalysisResult
import com.example.audio.AudioTrackType
import com.example.audio.VoiceOverManagerImpl
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class VideoEditorViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(VideoEditorState())
    val state: StateFlow<VideoEditorState> = _state.asStateFlow()

    private val history = mutableListOf<VideoEditorState>()
    private var historyIndex = -1

    // The main player for previewing the timeline.
    val exoPlayer = ExoPlayer.Builder(application).build()
    private val aiEngine = AIVideoEngine(application)
    val generativeEngine = GenerativeEngine(application)

    private val _aiMessages = MutableSharedFlow<String>()
    val aiMessages = _aiMessages.asSharedFlow()

    private val voiceOverManager = VoiceOverManagerImpl(application)

    private data class MediaMetadataInfo(
        val durationMs: Long,
        val rotation: Float,
        val isImage: Boolean
    )

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) updateGlobalPositionFromPlayer()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateGlobalPositionFromPlayer()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            updateGlobalPositionFromPlayer()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                _state.update { it.copy(playheadMs = it.durationMs, isPlaying = false) }
            } else {
                updateGlobalPositionFromPlayer()
            }
        }
    }

    init {
        history.add(VideoEditorState())
        historyIndex = 0
        exoPlayer.addListener(playerListener)

        viewModelScope.launch {
            while (true) {
                delay(100)
                if (exoPlayer.isPlaying) updateGlobalPositionFromPlayer()
            }
        }
    }

    fun addMedia(uris: List<String>, contentResolver: ContentResolver) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val existingUris = _state.value.videoClips.mapTo(mutableSetOf()) { it.uri }
            val newClips = mutableListOf<VideoClip>()

            for (uriString in uris) {
                if (!existingUris.add(uriString)) continue
                val metadata = readMediaMetadata(uriString, contentResolver)
                val duration = if (metadata.isImage) 5_000L else metadata.durationMs.coerceAtLeast(10_000L)
                newClips += VideoClip(
                    uri = uriString,
                    originalDurationMs = duration,
                    durationMs = duration,
                    rotation = metadata.rotation,
                    isImage = metadata.isImage
                )
            }

            if (newClips.isEmpty()) return@launch

            val current = _state.value
            val (recalculatedClips, totalDuration) = recalculateTimeline(current.videoClips + newClips)
            updateState(
                current.copy(
                    videoClips = recalculatedClips,
                    durationMs = totalDuration,
                    selectedItemId = newClips.first().id,
                    playheadMs = current.playheadMs.coerceIn(0L, totalDuration),
                    isPlaying = false
                )
            )
            commitState()
            updatePlayerMedia()
        }
    }

    fun addAudio(uris: List<String>, contentResolver: ContentResolver) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val current = _state.value
            val newTracks = uris.distinct().mapNotNull { uriString ->
                val duration = readMediaMetadata(uriString, contentResolver).durationMs
                if (duration <= 0L) return@mapNotNull null
                AudioClip(
                    type = AudioTrackType.MUSIC,
                    uri = uriString,
                    startTimeMs = current.playheadMs.coerceAtLeast(0L),
                    durationMs = duration,
                    originalDurationMs = duration
                )
            }
            if (newTracks.isEmpty()) return@launch
            updateState(
                current.copy(
                    audioTracks = current.audioTracks + newTracks,
                    selectedItemId = newTracks.first().id
                )
            )
            commitState()
        }
    }

    private fun readMediaMetadata(uriString: String, contentResolver: ContentResolver): MediaMetadataInfo {
        val uri = Uri.parse(uriString)
        val mime = runCatching { contentResolver.getType(uri) }.getOrNull()
        val value = uriString.lowercase()
        val isImage = mime?.startsWith("image/") == true || listOf(
            ".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif", ".avif", ".bmp"
        ).any(value::endsWith)
        if (isImage) return MediaMetadataInfo(5_000L, 0f, true)

        var duration = 0L
        var rotation = 0f
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(getApplication(), uri)
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toFloatOrNull()
                ?: 0f
        } catch (_: Exception) {
            // Caller applies a safe fallback for video media.
        } finally {
            runCatching { retriever.release() }
        }
        return MediaMetadataInfo(duration, rotation, false)
    }

    private fun recalculateTimeline(clips: List<VideoClip>): Pair<List<VideoClip>, Long> {
        var currentTime = 0L
        val recalculated = clips.map { clip ->
            val normalizedDuration = clip.durationMs.coerceAtLeast(100L)
            val normalizedStart = if (clip.isImage) {
                0L
            } else {
                clip.startTrimMs.coerceIn(0L, clip.originalDurationMs)
            }
            val maximumDuration = if (clip.isImage) {
                clip.originalDurationMs.coerceAtLeast(100L)
            } else {
                (clip.originalDurationMs - normalizedStart).coerceAtLeast(100L)
            }
            val duration = normalizedDuration.coerceAtMost(maximumDuration)
            val newClip = clip.copy(
                startTimeMs = currentTime,
                startTrimMs = normalizedStart,
                durationMs = duration
            )
            currentTime += duration
            newClip
        }
        return recalculated to currentTime
    }

    fun validateTimelineState(): List<String> {
        val s = _state.value
        val errors = mutableListOf<String>()
        val ids = mutableSetOf<String>()
        var expectedStart = 0L

        for (clip in s.videoClips) {
            if (!ids.add(clip.id)) errors += "Duplicate timeline item ID: ${clip.id}"
            if (clip.startTimeMs < 0L) errors += "Negative timeline start on clip ${clip.id}"
            if (clip.startTrimMs < 0L) errors += "Negative startTrimMs on clip ${clip.id}"
            if (clip.durationMs < 0L) errors += "Negative durationMs on clip ${clip.id}"
            if (clip.originalDurationMs < 0L) errors += "Negative original duration on clip ${clip.id}"
            if (!clip.isImage && clip.startTrimMs + clip.durationMs > clip.originalDurationMs) {
                errors += "Invalid trim range on clip ${clip.id}"
            }
            if (clip.startTimeMs != expectedStart) {
                errors += "Invalid timeline ordering for clip ${clip.id}. Expected $expectedStart, got ${clip.startTimeMs}"
            }
            expectedStart += clip.durationMs
        }

        for (audio in s.audioTracks) {
            if (!ids.add(audio.id)) errors += "Duplicate timeline item ID: ${audio.id}"
            if (audio.startTimeMs < 0L) errors += "Negative start time on audio ${audio.id}"
            if (audio.startTrimMs < 0L) errors += "Negative startTrimMs on audio ${audio.id}"
            if (audio.durationMs < 0L) errors += "Negative durationMs on audio ${audio.id}"
            if (audio.originalDurationMs < 0L) errors += "Negative original duration on audio ${audio.id}"
            if (audio.startTrimMs + audio.durationMs > audio.originalDurationMs) {
                errors += "Invalid trim range on audio ${audio.id}"
            }
        }

        if (s.durationMs != expectedStart) errors += "Duration mismatch. Expected $expectedStart, got ${s.durationMs}"
        if (s.playheadMs < 0L || s.playheadMs > s.durationMs) errors += "Playhead outside duration"
        if (s.selectedItemId != null && ids.none { it == s.selectedItemId }) errors += "Invalid selectedItemId"
        return errors
    }

    fun loadInitialMedia(uriString: String) {
        if (_state.value.videoClips.isNotEmpty()) return
        viewModelScope.launch {
            val contentResolver = getApplication<Application>().contentResolver
            val metadata = readMediaMetadata(uriString, contentResolver)
            val duration = if (metadata.isImage) 5_000L else metadata.durationMs.coerceAtLeast(10_000L)
            val clip = VideoClip(
                uri = uriString,
                originalDurationMs = duration,
                durationMs = duration,
                rotation = metadata.rotation,
                isImage = metadata.isImage
            )
            val newState = _state.value.copy(
                videoClips = listOf(clip),
                durationMs = duration,
                selectedItemId = clip.id,
                playheadMs = 0L,
                isPlaying = false
            )
            updateState(newState)
            commitState()
            updatePlayerMedia()
        }
    }

    private fun updatePlayerMedia() {
        val s = _state.value
        exoPlayer.pause()
        exoPlayer.clearMediaItems()
        s.videoClips.forEach { clip ->
            val builder = MediaItem.Builder()
                .setUri(Uri.parse(clip.uri))
                .setMediaId(clip.id)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(clip.id).build())
            val mediaItem = if (clip.isImage) {
                builder.setImageDurationMs(clip.durationMs).build()
            } else {
                builder
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clip.startTrimMs)
                            .setEndPositionMs(clip.startTrimMs + clip.durationMs)
                            .build()
                    )
                    .build()
            }
            exoPlayer.addMediaItem(mediaItem)
        }
        if (s.videoClips.isEmpty()) {
            exoPlayer.stop()
            return
        }
        exoPlayer.prepare()
        seekPlayerToGlobal(s.playheadMs)
    }

    private fun updateGlobalPositionFromPlayer() {
        val s = _state.value
        val index = exoPlayer.currentMediaItemIndex
        if (index == C.INDEX_UNSET) return
        val clip = s.videoClips.getOrNull(index) ?: return
        val global = (clip.startTimeMs + exoPlayer.currentPosition).coerceIn(0L, s.durationMs)
        _state.update { it.copy(playheadMs = global) }
    }

    private fun seekPlayerToGlobal(positionMs: Long) {
        val s = _state.value
        if (s.videoClips.isEmpty()) return
        val position = positionMs.coerceIn(0L, s.durationMs)
        val targetIndex = when {
            position >= s.durationMs -> s.videoClips.lastIndex
            else -> s.videoClips.indexOfFirst { position >= it.startTimeMs && position < it.endTimeMs }
        }.let { if (it < 0) 0 else it }
        val clip = s.videoClips[targetIndex]
        val offset = if (position >= s.durationMs) clip.durationMs else (position - clip.startTimeMs).coerceIn(0L, clip.durationMs)
        exoPlayer.seekTo(targetIndex, offset)
    }

    fun updateState(newState: VideoEditorState) {
        _state.value = newState
    }

    fun commitState() {
        if (historyIndex < history.size - 1) {
            history.subList(historyIndex + 1, history.size).clear()
        }
        history += _state.value.copy()
        historyIndex++
        autosave()
    }

    private fun autosave() {
        // Full project persistence is intentionally handled in the project-persistence phase.
    }

    fun undo() {
        if (historyIndex > 0) {
            historyIndex--
            _state.value = history[historyIndex].copy()
            updatePlayerMedia()
        }
    }

    fun redo() {
        if (historyIndex < history.size - 1) {
            historyIndex++
            _state.value = history[historyIndex].copy()
            updatePlayerMedia()
        }
    }

    fun selectItem(id: String?) {
        updateState(_state.value.copy(selectedItemId = id))
    }

    fun trimSelectedClip(startOffset: Long, endOffset: Long) {
        val s = _state.value
        val id = s.selectedItemId ?: return
        val startIsVideo = s.videoClips.any { it.id == id }
        val startIsAudio = s.audioTracks.any { it.id == id }
        if (!startIsVideo && !startIsAudio) return

        fun normalizedRange(originalDurationMs: Long): Pair<Long, Long>? {
            val start = startOffset.coerceIn(0L, originalDurationMs)
            val end = endOffset.coerceIn(0L, originalDurationMs)
            if (end <= start || end - start < 100L) return null
            return start to end
        }

        if (startIsVideo) {
            val clip = s.videoClips.first { it.id == id }
            val range = normalizedRange(clip.originalDurationMs) ?: return
            val updated = s.videoClips.map {
                if (it.id == id) {
                    if (it.isImage) it.copy(startTrimMs = 0L, durationMs = range.second - range.first)
                    else it.copy(startTrimMs = range.first, durationMs = range.second - range.first)
                } else it
            }
            val (recalculated, total) = recalculateTimeline(updated)
            updateState(
                s.copy(
                    videoClips = recalculated,
                    durationMs = total,
                    playheadMs = s.playheadMs.coerceIn(0L, total),
                    isPlaying = false
                )
            )
            commitState()
            updatePlayerMedia()
        } else {
            val audio = s.audioTracks.first { it.id == id }
            val range = normalizedRange(audio.originalDurationMs) ?: return
            val updated = s.audioTracks.map {
                if (it.id == id) it.copy(startTrimMs = range.first, durationMs = range.second - range.first) else it
            }
            updateState(s.copy(audioTracks = updated))
            commitState()
        }
    }

    fun deleteSelectedClip() {
        val s = _state.value
        val id = s.selectedItemId ?: return
        val videoIndex = s.videoClips.indexOfFirst { it.id == id }

        if (videoIndex >= 0) {
            val newVideoClips = s.videoClips.toMutableList().apply { removeAt(videoIndex) }
            val (recalculated, total) = recalculateTimeline(newVideoClips)
            val nextSelection = recalculated.getOrNull(videoIndex.coerceAtMost(recalculated.lastIndex))?.id
            updateState(
                s.copy(
                    videoClips = recalculated,
                    durationMs = total,
                    selectedItemId = nextSelection,
                    playheadMs = s.playheadMs.coerceIn(0L, total),
                    isPlaying = false
                )
            )
            commitState()
            updatePlayerMedia()
            return
        }

        val audioIndex = s.audioTracks.indexOfFirst { it.id == id }
        if (audioIndex >= 0) {
            val newAudio = s.audioTracks.toMutableList().apply { removeAt(audioIndex) }
            val nextSelection = newAudio.getOrNull(audioIndex.coerceAtMost(newAudio.lastIndex))?.id
                ?: s.videoClips.getOrNull(0)?.id
            updateState(s.copy(audioTracks = newAudio, selectedItemId = nextSelection))
            commitState()
        }
    }

    fun splitSelectedClip() {
        val s = _state.value
        val id = s.selectedItemId ?: return
        val index = s.videoClips.indexOfFirst { it.id == id }
        if (index < 0) return

        val clip = s.videoClips[index]
        val cutOffset = s.playheadMs - clip.startTimeMs
        if (cutOffset < 100L || clip.durationMs - cutOffset < 100L) return

        if (clip.isImage) {
            val first = clip.copy(durationMs = cutOffset, startTrimMs = 0L)
            val second = clip.copy(
                id = UUID.randomUUID().toString(),
                startTrimMs = 0L,
                durationMs = clip.durationMs - cutOffset
            )
            val newClips = s.videoClips.toMutableList().apply {
                set(index, first)
                add(index + 1, second)
            }
            val (recalculated, total) = recalculateTimeline(newClips)
            updateState(s.copy(videoClips = recalculated, durationMs = total, selectedItemId = second.id, isPlaying = false))
            commitState()
            updatePlayerMedia()
            return
        }

        val first = clip.copy(durationMs = cutOffset)
        val second = clip.copy(
            id = UUID.randomUUID().toString(),
            startTrimMs = clip.startTrimMs + cutOffset,
            durationMs = clip.durationMs - cutOffset
        )
        val newClips = s.videoClips.toMutableList().apply {
            set(index, first)
            add(index + 1, second)
        }
        val (recalculated, total) = recalculateTimeline(newClips)
        updateState(
            s.copy(
                videoClips = recalculated,
                durationMs = total,
                selectedItemId = second.id,
                isPlaying = false
            )
        )
        commitState()
        updatePlayerMedia()
    }

    fun seekTo(positionMs: Long) {
        val position = positionMs.coerceIn(0L, _state.value.durationMs)
        _state.update { it.copy(playheadMs = position) }
        seekPlayerToGlobal(position)
    }

    fun togglePlayback() {
        if (_state.value.videoClips.isEmpty()) return
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (_state.value.playheadMs >= _state.value.durationMs) seekTo(0L)
            exoPlayer.play()
        }
    }

    fun setSelectedVolume(volume: Float) {
        val id = _state.value.selectedItemId ?: return
        val normalized = volume.coerceIn(0f, 1f)
        val newState = _state.value.copy(
            videoClips = _state.value.videoClips.map { if (it.id == id) it.copy(volume = normalized) else it },
            audioTracks = _state.value.audioTracks.map { if (it.id == id) it.copy(volume = normalized) else it }
        )
        if (newState != _state.value) {
            updateState(newState)
            commitState()
        }
    }

    fun toggleSelectedMute() {
        val id = _state.value.selectedItemId ?: return
        val newState = _state.value.copy(
            videoClips = _state.value.videoClips.map { if (it.id == id) it.copy(isMuted = !it.isMuted) else it },
            audioTracks = _state.value.audioTracks.map { if (it.id == id) it.copy(isMuted = !it.isMuted) else it }
        )
        if (newState != _state.value) {
            updateState(newState)
            commitState()
        }
    }

    fun moveSelectedClipLeft() = moveSelectedClip(-1)
    fun moveSelectedClipRight() = moveSelectedClip(1)

    private fun moveSelectedClip(delta: Int) {
        val s = _state.value
        val id = s.selectedItemId ?: return
        val index = s.videoClips.indexOfFirst { it.id == id }
        if (index < 0) return
        val target = index + delta
        if (target !in s.videoClips.indices) return
        val reordered = s.videoClips.toMutableList().apply {
            val item = removeAt(index)
            add(target, item)
        }
        val (recalculated, total) = recalculateTimeline(reordered)
        updateState(s.copy(videoClips = recalculated, durationMs = total, isPlaying = false))
        commitState()
        updatePlayerMedia()
    }

    var isRecordingVoiceOver = false
        private set

    fun toggleVoiceOverRecording() {
        if (isRecordingVoiceOver) {
            voiceOverManager.stopVoiceOverSession()
            isRecordingVoiceOver = false
            val track = voiceOverManager.getRecordedTrack()
            if (track != null) {
                val duration = readMediaMetadata(track.uri, getApplication<Application>().contentResolver).durationMs.coerceAtLeast(100L)
                val audioClip = AudioClip(
                    type = AudioTrackType.VOICE_OVER,
                    uri = track.uri,
                    startTimeMs = track.startTimeMs.coerceAtLeast(0L),
                    durationMs = duration,
                    originalDurationMs = duration
                )
                updateState(_state.value.copy(audioTracks = _state.value.audioTracks + audioClip, selectedItemId = audioClip.id))
                commitState()
            }
            exoPlayer.pause()
        } else {
            exoPlayer.play()
            voiceOverManager.startVoiceOverSession(_state.value.playheadMs)
            isRecordingVoiceOver = true
        }
    }

    fun runObjectTracking() {
        val uri = _state.value.videoClips.firstOrNull()?.uri ?: return
        viewModelScope.launch {
            _aiMessages.emit(getApplication<Application>().getString(com.example.R.string.ai_msg_running_tracking))
            when (val result = aiEngine.tracking.analyze(uri)) {
                is VideoAnalysisResult.Tracking -> {
                    updateState(_state.value.copy(aiTrackingData = result.keyframes))
                    commitState()
                    _aiMessages.emit(getApplication<Application>().getString(com.example.R.string.ai_msg_tracking_complete))
                }
                is VideoAnalysisResult.Error -> _aiMessages.emit(result.message)
                else -> Unit
            }
        }
    }

    fun runSmartCut() {
        val uri = _state.value.videoClips.firstOrNull()?.uri ?: return
        viewModelScope.launch {
            _aiMessages.emit(getApplication<Application>().getString(com.example.R.string.ai_msg_analyzing_cuts))
            when (val result = aiEngine.smartCut.analyze(uri)) {
                is VideoAnalysisResult.SmartCuts -> {
                    updateState(_state.value.copy(aiSuggestedCuts = result.suggestedCuts))
                    _aiMessages.emit(getApplication<Application>().getString(com.example.R.string.ai_msg_cuts_suggested))
                }
                is VideoAnalysisResult.Error -> _aiMessages.emit(result.message)
                else -> Unit
            }
        }
    }

    private fun normalizeExcludedIntervals(cuts: List<SuggestedCut>, clip: VideoClip): List<Pair<Long, Long>> {
        val clipStart = clip.startTrimMs
        val clipEnd = clip.startTrimMs + clip.durationMs
        return cuts.mapNotNull { cut ->
            val start = cut.startTimeMs.coerceIn(clipStart, clipEnd)
            val end = cut.endTimeMs.coerceIn(clipStart, clipEnd)
            if (end > start) start to end else null
        }.sortedBy { it.first }.fold(mutableListOf()) { acc, interval ->
            val last = acc.lastOrNull()
            if (last == null || interval.first > last.second) {
                acc += interval
            } else {
                acc[acc.lastIndex] = last.first to maxOf(last.second, interval.second)
            }
            acc
        }
    }

    fun applySmartCuts() {
        val s = _state.value
        val cuts = s.aiSuggestedCuts ?: return
        val targetIndex = s.videoClips.indexOfFirst { it.id == s.selectedItemId }.let { if (it >= 0) it else 0 }
        val target = s.videoClips.getOrNull(targetIndex) ?: return
        val excluded = normalizeExcludedIntervals(cuts, target)
        if (excluded.isEmpty()) {
            rejectSmartCuts()
            return
        }

        val kept = mutableListOf<Pair<Long, Long>>()
        var cursor = target.startTrimMs
        val targetEnd = target.startTrimMs + target.durationMs
        for ((start, end) in excluded) {
            if (start > cursor) kept += cursor to start
            cursor = maxOf(cursor, end)
        }
        if (cursor < targetEnd) kept += cursor to targetEnd

        val validKept = kept.filter { it.second - it.first >= 100L }
        if (validKept.isEmpty()) {
            _aiMessages.tryEmit("Smart Cut produced no valid remaining video.")
            return
        }

        val generated = validKept.map { (start, end) ->
            target.copy(
                id = UUID.randomUUID().toString(),
                startTrimMs = if (target.isImage) 0L else start,
                durationMs = end - start
            )
        }
        val combined = s.videoClips.toMutableList().apply {
            removeAt(targetIndex)
            addAll(targetIndex, generated)
        }
        val (recalculated, total) = recalculateTimeline(combined)
        updateState(
            s.copy(
                videoClips = recalculated,
                durationMs = total,
                aiSuggestedCuts = null,
                selectedItemId = generated.first().id,
                playheadMs = s.playheadMs.coerceIn(0L, total),
                isPlaying = false
            )
        )
        commitState()
        updatePlayerMedia()
    }

    fun rejectSmartCuts() {
        updateState(_state.value.copy(aiSuggestedCuts = null))
    }

    fun runAutoCaptions() {
        val uri = _state.value.videoClips.firstOrNull()?.uri ?: return
        viewModelScope.launch {
            _aiMessages.emit(getApplication<Application>().getString(com.example.R.string.ai_msg_generating_captions))
            when (val result = aiEngine.autoCaption.generate(uri)) {
                is VideoAnalysisResult.AutoCaptions -> {
                    updateState(_state.value.copy(aiSubtitleTrack = result.track))
                    commitState()
                    _aiMessages.emit(getApplication<Application>().getString(com.example.R.string.ai_msg_captions_generated))
                }
                is VideoAnalysisResult.Error -> _aiMessages.emit(result.message)
                else -> Unit
            }
        }
    }

    fun runSmartReframe() {
        val uri = _state.value.videoClips.firstOrNull()?.uri ?: return
        viewModelScope.launch {
            _aiMessages.emit(getApplication<Application>().getString(com.example.R.string.ai_msg_generating_reframe))
            when (val result = aiEngine.smartReframe.process(uri)) {
                is VideoAnalysisResult.SmartReframe -> {
                    val keyframes = result.cropPaths.map {
                        ReframeKeyframe(
                            timeMs = it.timeMs,
                            centerX = it.x,
                            centerY = it.y,
                            width = it.width,
                            height = it.height,
                            confidence = it.confidence
                        )
                    }
                    updateState(_state.value.copy(aiReframeKeyframes = keyframes))
                    commitState()
                    _aiMessages.emit(getApplication<Application>().getString(com.example.R.string.ai_msg_reframe_generated, keyframes.size))
                }
                is VideoAnalysisResult.Error -> _aiMessages.emit(result.message)
                else -> Unit
            }
        }
    }

    fun runEnhancement() {
        val uri = _state.value.videoClips.firstOrNull()?.uri ?: return
        viewModelScope.launch {
            _aiMessages.emit(getApplication<Application>().getString(com.example.R.string.ai_msg_enhancing))
            when (val result = aiEngine.enhancement.enhance(uri)) {
                is VideoAnalysisResult.Error -> _aiMessages.emit(result.message)
                else -> _aiMessages.emit("Video enhancement is unavailable on this device/backend.")
            }
        }
    }

    fun runGenerativeVideo(type: GenerativeType, prompt: String) {
        val uri = _state.value.videoClips.firstOrNull()?.uri
        if (uri == null) {
            viewModelScope.launch { _aiMessages.emit("No source video for generation") }
            return
        }

        val req = GenerativeRequest(
            type = type,
            sourceUri = Uri.parse(uri),
            prompt = prompt
        )
        val jobId = generativeEngine.submitJob(req)
        viewModelScope.launch {
            _aiMessages.emit("Generative Job $jobId submitted.")
            generativeEngine.jobs.collect { jobs ->
                val job = jobs[jobId]
                if (job != null) {
                    when (job.state) {
                        JobState.FAILED -> _aiMessages.emit("Generation Failed: ${job.message}")
                        JobState.COMPLETED -> _aiMessages.emit("Generation Completed!")
                        else -> Unit
                    }
                }
            }
        }
    }

    override fun onCleared() {
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
        super.onCleared()
    }
}
