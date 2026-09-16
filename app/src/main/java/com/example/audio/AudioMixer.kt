package com.example.audio

/**
 * Handles mixing of multiple AudioTracks, volume adjustments, and ducking logic.
 */
interface AudioMixer {
    fun addTrack(track: AudioTrack)
    fun removeTrack(trackId: String)
    fun setTrackVolume(trackId: String, volume: Float)
    fun toggleMute(trackId: String, isMuted: Boolean)
    fun toggleSolo(trackId: String, isSolo: Boolean)
    
    /**
     * Applies ducking to background tracks when a primary track (like voice-over) is active.
     */
    fun applyDucking(primaryTrackId: String, targetTrackIds: List<String>, duckingLevel: Float)
    
    // Future: fun mix(): AudioStream
}
