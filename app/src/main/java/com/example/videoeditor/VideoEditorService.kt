package com.example.videoeditor

// This module is responsible for Video Editing features.
// To be implemented in future phases.
interface VideoEditorService {
    // fun trimVideo(...)
    // fun applyEffect(...)
    
    /**
     * Loads a project timeline containing video tracks and multiple audio tracks
     * (original audio, music, voice-overs, sound effects).
     */
    fun loadTimeline(timeline: Timeline)
    
    /**
     * Previews the timeline at the current playhead position.
     */
    fun previewTimeline(positionMs: Long)
}
