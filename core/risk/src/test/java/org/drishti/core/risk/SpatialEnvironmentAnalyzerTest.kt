package org.drishti.core.risk

import org.drishti.core.model.AssistiveClass
import org.drishti.core.model.BoundingBox
import org.drishti.core.model.CorridorStatus
import org.drishti.core.model.DetectedObstacle
import org.drishti.core.model.DistanceTier
import org.drishti.core.model.SpatialZone
import org.drishti.core.model.SurfaceType
import org.drishti.core.model.WalkingCorridor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SpatialEnvironmentAnalyzerTest {

    private lateinit var depthEstimator: GroundContactDepthEstimator
    private lateinit var spatialAnalyzer: SpatialEnvironmentAnalyzer

    @Before
    fun setup() {
        depthEstimator = GroundContactDepthEstimator(
            cameraHeightMeters = 1.35f,
            defaultPitchDegrees = 15.0f,
            verticalFovDegrees = 60.0f
        )
        spatialAnalyzer = SpatialEnvironmentAnalyzer(
            depthEstimator = depthEstimator,
            corridor = WalkingCorridor(leftBound = 0.30f, rightBound = 0.70f)
        )
    }

    @Test
    fun testCorridorZoneClassification() {
        val corridor = WalkingCorridor(0.30f, 0.70f)

        val leftBox = BoundingBox(left = 0.05f, top = 0.4f, right = 0.25f, bottom = 0.8f)
        val centerBox = BoundingBox(left = 0.40f, top = 0.3f, right = 0.60f, bottom = 0.85f)
        val rightBox = BoundingBox(left = 0.75f, top = 0.4f, right = 0.95f, bottom = 0.8f)

        assertEquals(SpatialZone.LEFT, corridor.classifyZone(leftBox))
        assertEquals(SpatialZone.CENTER, corridor.classifyZone(centerBox))
        assertEquals(SpatialZone.RIGHT, corridor.classifyZone(rightBox))
    }

    @Test
    fun testCorridorOverlapComputation() {
        val corridor = WalkingCorridor(0.30f, 0.70f) // Width = 0.40f

        // Box occupying full width of corridor
        val fullSpanBox = BoundingBox(left = 0.25f, top = 0.2f, right = 0.75f, bottom = 0.9f)
        assertEquals(1.0f, corridor.computeOverlap(fullSpanBox), 0.01f)

        // Box occupying exactly half of corridor (0.30 to 0.50)
        val halfSpanBox = BoundingBox(left = 0.30f, top = 0.2f, right = 0.50f, bottom = 0.9f)
        assertEquals(0.5f, corridor.computeOverlap(halfSpanBox), 0.01f)

        // Box completely outside corridor on left
        val outsideLeftBox = BoundingBox(left = 0.05f, top = 0.2f, right = 0.25f, bottom = 0.9f)
        assertEquals(0.0f, corridor.computeOverlap(outsideLeftBox), 0.01f)
    }

    @Test
    fun testGroundContactDepthEstimator() {
        // Obstacle with ground contact near bottom of screen (close to user's feet)
        val closeObstacle = DetectedObstacle(
            assistiveClass = AssistiveClass.POTHOLE_TRENCH,
            confidence = 0.92f,
            boundingBox = BoundingBox(0.4f, 0.6f, 0.6f, 0.95f) // bottom = 0.95
        )

        val closeResult = depthEstimator.estimateDistance(closeObstacle, currentPitchDegrees = 15.0f)
        assertTrue("Distance should be very close (< 2.0m)", closeResult.distanceMeters < 2.0f)
        assertEquals(DistanceTier.VERY_NEAR, closeResult.distanceTier)
        assertTrue(closeResult.isMetricCalibrated)

        // Obstacle with ground contact near center/horizon
        val farObstacle = DetectedObstacle(
            assistiveClass = AssistiveClass.CAR_VAN_TRUCK,
            confidence = 0.88f,
            boundingBox = BoundingBox(0.4f, 0.35f, 0.6f, 0.52f) // bottom = 0.52
        )

        val farResult = depthEstimator.estimateDistance(farObstacle, currentPitchDegrees = 15.0f)
        assertTrue("Distance should be farther (> 4.0m)", farResult.distanceMeters > 4.0f)
        assertTrue(farResult.distanceTier == DistanceTier.MEDIUM || farResult.distanceTier == DistanceTier.FAR)
    }

    @Test
    fun testHeadLevelHazardHandling() {
        // Head-level hazard: overhanging branch
        val branchObstacle = DetectedObstacle(
            assistiveClass = AssistiveClass.OVERHANGING_BRANCH,
            confidence = 0.85f,
            boundingBox = BoundingBox(0.3f, 0.1f, 0.7f, 0.45f) // top of frame
        )

        val branchResult = depthEstimator.estimateDistance(branchObstacle)
        // Should NOT use ground contact trigonometry and should not claim metric calibration
        assertFalse("Head hazards must not claim ground-calibrated metric precision", branchResult.isMetricCalibrated)
        assertTrue("Must be classified into a valid distance tier", branchResult.distanceTier in DistanceTier.values())
    }

    @Test
    fun testSpatialEnvironmentBlockedCorridor() {
        // Critical ground hazard right in the center walking path
        val pothole = DetectedObstacle(
            assistiveClass = AssistiveClass.POTHOLE_TRENCH,
            confidence = 0.95f,
            boundingBox = BoundingBox(0.35f, 0.65f, 0.65f, 0.92f)
        )

        val summary = spatialAnalyzer.analyzeEnvironment(listOf(pothole))
        assertEquals(CorridorStatus.BLOCKED, summary.corridorStatus)
        assertEquals(SpatialEnvironmentAnalyzer.DetourRecommendation.STOP_IMMEDIATELY, summary.recommendedDetour)
        assertEquals(1, summary.criticalGroundHazards.size)
        assertEquals(SurfaceType.UNEVEN_TERRAIN, summary.detectedSurface)
    }

    @Test
    fun testSpatialEnvironmentClearCorridor() {
        // No obstacles
        val summary = spatialAnalyzer.analyzeEnvironment(emptyList())
        assertEquals(CorridorStatus.CLEAR, summary.corridorStatus)
        assertEquals(SpatialEnvironmentAnalyzer.DetourRecommendation.MAINTAIN_COURSE, summary.recommendedDetour)
        assertEquals(0, summary.criticalGroundHazards.size)
    }

    @Test
    fun testSpatialEnvironmentDetourRightWhenLeftIsObstructed() {
        // Obstacle on left side of corridor
        val leftObstacle = DetectedObstacle(
            assistiveClass = AssistiveClass.BOLLARD,
            confidence = 0.9f,
            boundingBox = BoundingBox(0.20f, 0.45f, 0.45f, 0.80f)
        )

        val summary = spatialAnalyzer.analyzeEnvironment(listOf(leftObstacle))
        assertEquals(CorridorStatus.PARTIALLY_OBSTRUCTED, summary.corridorStatus)
        assertEquals(SpatialEnvironmentAnalyzer.DetourRecommendation.VEER_RIGHT, summary.recommendedDetour)
    }

    @Test
    fun testSurfaceInferenceWithFootpathObjects() {
        val curb = DetectedObstacle(
            assistiveClass = AssistiveClass.CURB_STEP_UP,
            confidence = 0.91f,
            boundingBox = BoundingBox(0.35f, 0.7f, 0.65f, 0.95f)
        )

        val summary = spatialAnalyzer.analyzeEnvironment(listOf(curb))
        assertEquals(SurfaceType.WALKABLE_FOOTPATH, summary.detectedSurface)
    }
}

