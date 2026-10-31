package org.drishti.feature.navigation

import org.drishti.core.location.PedestrianNavigationEngine
import org.drishti.core.model.AssistiveClass
import org.drishti.core.model.BoundingBox
import org.drishti.core.model.CorridorStatus
import org.drishti.core.model.DetectedObstacle
import org.drishti.core.model.DistanceTier
import org.drishti.core.model.MotionState
import org.drishti.core.model.SpatialZone
import org.drishti.core.model.SurfaceType
import org.drishti.core.model.ThreatLevel
import org.drishti.core.model.TurnManeuver
import org.drishti.core.risk.HazardIntelligenceEngine
import org.drishti.core.risk.SpatialEnvironmentAnalyzer
import org.drishti.core.tracking.ByteTracklet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FusedGuidanceCoordinatorTest {

    private lateinit var coordinator: FusedGuidanceCoordinator

    @Before
    fun setup() {
        coordinator = FusedGuidanceCoordinator(userLanguage = "en")
    }

    private fun createHazard(
        item: AssistiveClass,
        level: ThreatLevel,
        zone: SpatialZone,
        speech: String
    ): HazardIntelligenceEngine.HazardAssessment {
        val det = DetectedObstacle(item, 0.9f, BoundingBox(0.4f, 0.4f, 0.6f, 0.7f))
        val track = ByteTracklet(1L, item, det, 3.0f)
        return HazardIntelligenceEngine.HazardAssessment(
            tracklet = track,
            threatLevel = level,
            threatScore = 120f,
            spatialZone = zone,
            distanceTier = DistanceTier.NEAR,
            alertSpeech = speech,
            requiresImmediateHaptic = level == ThreatLevel.CRITICAL
        )
    }

    private fun createNavGuidance(maneuver: TurnManeuver, steps: Int): PedestrianNavigationEngine.NavGuidance {
        return PedestrianNavigationEngine.NavGuidance(
            maneuver = maneuver,
            distanceToNextTurnMeters = steps * 0.7f,
            estimatedStepsToNextTurn = steps,
            targetBearingDegrees = 90f,
            isOffRoute = false,
            currentWaypointIndex = 1,
            totalWaypoints = 5,
            spokenInstruction = "In about $steps steps, turn left."
        )
    }

    @Test
    fun testCriticalObstaclePreemptsNavigation() {
        val criticalHazard = createHazard(
            item = AssistiveClass.CAR_VAN_TRUCK,
            level = ThreatLevel.CRITICAL,
            zone = SpatialZone.CENTER,
            speech = "Stop! Car approaching ahead!"
        )
        val navGuidance = createNavGuidance(TurnManeuver.TURN_LEFT, 20)

        val output = coordinator.synthesizeGuidance(criticalHazard, navGuidance)

        // Must output exclusively the critical hazard warning
        assertEquals("Stop! Car approaching ahead!", output.speechText)
        assertTrue(output.isCriticalPreemption)
        assertTrue(output.requiresHaptic)
        assertEquals(ThreatLevel.CRITICAL, output.primaryThreat)
    }

    @Test
    fun testFusedTurnManeuverWithPeripheralObstacle() {
        val cautionPole = createHazard(
            item = AssistiveClass.UTILITY_POLE,
            level = ThreatLevel.CAUTION,
            zone = SpatialZone.LEFT,
            speech = "Pole on your left."
        )
        val navGuidance = createNavGuidance(TurnManeuver.TURN_LEFT, 25)

        val output = coordinator.synthesizeGuidance(cautionPole, navGuidance)

        assertFalse(output.isCriticalPreemption)
        assertTrue("Speech should combine turn and obstacle smoothly",
            output.speechText.contains("turn left carefully", ignoreCase = true))
        assertTrue("Speech should mention pole on left side",
            output.speechText.contains("pole", ignoreCase = true))
    }

    @Test
    fun testClearPathOutputsPureNavigation() {
        val navGuidance = createNavGuidance(TurnManeuver.TURN_LEFT, 30)

        val output = coordinator.synthesizeGuidance(null, navGuidance)

        assertEquals("In about 30 steps, turn left.", output.speechText)
        assertFalse(output.isCriticalPreemption)
        assertEquals(ThreatLevel.NONE, output.primaryThreat)
    }

    @Test
    fun testCorridorBlockageTriggersEmergencyHalt() {
        val envSummary = SpatialEnvironmentAnalyzer.EnvironmentSummary(
            corridorStatus = CorridorStatus.BLOCKED,
            recommendedDetour = SpatialEnvironmentAnalyzer.DetourRecommendation.STOP_IMMEDIATELY,
            detectedSurface = SurfaceType.WALKABLE_FOOTPATH,
            analyses = emptyList(),
            criticalGroundHazards = emptyList()
        )
        val navGuidance = createNavGuidance(TurnManeuver.CONTINUE_STRAIGHT, 50)

        val output = coordinator.synthesizeGuidance(null, navGuidance, envSummary)

        assertTrue(output.isCriticalPreemption)
        assertTrue(output.speechText.contains("blocked", ignoreCase = true))
        assertEquals(ThreatLevel.CRITICAL, output.primaryThreat)
    }
}

