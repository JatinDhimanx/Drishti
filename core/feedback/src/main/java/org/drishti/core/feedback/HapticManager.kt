package org.drishti.core.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import org.drishti.core.model.ThreatLevel

/**
 * Precision Haptic Feedback synthesizer for Drishti.
 * Implements spatial vibration waveforms for obstacles, turns, and alerts.
 */
class HapticManager(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    var isHapticEnabled: Boolean = true

    companion object {
        val PATTERN_CRITICAL_STOP = longArrayOf(0, 120, 60, 120, 60, 250)
        val AMPLITUDES_CRITICAL_STOP = intArrayOf(0, 255, 0, 255, 0, 255)

        val PATTERN_WARNING_HAZARD = longArrayOf(0, 180, 80, 180)
        val AMPLITUDES_WARNING_HAZARD = intArrayOf(0, 220, 0, 220)

        val PATTERN_CAUTION_ALERT = longArrayOf(0, 120)
        val AMPLITUDES_CAUTION_ALERT = intArrayOf(0, 160)

        val PATTERN_DROP_OFF = longArrayOf(0, 180, 80, 250)
        val AMPLITUDES_DROP_OFF = intArrayOf(0, 255, 0, 200)

        val PATTERN_TURN_LEFT = longArrayOf(0, 100, 100, 250)
        val AMPLITUDES_TURN_LEFT = intArrayOf(0, 200, 0, 255)

        val PATTERN_TURN_RIGHT = longArrayOf(0, 80, 80, 80, 80, 80)
        val AMPLITUDES_TURN_RIGHT = intArrayOf(0, 255, 0, 255, 0, 255)

        val PATTERN_SOS_ALARM = longArrayOf(0, 300, 100, 300, 100, 500)
        val AMPLITUDES_SOS_ALARM = intArrayOf(0, 255, 0, 255, 0, 255)
    }

    /**
     * Dispatches vibration waveform based on ThreatLevel.
     */
    fun vibrateForThreat(level: ThreatLevel) {
        when (level) {
            ThreatLevel.CRITICAL -> vibrateCriticalStop()
            ThreatLevel.WARNING -> vibrateWarning()
            ThreatLevel.CAUTION -> vibrateCaution()
            ThreatLevel.ADVISORY -> vibrateClick()
            ThreatLevel.NONE -> {}
        }
    }

    /**
     * Critical Collision Stop Pattern:
     * 3 sharp, maximum-intensity pulses [0ms, 120ms ON, 60ms OFF, 120ms ON, 60ms OFF, 250ms ON]
     */
    fun vibrateCriticalStop() {
        if (!isHapticEnabled || vibrator == null || !vibrator.hasVibrator()) return
        vibratePattern(PATTERN_CRITICAL_STOP, AMPLITUDES_CRITICAL_STOP)
    }

    /**
     * Warning Hazard Pattern:
     * 2 medium-high intensity pulses [0ms, 180ms ON, 80ms OFF, 180ms ON]
     */
    fun vibrateWarning() {
        if (!isHapticEnabled || vibrator == null || !vibrator.hasVibrator()) return
        vibratePattern(PATTERN_WARNING_HAZARD, AMPLITUDES_WARNING_HAZARD)
    }

    /**
     * Caution Alert Pattern:
     * 1 short, gentle pulse [0ms, 120ms ON]
     */
    fun vibrateCaution() {
        if (!isHapticEnabled || vibrator == null || !vibrator.hasVibrator()) return
        vibratePattern(PATTERN_CAUTION_ALERT, AMPLITUDES_CAUTION_ALERT)
    }

    /**
     * Ground Drop-off / Downward Stairs:
     * Step-down slide pattern [0ms, 180ms ON, 80ms OFF, 250ms ON]
     */
    fun vibrateDropOff() {
        if (!isHapticEnabled || vibrator == null || !vibrator.hasVibrator()) return
        vibratePattern(PATTERN_DROP_OFF, AMPLITUDES_DROP_OFF)
    }

    /**
     * Turn Left Ahead (20m):
     * Double pulse skewed left [0ms, 100ms ON, 100ms OFF, 250ms ON]
     */
    fun vibrateTurnLeft() {
        if (!isHapticEnabled || vibrator == null || !vibrator.hasVibrator()) return
        vibratePattern(PATTERN_TURN_LEFT, AMPLITUDES_TURN_LEFT)
    }

    /**
     * Turn Right Ahead (20m):
     * Rapid triple pulse [0ms, 80ms ON, 80ms OFF, 80ms ON, 80ms OFF, 80ms ON]
     */
    fun vibrateTurnRight() {
        if (!isHapticEnabled || vibrator == null || !vibrator.hasVibrator()) return
        vibratePattern(PATTERN_TURN_RIGHT, AMPLITUDES_TURN_RIGHT)
    }

    /**
     * Emergency SOS Alarm Pattern:
     * Continuous escalating siren bursts.
     */
    fun vibrateSosAlarm() {
        if (!isHapticEnabled || vibrator == null || !vibrator.hasVibrator()) return
        vibratePattern(PATTERN_SOS_ALARM, AMPLITUDES_SOS_ALARM)
    }

    /**
     * Off-Route / Wrong Direction Warning:
     * Continuous rapid buzzing [0ms, 200ms ON, 100ms OFF, 200ms ON]
     */
    fun vibrateOffRoute() {
        if (!isHapticEnabled || vibrator == null || !vibrator.hasVibrator()) return
        val pattern = longArrayOf(0, 200, 100, 200)
        val amplitudes = intArrayOf(0, 255, 0, 255)
        vibratePattern(pattern, amplitudes)
    }

    /**
     * Reassuring heartbeat pulse while on correct route:
     */
    fun vibrateHeartbeat() {
        if (!isHapticEnabled || vibrator == null || !vibrator.hasVibrator()) return
        val effect = VibrationEffect.createOneShot(60, 120)
        vibrator.vibrate(effect)
    }

    /**
     * Destination Arrived Celebration Pattern:
     * [0ms, 400ms ON, 200ms OFF, 600ms ON]
     */
    fun vibrateArrival() {
        if (!isHapticEnabled || vibrator == null || !vibrator.hasVibrator()) return
        val pattern = longArrayOf(0, 400, 200, 600)
        val amplitudes = intArrayOf(0, 255, 0, 255)
        vibratePattern(pattern, amplitudes)
    }

    /**
     * Short haptic click for button taps:
     */
    fun vibrateClick() {
        if (!isHapticEnabled || vibrator == null || !vibrator.hasVibrator()) return
        val effect = VibrationEffect.createOneShot(35, 180)
        vibrator.vibrate(effect)
    }

    private fun vibratePattern(pattern: LongArray, amplitudes: IntArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(pattern, amplitudes, -1)
            vibrator?.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, -1)
        }
    }
}
