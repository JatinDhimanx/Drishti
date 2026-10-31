package org.drishti.core.vision

/**
 * Operating power profiles for Drishti.
 */
enum class PowerProfile {
    NORMAL,
    BLIND_SAVER,
    CRITICAL_BATTERY_SAVER
}

/**
 * Controller for optimizing battery consumption, thermal dissipation, and display
 * energy during prolonged assistive navigation sessions.
 *
 * Visually impaired users often do not look at the smartphone screen; dimming the display
 * to the minimum system level (1% or 0.01f) reduces OLED panel power draw by up to 50%
 * while preserving full CameraX throughput and LiteRT inference capability.
 */
class BatterySaverOptimizer(
    private val throttler: AdaptiveFrameThrottler
) {
    var isBlindModeEnabled: Boolean = false
        private set

    var currentBatteryPercent: Int = 100
        private set

    var isSystemPowerSaveActive: Boolean = false
        private set

    /**
     * Updates power state from system broadcasts.
     */
    fun updateSystemPowerState(batteryPercent: Int, isPowerSaveActive: Boolean) {
        this.currentBatteryPercent = batteryPercent.coerceIn(0, 100)
        this.isSystemPowerSaveActive = isPowerSaveActive
        applyProfile(determineActiveProfile())
    }

    /**
     * Toggles blind mode power saver on or off.
     */
    fun setBlindMode(enabled: Boolean) {
        this.isBlindModeEnabled = enabled
        applyProfile(determineActiveProfile())
    }

    /**
     * Evaluates the recommended display brightness (0.01f to 1.0f).
     * Returns 0.01f if blind mode is active or battery is critically low.
     */
    fun getRecommendedScreenBrightness(): Float {
        return if (isBlindModeEnabled || currentBatteryPercent <= 15) {
            0.01f // Minimum visible backlight, maximal OLED power savings
        } else {
            -1.0f // Let system manage auto-brightness
        }
    }

    fun determineActiveProfile(): PowerProfile {
        return when {
            isSystemPowerSaveActive || currentBatteryPercent <= 15 -> PowerProfile.CRITICAL_BATTERY_SAVER
            isBlindModeEnabled -> PowerProfile.BLIND_SAVER
            else -> PowerProfile.NORMAL
        }
    }

    private fun applyProfile(profile: PowerProfile) {
        when (profile) {
            PowerProfile.CRITICAL_BATTERY_SAVER -> {
                throttler.applyPowerSavingThrottle(powerSaveEnabled = true)
            }
            PowerProfile.BLIND_SAVER -> {
                // Keep inference at full speed so safety is not compromised
                throttler.applyPowerSavingThrottle(powerSaveEnabled = false)
            }
            PowerProfile.NORMAL -> {
                throttler.applyPowerSavingThrottle(powerSaveEnabled = false)
            }
        }
    }
}

