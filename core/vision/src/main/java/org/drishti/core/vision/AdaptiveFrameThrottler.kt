package org.drishti.core.vision

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

/**
 * Adaptive frame rate throttler designed for real-time mobile AI inference.
 *
 * During active pedestrian navigation, blind users walk at ~1.2 m/s. Frame throttling
 * must NEVER starve the vision pipeline. When active walking is enabled, minimum
 * processing rate is locked at 15–20 FPS (interval <= 50-66ms) to prevent collisions.
 */
class AdaptiveFrameThrottler(
    private val targetFps: Int = 18,
    private val minFps: Int = 8,
    private val maxFps: Int = 24
) {
    private val baseIntervalMs: Long = (1000.0 / targetFps.coerceIn(minFps, maxFps)).toLong()
    private val minIntervalMs: Long = (1000.0 / maxFps).toLong()
    private val maxIntervalMs: Long = (1000.0 / minFps).toLong()

    // Active walking navigation mode: clamps minimum FPS to 15-20 FPS (defaults true for responsive safety)
    private val isNavigatingActive = AtomicBoolean(true)
    private val activeWalkingMaxIntervalMs: Long = 66L // ~15 FPS minimum

    private val lastProcessedTimestampMs = AtomicLong(0L)
    private val currentDynamicIntervalMs = AtomicLong(baseIntervalMs)
    private val lastInferenceDurationMs = AtomicLong(0L)

    // Rolling latency statistics over the last 8 cycles
    private val latencyHistory = LongArray(8) { 0L }
    private var historyIndex = 0

    /**
     * Toggles active walking navigation mode.
     * When true, disables aggressive frame dropping and enforces a strict 15-20 FPS minimum.
     */
    @Synchronized
    fun setNavigatingActive(active: Boolean) {
        isNavigatingActive.set(active)
        recomputeInterval()
    }

    fun isNavigatingActive(): Boolean = isNavigatingActive.get()

    /**
     * Determines whether an incoming camera frame should be processed or dropped.
     *
     * @param timestampMs Monotonic timestamp of the incoming camera frame.
     * @return true if the frame is accepted for inference, false if dropped.
     */
    @Synchronized
    fun shouldProcessFrame(timestampMs: Long): Boolean {
        val lastTimestamp = lastProcessedTimestampMs.get()
        val interval = currentDynamicIntervalMs.get()

        if (timestampMs - lastTimestamp >= interval) {
            lastProcessedTimestampMs.set(timestampMs)
            return true
        }
        return false
    }

    /**
     * Records the actual execution time taken by the on-device AI inference pipeline
     * for the most recent frame, updating the dynamic throttling interval accordingly.
     *
     * @param latencyMs Elapsed time in milliseconds spent in model inference + parsing.
     */
    @Synchronized
    fun recordInferenceLatency(latencyMs: Long) {
        lastInferenceDurationMs.set(latencyMs)
        latencyHistory[historyIndex] = latencyMs
        historyIndex = (historyIndex + 1) % latencyHistory.size

        recomputeInterval()
    }

    /**
     * Forces a lower FPS throttle target, e.g. when entering low-battery or power-saving mode.
     * If user is in active walking navigation, power saving is prevented from dropping below 15 FPS for safety.
     */
    @Synchronized
    fun applyPowerSavingThrottle(powerSaveEnabled: Boolean) {
        if (powerSaveEnabled && !isNavigatingActive.get()) {
            // Drop to 10 FPS (100ms interval) only when not actively walking
            currentDynamicIntervalMs.set(max(100L, currentDynamicIntervalMs.get()))
        } else {
            recomputeInterval()
        }
    }

    private fun recomputeInterval() {
        val nonZeroCount = latencyHistory.count { it > 0 }
        if (nonZeroCount == 0) {
            val defaultInterval = if (isNavigatingActive.get()) min(baseIntervalMs, activeWalkingMaxIntervalMs) else baseIntervalMs
            currentDynamicIntervalMs.set(defaultInterval)
            return
        }

        val averageLatency = latencyHistory.filter { it > 0 }.average()
        val requiredInterval = (averageLatency * 1.15).toLong()

        val effectiveMaxInterval = if (isNavigatingActive.get()) {
            activeWalkingMaxIntervalMs
        } else {
            maxIntervalMs
        }

        val adjustedInterval = max(minIntervalMs, min(effectiveMaxInterval, requiredInterval))
        currentDynamicIntervalMs.set(adjustedInterval)
    }

    fun getCurrentIntervalMs(): Long = currentDynamicIntervalMs.get()
    fun getLastInferenceDurationMs(): Long = lastInferenceDurationMs.get()
    fun getEstimatedEffectiveFps(): Double = 1000.0 / currentDynamicIntervalMs.get()

    @Synchronized
    fun reset() {
        lastProcessedTimestampMs.set(0L)
        currentDynamicIntervalMs.set(baseIntervalMs)
        lastInferenceDurationMs.set(0L)
        latencyHistory.fill(0L)
        historyIndex = 0
    }
}
