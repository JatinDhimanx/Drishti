package org.drishti.core.vision

import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

/**
 * Adaptive frame rate throttler designed for real-time mobile AI inference.
 *
 * Targets a stable 15 to 20 FPS processing rate while monitoring real-time
 * model inference latency. If inference latency spikes (e.g. thermal throttling
 * or heavy background processing), this throttler dynamically expands the
 * minimum inter-frame interval to prevent queue bloat, out-of-order execution,
 * and high CPU thermal buildup.
 */
class AdaptiveFrameThrottler(
    private val targetFps: Int = 18,
    private val minFps: Int = 8,
    private val maxFps: Int = 24
) {
    private val baseIntervalMs: Long = (1000.0 / targetFps.coerceIn(minFps, maxFps)).toLong()
    private val minIntervalMs: Long = (1000.0 / maxFps).toLong()
    private val maxIntervalMs: Long = (1000.0 / minFps).toLong()

    private val lastProcessedTimestampMs = AtomicLong(0L)
    private val currentDynamicIntervalMs = AtomicLong(baseIntervalMs)
    private val lastInferenceDurationMs = AtomicLong(0L)

    // Rolling latency statistics over the last 8 cycles
    private val latencyHistory = LongArray(8) { 0L }
    private var historyIndex = 0

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
     */
    @Synchronized
    fun applyPowerSavingThrottle(powerSaveEnabled: Boolean) {
        if (powerSaveEnabled) {
            // Drop to minimum 10 FPS (100ms interval)
            currentDynamicIntervalMs.set(max(100L, currentDynamicIntervalMs.get()))
        } else {
            recomputeInterval()
        }
    }

    private fun recomputeInterval() {
        val nonZeroCount = latencyHistory.count { it > 0 }
        if (nonZeroCount == 0) {
            currentDynamicIntervalMs.set(baseIntervalMs)
            return
        }

        val averageLatency = latencyHistory.filter { it > 0 }.average()
        val requiredInterval = (averageLatency * 1.15).toLong()
        val adjustedInterval = max(baseIntervalMs, min(maxIntervalMs, requiredInterval))
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
        historyIndex = 0
        for (i in latencyHistory.indices) {
            latencyHistory[i] = 0L
        }
    }
}
