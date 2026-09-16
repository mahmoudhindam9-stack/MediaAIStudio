package com.example.audio

import kotlinx.coroutines.flow.Flow

/**
 * Base interface for capturing raw audio from the device microphone.
 */
interface AudioRecorder {
    fun startRecording(outputFileUri: String)
    fun stopRecording()
    fun pauseRecording()
    fun resumeRecording()
    
    /**
     * Emits real-time amplitudes for live waveform visualization.
     */
    fun getLiveAmplitudes(): Flow<Int>
}
