package org.drishti.core.feedback

import org.drishti.core.model.ThreatLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackManagerTest {

    @Test
    fun testCriticalStopWaveformPattern() {
        val pattern = HapticManager.PATTERN_CRITICAL_STOP
        val amplitudes = HapticManager.AMPLITUDES_CRITICAL_STOP

        // Should have 3 sharp ON pulses (indices 1, 3, 5)
        assertEquals(6, pattern.size)
        assertEquals(6, amplitudes.size)

        // Verifying max vibration intensity (255) for critical danger
        assertEquals(255, amplitudes[1])
        assertEquals(255, amplitudes[3])
        assertEquals(255, amplitudes[5])
    }

    @Test
    fun testWarningHazardWaveformPattern() {
        val pattern = HapticManager.PATTERN_WARNING_HAZARD
        val amplitudes = HapticManager.AMPLITUDES_WARNING_HAZARD

        // 2 medium-high pulses (indices 1, 3)
        assertEquals(4, pattern.size)
        assertEquals(4, amplitudes.size)
        assertEquals(220, amplitudes[1])
        assertEquals(220, amplitudes[3])
    }

    @Test
    fun testCautionAlertWaveformPattern() {
        val pattern = HapticManager.PATTERN_CAUTION_ALERT
        val amplitudes = HapticManager.AMPLITUDES_CAUTION_ALERT

        // 1 short gentle pulse
        assertEquals(2, pattern.size)
        assertEquals(2, amplitudes.size)
        assertEquals(160, amplitudes[1])
    }

    @Test
    fun testDirectionalTurnWaveformsAreAsymmetric() {
        val leftPattern = HapticManager.PATTERN_TURN_LEFT
        val rightPattern = HapticManager.PATTERN_TURN_RIGHT

        // Left is a double pulse, Right is a rapid triple pulse
        // They must have different lengths so user feels distinct directional tactile cues
        assertTrue("Turn left and right must have distinct pulse counts", leftPattern.size != rightPattern.size)
        assertEquals(4, leftPattern.size)
        assertEquals(6, rightPattern.size)
    }

    @Test
    fun testSosAlarmWaveformIsHighIntensityAndLong() {
        val pattern = HapticManager.PATTERN_SOS_ALARM
        val amplitudes = HapticManager.AMPLITUDES_SOS_ALARM

        // SOS waveform should have 3 long bursts totaling over 1200ms
        val totalActiveDuration = pattern[1] + pattern[3] + pattern[5]
        assertTrue("SOS alarm should have long active pulses (> 1000ms)", totalActiveDuration >= 1000L)
        assertEquals(255, amplitudes[1])
        assertEquals(255, amplitudes[5])
    }
}

