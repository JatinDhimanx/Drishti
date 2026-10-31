package org.drishti.core.vision

import org.drishti.core.model.GeometricHazardType
import org.drishti.core.model.HeightClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GeometryObstacleEngineTest {

    private lateinit var engine: GeometryObstacleEngine
    private val width = 16
    private val height = 16

    @Before
    fun setup() {
        engine = GeometryObstacleEngine(
            cameraHeightMeters = 1.35f,
            horizontalFovDegrees = 70.0f,
            verticalFovDegrees = 60.0f
        )
    }

    private fun createFlatGroundGrid(pitchDeg: Float = 15.0f): FloatArray {
        val depths = FloatArray(width * height)
        val pitchRad = Math.toRadians(pitchDeg.toDouble())
        for (y in 0 until height) {
            val normY = (y + 0.5f) / height
            val vertAngleDeg = (normY - 0.5f) * 60.0f
            val totalDep = pitchRad + Math.toRadians(vertAngleDeg.toDouble())
            val sinDep = Math.sin(totalDep)
            val dist = if (sinDep > 0.05) (1.35 / sinDep).toFloat().coerceIn(0.5f, 10.0f) else 10.0f
            for (x in 0 until width) {
                depths[y * width + x] = dist
            }
        }
        return depths
    }

    @Test
    fun testClearWalkableGroundProducesZeroHazards() {
        val flatDepths = createFlatGroundGrid(pitchDeg = 15.0f)
        val depthGrid = GeometryObstacleEngine.DepthGrid(width, height, flatDepths)
        val segMask = ByteArray(width * height) { GeometryObstacleEngine.SegmentationGrid.CLASS_WALKABLE_GROUND }
        val segGrid = GeometryObstacleEngine.SegmentationGrid(width, height, segMask)

        val hazards = engine.detectGeometricHazards(depthGrid, segGrid, devicePitchDegrees = 15.0f)
        assertTrue("Clear path must produce ZERO alerts (no hallucinated obstacles)", hazards.isEmpty())
    }

    @Test
    fun testProtrusionInCenterCorridorYieldsObstacle() {
        val flatDepths = createFlatGroundGrid(pitchDeg = 15.0f)
        // Insert a 1.8m obstacle cluster right in the center corridor (x=7..9, y=9..11)
        for (y in 9..11) {
            for (x in 7..9) {
                flatDepths[y * width + x] = 1.8f // 1.8 meters ahead
            }
        }
        val depthGrid = GeometryObstacleEngine.DepthGrid(width, height, flatDepths)
        val segMask = ByteArray(width * height) { GeometryObstacleEngine.SegmentationGrid.CLASS_WALKABLE_GROUND }
        // Mark the obstacle cells
        for (y in 9..11) {
            for (x in 7..9) {
                segMask[y * width + x] = GeometryObstacleEngine.SegmentationGrid.CLASS_NON_WALKABLE
            }
        }
        val segGrid = GeometryObstacleEngine.SegmentationGrid(width, height, segMask)

        val hazards = engine.detectGeometricHazards(depthGrid, segGrid, devicePitchDegrees = 15.0f)
        assertEquals(1, hazards.size)
        val hazard = hazards.first()
        assertEquals(GeometricHazardType.OBSTACLE, hazard.type)
        assertEquals(1.8f, hazard.distanceMeters, 0.3f)
        assertTrue("Hazard should be in center corridor", Math.abs(hazard.azimuthDegrees) <= 15.0f)
        assertTrue(hazard.isPitchValid)
    }

    @Test
    fun testDropOffDiscontinuityYieldsDropOffHazard() {
        val depths = createFlatGroundGrid(pitchDeg = 15.0f)
        // Create sudden deep hole / drop in bottom-center frame (y=11..14, x=7..9)
        for (y in 11..14) {
            for (x in 7..9) {
                depths[y * width + x] = 5.0f // Farther than expected ground (open pit / downward stairs)
            }
        }
        val depthGrid = GeometryObstacleEngine.DepthGrid(width, height, depths)
        val segMask = ByteArray(width * height) {
            if (it in listOf(11*16+7, 11*16+8, 12*16+7, 12*16+8, 13*16+7, 13*16+8)) {
                GeometryObstacleEngine.SegmentationGrid.CLASS_DROP_OFF
            } else {
                GeometryObstacleEngine.SegmentationGrid.CLASS_WALKABLE_GROUND
            }
        }
        val segGrid = GeometryObstacleEngine.SegmentationGrid(width, height, segMask)

        val hazards = engine.detectGeometricHazards(depthGrid, segGrid, devicePitchDegrees = 15.0f)
        assertTrue("Must detect drop-off hazard", hazards.any { it.type == GeometricHazardType.DROPOFF })
    }

    @Test
    fun testHeadHeightHazardDetection() {
        val depths = createFlatGroundGrid(pitchDeg = 15.0f)
        // Insert overhanging obstacle at eye/head height (top of frame y=1..3, x=6..9) at 2.0m distance
        for (y in 1..3) {
            for (x in 6..9) {
                depths[y * width + x] = 2.0f
            }
        }
        val depthGrid = GeometryObstacleEngine.DepthGrid(width, height, depths)
        val segMask = ByteArray(width * height) { GeometryObstacleEngine.SegmentationGrid.CLASS_NON_WALKABLE }
        val segGrid = GeometryObstacleEngine.SegmentationGrid(width, height, segMask)

        val hazards = engine.detectGeometricHazards(depthGrid, segGrid, devicePitchDegrees = 15.0f)
        assertTrue("Must detect head-height hazard", hazards.any { it.type == GeometricHazardType.HEAD_HAZARD })
    }

    @Test
    fun testPeripheralObstacleOutsideCorridorIsIgnored() {
        val depths = createFlatGroundGrid(pitchDeg = 15.0f)
        // Obstacle at extreme right (x=15, azimuth ~ +32°, outside corridor ±15°)
        for (y in 7..10) {
            depths[y * width + 15] = 1.2f
        }
        val depthGrid = GeometryObstacleEngine.DepthGrid(width, height, depths)
        val hazards = engine.detectGeometricHazards(depthGrid, null, devicePitchDegrees = 15.0f)
        assertTrue("Obstacle outside walking corridor must be ignored", hazards.isEmpty())
    }

    @Test
    fun testPitchProtectionFlagging() {
        val depths = createFlatGroundGrid(pitchDeg = 48.0f) // Extreme tilt (looking straight at feet)
        for (y in 7..9) {
            for (x in 7..9) {
                depths[y * width + x] = 1.0f
            }
        }
        val depthGrid = GeometryObstacleEngine.DepthGrid(width, height, depths)

        val hazards = engine.detectGeometricHazards(depthGrid, null, devicePitchDegrees = 48.0f)
        assertFalse("Device pitch > 40° must be marked invalid", engine.isPitchSafe(48.0f))
        if (hazards.isNotEmpty()) {
            assertFalse("Hazard pitch validity must be false", hazards.first().isPitchValid)
        }
    }
}

