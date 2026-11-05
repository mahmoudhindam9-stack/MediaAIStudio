package com.example.audio

/**
 * Coordinates recording voice-overs while synchronized with video playback.
 */
interface VoiceOverManager {
    fun startVoiceOverSession(videoPlaybackPositionMs: Long)
    fun stopVoiceOverSession()
    fun cancelVoiceOverSession()
    
    fun getRecordedTrack(): AudioTrack?
}
