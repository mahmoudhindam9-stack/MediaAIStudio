package com.example.videoeditor.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportAudioProcessorTest {
    @Test
    fun fullVolumeWithoutFades_staysAtUnity() {
        assertEquals(
            1f,
            AudioAutomation.gainAt(500L, 2_000L, 1f, 0L, 0L),
            0.0001f
        )
    }

    @Test
    fun volumeIsApplied() {
        assertEquals(
            0.35f,
            AudioAutomation.gainAt(500L, 2_000L, 0.35f, 0L, 0L),
            0.0001f
        )
    }

    @Test
    fun mutedTrackProducesZeroGain() {
        assertEquals(
            0f,
            AudioAutomation.gainAt(500L, 2_000L, 0f, 0L, 0L),
            0.0001f
        )
    }

    @Test
    fun fadeInStartsAtZero() {
        assertEquals(0f, AudioAutomation.gainAt(0L, 2_000L, 1f, 1_000L, 0L), 0.0001f)
    }

    @Test
    fun fadeOutEndsAtZero() {
        assertEquals(0f, AudioAutomation.gainAt(2_000L, 2_000L, 1f, 0L, 1_000L), 0.0001f)
    }

    @Test
    fun fadeUsesTrimmedDuration() {
        // If the trim duration is 2000, fade out starting at 1000 should apply to the final 1000ms.
        assertEquals(1f, AudioAutomation.gainAt(1_000L, 2_000L, 1f, 0L, 1_000L), 0.0001f)
        assertEquals(0.5f, AudioAutomation.gainAt(1_500L, 2_000L, 1f, 0L, 1_000L), 0.0001f)
    }

    @Test
    fun negativeFadeIsTreatedAsZero() {
        assertEquals(1f, AudioAutomation.gainAt(500L, 2_000L, 1f, -100L, -100L), 0.0001f)
    }

    @Test
    fun fadeDurationsLargerThanActualDuration() {
        // Should clamp fade to duration and cross over peacefully
        assertEquals(0.5f, AudioAutomation.gainAt(500L, 1_000L, 1f, 2_000L, 0L), 0.0001f)
        assertEquals(0.5f, AudioAutomation.gainAt(500L, 1_000L, 1f, 0L, 2_000L), 0.0001f)
    }

    @Test
    fun zeroFade() {
        assertEquals(1f, AudioAutomation.gainAt(500L, 1_000L, 1f, 0L, 0L), 0.0001f)
    }

    @Test
    fun gainsAreAlwaysClampedToZeroThroughOne() {
        val values = listOf(
            AudioAutomation.gainAt(-10L, 1_000L, 2f, 0L, 0L),
            AudioAutomation.gainAt(500L, 1_000L, -1f, 0L, 0L),
            AudioAutomation.gainAt(500L, 1_000L, 1f, 10_000L, 10_000L)
        )
        assertTrue(values.all { it in 0f..1f })
    }
}
