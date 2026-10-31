package org.drishti.app.ml

import org.drishti.core.model.BoundingBox
import org.drishti.core.vision.GeometryObstacleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OfflineReplayEvaluatorTest {

    private lateinit var evaluator: OfflineReplayEvaluator

    @Before
    fun setup() {
        evaluator = OfflineReplayEvaluator()
    }

    private fun makeFlatDepth(dim: Int = 16, pitchDeg: Float = 15.0f): GeometryObstacleEngine.DepthGrid {
        val depths = FloatArray(dim * dim)
        val vFov = 50.0f
        val camH = 1.35f
        val pitchRad = Math.toRadians(pitchDeg.toDouble()).toFloat()
        for (y in 0 until dim) {
            val normY = (y + 0.5f) / dim
            val vertAngleDeg = (normY - 0.5f) * vFov
            val totalDepressionRad = pitchRad + Math.toRadians(vertAngleDeg.toDouble()).toFloat()
            val sinDep = kotlin.math.sin(totalDepressionRad)
            val d = if (sinDep > 0.05f) (camH / sinDep).coerceIn(0.5f, 8.0f) else 8.0f
            for (x in 0 until dim) {
                depths[y * dim + x] = d
            }
        }
        return GeometryObstacleEngine.DepthGrid(dim, dim, depths)
    }

    private fun makeObstacleDepth(dim: Int = 16, dist: Float = 1.8f, pitchDeg: Float = 15.0f): GeometryObstacleEngine.DepthGrid {
        val grid = makeFlatDepth(dim, pitchDeg)
        val depths = grid.depths.clone()
        // Place obstacle in middle rows, middle cols (corridor)
        for (y in 6..10) {
            for (x in 7..9) {
                depths[y * dim + x] = dist
            }
        }
        return GeometryObstacleEngine.DepthGrid(dim, dim, depths)
    }

    @Test
    fun testReplayModeRejectsTableAndLaptopAnnouncements() {
        val frames = mutableListOf<OfflineReplayEvaluator.ReplayFrame>()

        // 6 consecutive frames of a laptop / table placed in front of camera
        for (i in 0..5) {
            frames.add(
                OfflineReplayEvaluator.ReplayFrame(
                    frameIndex = i,
                    timestampMs = 1000L + (i * 100L),
                    pitchDeg = 15.0f,
                    detectedRawBoxes = listOf("table" to BoundingBox(0.35f, 0.40f, 0.65f, 0.80f)),
                    depthGrid = makeObstacleDepth(dist = 1.8f)
                )
            )
        }

        val logs = evaluator.evaluateSequence(frames)
        assertEquals(6, logs.size)

        // Verify that across all frames, NO blacklisted word ("table", "desk", "laptop") was ever spoken!
        for (log in logs) {
            assertFalse("Table must never be spoken", log.blacklistedSpoken)
            for (alert in log.spokenAlerts) {
                assertFalse("Alert must never say table", alert.contains("table", ignoreCase = true))
                assertFalse("Alert must never say desk", alert.contains("desk", ignoreCase = true))
                assertFalse("Alert must never say laptop", alert.contains("laptop", ignoreCase = true))
            }
        }

        // Must announce generic obstacle instead
        val alertTriggered = logs.any { it.spokenAlerts.isNotEmpty() }
        assertTrue("Geometric obstacle in path must trigger alert", alertTriggered)
        val alertText = logs.first { it.spokenAlerts.isNotEmpty() }.spokenAlerts.first()
        assertTrue("Generic fallback must say Obstacle", alertText.contains("Obstacle", ignoreCase = true))
    }

    @Test
    fun testClearCorridorProducesZeroFalseAlarms() {
        val frames = (0..10).map { i ->
            OfflineReplayEvaluator.ReplayFrame(
                frameIndex = i,
                timestampMs = 1000L + (i * 100L),
                pitchDeg = 15.0f,
                detectedRawBoxes = emptyList(),
                depthGrid = makeFlatDepth()
            )
        }

        val logs = evaluator.evaluateSequence(frames)
        for (log in logs) {
            assertTrue("Clear path must produce 0 spoken alerts", log.spokenAlerts.isEmpty())
            assertFalse("No false alarm should be flagged", log.falseAlarmDetected)
        }
    }
}

