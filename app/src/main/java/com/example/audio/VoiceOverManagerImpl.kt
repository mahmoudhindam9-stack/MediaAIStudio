package com.example.audio

import android.content.Context
import java.io.File
import java.util.UUID

class VoiceOverManagerImpl(private val context: Context) : VoiceOverManager {
    private val audioRecorder = AudioRecorderImpl(context)
    private var currentOutputFile: File? = null
    private var currentStartMs: Long = 0
    
    override fun startVoiceOverSession(videoPlaybackPositionMs: Long) {
        currentStartMs = videoPlaybackPositionMs
        val dir = context.cacheDir
        currentOutputFile = File(dir, "voiceover_${UUID.randomUUID()}.m4a")
        audioRecorder.startRecording(currentOutputFile!!.absolutePath)
    }

    override fun stopVoiceOverSession() {
        audioRecorder.stopRecording()
    }

    override fun cancelVoiceOverSession() {
        audioRecorder.stopRecording()
        currentOutputFile?.delete()
        currentOutputFile = null
    }

    override fun getRecordedTrack(): AudioTrack? {
        val file = currentOutputFile ?: return null
        if (!file.exists()) return null
        
        return AudioTrack(
            id = UUID.randomUUID().toString(),
            type = AudioTrackType.VOICE_OVER,
            uri = file.absolutePath,
            startTimeMs = currentStartMs,
            // duration and end time will be set correctly upon loading metadata, but as placeholder:
            endTimeMs = currentStartMs + 1000L // We can compute exact duration using MediaMetadataRetriever
        )
    }
}
