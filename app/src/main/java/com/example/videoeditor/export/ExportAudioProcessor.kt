package com.example.videoeditor.export

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Pure timeline math used by the PCM processor and unit tests. */
object AudioAutomation {
    fun gainAt(
        positionMs: Long,
        durationMs: Long,
        volume: Float,
        fadeInDurationMs: Long,
        fadeOutDurationMs: Long
    ): Float {
        val duration = durationMs.coerceAtLeast(0L)
        if (duration == 0L) return 0f
        var gain = volume.coerceIn(0f, 1f)

        val position = positionMs.coerceIn(0L, duration)
        val fadeIn = fadeInDurationMs.coerceIn(0L, duration)
        val fadeOut = fadeOutDurationMs.coerceIn(0L, duration)

        if (fadeIn > 0L) {
            val fadeInFactor = (position.toDouble() / fadeIn.toDouble()).coerceIn(0.0, 1.0).toFloat()
            gain *= fadeInFactor
        }
        if (fadeOut > 0L) {
            val fadeOutStart = duration - fadeOut
            val fadeOutFactor = ((duration - position).toDouble() / fadeOut.toDouble())
                .coerceIn(0.0, 1.0)
                .toFloat()
            if (position >= fadeOutStart) gain *= fadeOutFactor
        }
        return gain.coerceIn(0f, 1f)
    }
}

/** Applies per-track gain automation to Media3's 16-bit PCM stream. */
@OptIn(UnstableApi::class)
class FadeGainAudioProcessor(
    volume: Float,
    durationMs: Long,
    fadeInDurationMs: Long,
    fadeOutDurationMs: Long
) : BaseAudioProcessor() {
    private val volume = volume.coerceIn(0f, 1f)
    private val durationMs = durationMs.coerceAtLeast(0L)
    private val fadeInDurationMs = fadeInDurationMs.coerceAtLeast(0L)
    private val fadeOutDurationMs = fadeOutDurationMs.coerceAtLeast(0L)
    private var processedFrames: Long = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        if (inputAudioFormat.sampleRate <= 0 || inputAudioFormat.channelCount <= 0) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytesPerFrame = inputAudioFormat.bytesPerFrame
        require(bytesPerFrame > 0) { "Invalid PCM frame size" }
        val inputBytes = inputBuffer.remaining()
        require(inputBytes % bytesPerFrame == 0) { "PCM input is not frame aligned" }

        val frames = inputBytes / bytesPerFrame
        val output = replaceOutputBuffer(inputBytes).order(ByteOrder.nativeOrder())
        val source = inputBuffer.order(ByteOrder.nativeOrder())
        repeat(frames) { frameIndex ->
            val absoluteFrame = processedFrames + frameIndex
            val positionMs = (absoluteFrame * 1_000L) / inputAudioFormat.sampleRate.toLong()
            val gain = AudioAutomation.gainAt(
                positionMs = positionMs,
                durationMs = durationMs,
                volume = volume,
                fadeInDurationMs = fadeInDurationMs,
                fadeOutDurationMs = fadeOutDurationMs
            )
            repeat(inputAudioFormat.channelCount) {
                val sample = source.short.toInt()
                val scaled = (sample * gain).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                output.putShort(scaled.toShort())
            }
        }
        processedFrames += frames.toLong()
        output.flip()
    }

    override fun onFlush() {
        processedFrames = 0L
    }

    override fun onReset() {
        processedFrames = 0L
    }
}
