package org.drishti.core.risk

import org.drishti.core.model.AssistiveClass
import org.drishti.core.model.BoundingBox
import org.drishti.core.model.DetectedObstacle
import org.drishti.core.model.ThreatLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RiskAssessmentEngineTest {

    private lateinit var engine: RiskAssessmentEngine

    @Before
    fun setUp() {
        engine = RiskAssessmentEngine(
            userLanguage = "en",
            cameraHeightMeters = 1.35f,
            devicePitchDegrees = 15.0f
        )
    }

    @Test
    fun testEmptyDetectionsProducesEmptyTrackedList() {
        val result = engine.evaluateObstacles(emptyList())
        assertTrue("Empty detections should produce empty tracked obstacles", result.isEmpty())
    }

    @Test
    fun testGroundHazardInCenterCorridorYieldsCriticalThreat() {
        // Pothole directly in bottom-center corridor
        val pothole = DetectedObstacle(
            assistiveClass = AssistiveClass.POTHOLE_TRENCH,
            confidence = 0.85f,
            boundingBox = BoundingBox(left = 0.35f, top = 0.65f, right = 0.65f, bottom = 0.95f)
        )

        val tracked = engine.evaluateObstacles(listOf(pothole))
        assertEquals(1, tracked.size)
        val first = tracked.first()
        assertEquals(ThreatLevel.CRITICAL, first.threatLevel)
        assertTrue("Ground hazard distance should be close to user", first.estimatedDistanceMeters < 3.5f)
    }

    @Test
    fun testDistantStaticObstacleYieldsCautionOrWarning() {
        // Static pole on lateral edge
        val pole = DetectedObstacle(
            assistiveClass = AssistiveClass.UTILITY_POLE,
            confidence = 0.75f,
            boundingBox = BoundingBox(left = 0.80f, top = 0.20f, right = 0.95f, bottom = 0.50f)
        )

        val tracked = engine.evaluateObstacles(listOf(pole))
        assertEquals(1, tracked.size)
        val first = tracked.first()
        assertTrue(first.threatLevel == ThreatLevel.CAUTION || first.threatLevel == ThreatLevel.ADVISORY)
    }

    @Test
    fun testClockPositionCalculation() {
        // Obstacle on extreme right
        val rightObstacle = DetectedObstacle(
            assistiveClass = AssistiveClass.PEDESTRIAN_MOVING,
            confidence = 0.80f,
            boundingBox = BoundingBox(left = 0.85f, top = 0.40f, right = 0.95f, bottom = 0.80f)
        )

        val tracked = engine.evaluateObstacles(listOf(rightObstacle))
        val first = tracked.first()
        assertTrue("Obstacle on right should be around 1 to 3 o'clock", first.clockPosition in 1..3)
    }
}
