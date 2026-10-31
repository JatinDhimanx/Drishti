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
            assistiveClass = AssistiveClass.POTHOLE_OPEN_TRENCH,
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
            assistiveClass = AssistiveClass.POLE_BOLLARD,
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

    @Test
    fun testStrict2MeterVibrationRules() {
        val staticFarPole = org.drishti.core.model.TrackedObstacle(
            trackId = 1L,
            obstacle = DetectedObstacle(AssistiveClass.POLE_BOLLARD, 0.9f, BoundingBox(0.45f, 0.5f, 0.55f, 0.8f)),
            estimatedDistanceMeters = 2.4f, // > 2.0m
            azimuthDegrees = 0f,
            clockPosition = 12,
            timeToCollisionSeconds = 2.0f,
            threatLevel = ThreatLevel.CAUTION,
            isMoving = false,
            isApproachingUser = false,
            relativeClosingSpeed = 0f
        )
        // Rule 1: Static > 2m -> NO VIBRATION
        org.junit.Assert.assertFalse("Static object > 2m must not vibrate", engine.shouldVibrate(staticFarPole))

        val approachingPerson = org.drishti.core.model.TrackedObstacle(
            trackId = 2L,
            obstacle = DetectedObstacle(AssistiveClass.PEDESTRIAN_MOVING, 0.9f, BoundingBox(0.42f, 0.3f, 0.58f, 0.85f)),
            estimatedDistanceMeters = 1.6f, // <= 2.0m
            azimuthDegrees = 0f,
            clockPosition = 12,
            timeToCollisionSeconds = 1.2f,
            threatLevel = ThreatLevel.CRITICAL,
            isMoving = true,
            isApproachingUser = true,
            relativeClosingSpeed = 1.2f
        )
        // Rule 2: Approaching dynamic <= 2m -> VIBRATE
        assertTrue("Approaching dynamic object <= 2m must vibrate", engine.shouldVibrate(approachingPerson))

        val imminentStaticCollision = org.drishti.core.model.TrackedObstacle(
            trackId = 3L,
            obstacle = DetectedObstacle(AssistiveClass.POLE_BOLLARD, 0.9f, BoundingBox(0.45f, 0.55f, 0.55f, 0.9f)),
            estimatedDistanceMeters = 1.4f, // <= 2.0m
            azimuthDegrees = 0f,
            clockPosition = 12,
            timeToCollisionSeconds = 1.1f,
            threatLevel = ThreatLevel.CRITICAL,
            isMoving = false,
            isApproachingUser = false,
            relativeClosingSpeed = 1.1f // user walking into it
        )
        // Rule 3: Imminent static collision <= 2m closing -> VIBRATE
        assertTrue("Static collision <= 2m with closing speed must vibrate", engine.shouldVibrate(imminentStaticCollision))
    }
}
