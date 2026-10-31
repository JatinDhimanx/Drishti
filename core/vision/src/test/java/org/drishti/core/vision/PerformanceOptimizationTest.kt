package org.drishti.core.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PerformanceOptimizationTest {

    private lateinit var throttler: AdaptiveFrameThrottler
    private lateinit var optimizer: BatterySaverOptimizer

    @Before
    fun setUp() {
        throttler = AdaptiveFrameThrottler(targetFps = 20, minFps = 10, maxFps = 25)
        optimizer = BatterySaverOptimizer(throttler)
    }

    @Test
    fun throttler_acceptsFirstFrame_andEnforcesInterval() {
        // Base interval for 20 FPS is 50ms
        val t0 = 1000L
        assertTrue("First frame should be accepted", throttler.shouldProcessFrame(t0))

        // Frame arriving 20ms later should be dropped
        assertFalse("Frame arriving before 50ms should be dropped", throttler.shouldProcessFrame(t0 + 20))

        // Frame arriving 49ms later should be dropped
        assertFalse("Frame arriving at 49ms should be dropped", throttler.shouldProcessFrame(t0 + 49))

        // Frame arriving 51ms later should be accepted
        assertTrue("Frame arriving after base interval should be accepted", throttler.shouldProcessFrame(t0 + 51))
    }

    @Test
    fun throttler_adaptsToHighInferenceLatency() {
        // Feed high inference latency (e.g. 80ms)
        repeat(8) {
            throttler.recordInferenceLatency(80L)
        }

        // Expected interval should expand beyond base 50ms (at least 80 * 1.15 = 92ms)
        assertTrue(
            "Interval should dynamically increase under high load",
            throttler.getCurrentIntervalMs() >= 90L
        )

        val t0 = 2000L
        assertTrue(throttler.shouldProcessFrame(t0))
        assertFalse(
            "Frame at 60ms should be dropped when interval expanded to ~92ms",
            throttler.shouldProcessFrame(t0 + 60L)
        )
    }

    @Test
    fun batteryOptimizer_blindModeDimsScreen_preservesInferenceSpeed() {
        optimizer.setBlindMode(true)

        assertEquals(
            "Recommended brightness in blind mode should be 0.01f",
            0.01f,
            optimizer.getRecommendedScreenBrightness(),
            0.001f
        )
        assertEquals(PowerProfile.BLIND_SAVER, optimizer.determineActiveProfile())
        // Throttler interval should still be base (50ms) for high safety throughput
        assertEquals(50L, throttler.getCurrentIntervalMs())
    }

    @Test
    fun batteryOptimizer_criticalBattery_throttlesFpsAndDimsScreen() {
        optimizer.updateSystemPowerState(batteryPercent = 12, isPowerSaveActive = false)

        assertEquals(
            "Recommended brightness should dim when battery <= 15%",
            0.01f,
            optimizer.getRecommendedScreenBrightness(),
            0.001f
        )
        assertEquals(PowerProfile.CRITICAL_BATTERY_SAVER, optimizer.determineActiveProfile())
        // Throttler interval should be throttled to at least 100ms (10 FPS)
        assertTrue(
            "Interval should expand to at least 100ms in critical battery mode",
            throttler.getCurrentIntervalMs() >= 100L
        )
    }

    @Test
    fun batteryOptimizer_systemPowerSave_triggersCriticalProfile() {
        optimizer.updateSystemPowerState(batteryPercent = 85, isPowerSaveActive = true)

        assertEquals(PowerProfile.CRITICAL_BATTERY_SAVER, optimizer.determineActiveProfile())
        assertTrue(throttler.getCurrentIntervalMs() >= 100L)
    }
}

