package org.drishti.core.risk

import org.drishti.core.model.AssistiveClass
import org.drishti.core.model.BoundingBox
import org.drishti.core.model.DetectedObstacle
import org.drishti.core.model.MotionState
import org.drishti.core.model.PathTrajectory
import org.drishti.core.model.ThreatLevel
import org.drishti.core.model.WalkingCorridor
import org.drishti.core.tracking.ByteTracklet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HazardIntelligenceEngineTest {

    private lateinit var engine: HazardIntelligenceEngine
    private val corridor = WalkingCorridor(0.30f, 0.70f)

    @Before
    fun setup() {
        engine = HazardIntelligenceEngine(userLanguage = "en", corridor = corridor)
    }

    private fun createTracklet(
        id: Long,
        itemClass: AssistiveClass,
        box: BoundingBox,
        distanceMeters: Float,
        motionState: MotionState = MotionState.STATIONARY,
        pathTrajectory: PathTrajectory = PathTrajectory.IN_PATH_DIRECTLY,
        ttc: Float? = null
    ): ByteTracklet {
        val det = DetectedObstacle(itemClass, 0.9f, box)
        val track = ByteTracklet(id, itemClass, det, distanceMeters)
        track.motionState = motionState
        track.pathTrajectory = pathTrajectory
        track.timeToCollisionSeconds = ttc
        return track
    }

    @Test
    fun testApproachingVehicleTriggersCriticalAlert() {
        val approachingCar = createTracklet(
            id = 1L,
            itemClass = AssistiveClass.CAR_VAN_TRUCK,
            box = BoundingBox(0.40f, 0.35f, 0.60f, 0.65f),
            distanceMeters = 4.0f,
            motionState = MotionState.APPROACHING,
            pathTrajectory = PathTrajectory.IN_PATH_DIRECTLY,
            ttc = 1.8f
        )

        val assessments = engine.evaluateHazards(listOf(approachingCar))
        assertEquals(1, assessments.size)

        val assessment = assessments.first()
        assertEquals(ThreatLevel.CRITICAL, assessment.threatLevel)
        assertTrue("Threat score should exceed critical threshold", assessment.threatScore >= 120f)
        assertTrue(engine.shouldTriggerAlert(assessment, assessments))
        assertTrue(assessment.alertSpeech.contains("Stop!"))
    }

    @Test
    fun testGroundHazardInPathYieldsCriticalThreat() {
        val pothole = createTracklet(
            id = 2L,
            itemClass = AssistiveClass.POTHOLE_OPEN_TRENCH,
            box = BoundingBox(0.35f, 0.70f, 0.65f, 0.95f),
            distanceMeters = 1.2f, // 1.2m directly in step range
            motionState = MotionState.STATIONARY,
            pathTrajectory = PathTrajectory.IN_PATH_DIRECTLY
        )

        val assessments = engine.evaluateHazards(listOf(pothole))
        val assessment = assessments.first()

        assertEquals(ThreatLevel.CRITICAL, assessment.threatLevel)
        assertTrue(assessment.requiresImmediateHaptic)
        assertTrue(assessment.alertSpeech.contains("pothole", ignoreCase = true))
    }

    @Test
    fun testPreemptionSuppressesLowPriorityChatterWhenCriticalHazardActive() {
        val criticalVehicle = createTracklet(
            id = 10L,
            itemClass = AssistiveClass.MOTORCYCLE_RICKSHAW,
            box = BoundingBox(0.40f, 0.35f, 0.60f, 0.65f),
            distanceMeters = 3.5f,
            motionState = MotionState.APPROACHING,
            pathTrajectory = PathTrajectory.IN_PATH_DIRECTLY,
            ttc = 1.5f
        )

        val distantTrashBin = createTracklet(
            id = 20L,
            itemClass = AssistiveClass.POLE_BOLLARD,
            box = BoundingBox(0.05f, 0.20f, 0.20f, 0.50f),
            distanceMeters = 6.5f,
            motionState = MotionState.STATIONARY,
            pathTrajectory = PathTrajectory.OUTSIDE_PATH
        )

        val assessments = engine.evaluateHazards(listOf(criticalVehicle, distantTrashBin))
        val criticalAssessment = assessments.first { it.tracklet.trackId == 10L }
        val lowAssessment = assessments.first { it.tracklet.trackId == 20L }

        // Critical hazard MUST trigger
        assertTrue("Critical hazard must alert", engine.shouldTriggerAlert(criticalAssessment, assessments))

        // Low priority dustbin outside path MUST be suppressed to preserve audio bandwidth
        assertFalse("Advisory chatter must be preempted when critical hazard is active",
            engine.shouldTriggerAlert(lowAssessment, assessments))
    }

    @Test
    fun testRecedingObjectIsIgnored() {
        val recedingBicycle = createTracklet(
            id = 30L,
            itemClass = AssistiveClass.BICYCLE_ESCOOTER,
            box = BoundingBox(0.45f, 0.20f, 0.55f, 0.40f),
            distanceMeters = 5.0f,
            motionState = MotionState.RECEDING,
            pathTrajectory = PathTrajectory.MOVING_PARALLEL
        )

        val assessments = engine.evaluateHazards(listOf(recedingBicycle))
        val assessment = assessments.first()

        assertEquals(ThreatLevel.NONE, assessment.threatLevel)
        assertFalse("Receding object must not trigger alert", engine.shouldTriggerAlert(assessment, assessments))
    }

    @Test
    fun testThreatEscalationBypassesCooldown() {
        val time = 1000L

        // Track starts as stationary street furniture at 2.8m -> Warning
        val obstacle = createTracklet(
            id = 40L,
            itemClass = AssistiveClass.POLE_BOLLARD,
            box = BoundingBox(0.42f, 0.35f, 0.58f, 0.65f),
            distanceMeters = 2.8f,
            motionState = MotionState.STATIONARY,
            pathTrajectory = PathTrajectory.IN_PATH_DIRECTLY
        )

        val assessments1 = engine.evaluateHazards(listOf(obstacle), time)
        val firstAssessment = assessments1.first()
        assertEquals(ThreatLevel.WARNING, firstAssessment.threatLevel)
        assertTrue(engine.shouldTriggerAlert(firstAssessment, assessments1, time))

        // Only 500ms later (well within 4.0s cooldown), obstacle begins approaching fast -> Escalates to Critical!
        val escalatedObstacle = createTracklet(
            id = 40L,
            itemClass = AssistiveClass.PEDESTRIAN_MOVING,
            box = BoundingBox(0.38f, 0.50f, 0.62f, 0.85f),
            distanceMeters = 1.3f,
            motionState = MotionState.APPROACHING,
            pathTrajectory = PathTrajectory.IN_PATH_DIRECTLY,
            ttc = 1.2f
        )

        val assessments2 = engine.evaluateHazards(listOf(escalatedObstacle), time + 500L)
        val escalatedAssessment = assessments2.first()
        assertEquals(ThreatLevel.CRITICAL, escalatedAssessment.threatLevel)

        // Must breakthrough cooldown immediately due to threat level jump!
        assertTrue("Threat escalation must bypass repetition cooldown",
            engine.shouldTriggerAlert(escalatedAssessment, assessments2, time + 500L))
    }

    @Test
    fun testClassSmoothingMajorityVoteAndConflictFallback() {
        val det = DetectedObstacle(AssistiveClass.PEDESTRIAN_MOVING, 0.9f, BoundingBox(0.4f, 0.4f, 0.6f, 0.6f))
        val track = ByteTracklet(55L, AssistiveClass.PEDESTRIAN_MOVING, det, 2.0f)

        // Simulate 7 frames of PEDESTRIAN_MOVING and 3 frames of BICYCLE_ESCOOTER
        for (i in 1..7) {
            track.classHistory.add(AssistiveClass.PEDESTRIAN_MOVING)
        }
        for (i in 1..3) {
            track.classHistory.add(AssistiveClass.BICYCLE_ESCOOTER)
        }

        // Majority (70%) is PEDESTRIAN_MOVING -> smoothedClass is PEDESTRIAN_MOVING
        assertEquals(AssistiveClass.PEDESTRIAN_MOVING, track.smoothedClass)

        // Now clear and simulate a tie/split vote (4 of A, 4 of B, 2 of C)
        track.classHistory.clear()
        for (i in 1..4) track.classHistory.add(AssistiveClass.CAR_VAN_TRUCK)
        for (i in 1..4) track.classHistory.add(AssistiveClass.POLE_BOLLARD)
        for (i in 1..2) track.classHistory.add(AssistiveClass.ANIMAL_DOG_CATTLE)

        // No majority >= 55% -> MUST fallback to GENERIC_OBSTACLE
        assertEquals(AssistiveClass.GENERIC_OBSTACLE, track.smoothedClass)
    }

    @Test
    fun testDistantObstacleBeyond3Point5MetersIsSilent() {
        val distantObstacle = createTracklet(
            id = 66L,
            itemClass = AssistiveClass.POLE_BOLLARD,
            box = BoundingBox(0.4f, 0.3f, 0.6f, 0.5f),
            distanceMeters = 4.2f, // > 3.5m
            motionState = MotionState.STATIONARY,
            pathTrajectory = PathTrajectory.IN_PATH_DIRECTLY
        )

        val assessments = engine.evaluateHazards(listOf(distantObstacle))
        assertEquals(1, assessments.size)
        // Must be ThreatLevel.NONE and silent
        assertEquals(ThreatLevel.NONE, assessments.first().threatLevel)
        assertEquals("", assessments.first().alertSpeech)
        assertFalse(engine.shouldTriggerAlert(assessments.first(), assessments))
    }
}

