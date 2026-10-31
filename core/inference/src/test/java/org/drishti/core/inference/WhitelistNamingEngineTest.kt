package org.drishti.core.inference

import org.drishti.core.model.AssistiveClass
import org.drishti.core.model.BoundingBox
import org.drishti.core.model.DetectedObstacle
import org.drishti.core.model.GeometricHazard
import org.drishti.core.model.GeometricHazardType
import org.drishti.core.model.HeightClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WhitelistNamingEngineTest {

    private lateinit var namingEngine: WhitelistNamingEngine

    @Before
    fun setup() {
        namingEngine = WhitelistNamingEngine()
    }

    private fun createGeoObstacle(
        dist: Float = 1.5f,
        azimuth: Float = 0.0f,
        type: GeometricHazardType = GeometricHazardType.OBSTACLE
    ): GeometricHazard {
        return GeometricHazard(
            distanceMeters = dist,
            azimuthDegrees = azimuth,
            heightClass = HeightClass.WAIST,
            type = type,
            confidence = 0.85f,
            boundingBox = BoundingBox(0.40f, 0.35f, 0.60f, 0.65f),
            isPitchValid = true
        )
    }

    @Test
    fun testTemporalConfirmationRequiresAtLeastThreeFrames() {
        val geo = createGeoObstacle()
        val det = DetectedObstacle(
            assistiveClass = AssistiveClass.POLE_BOLLARD,
            confidence = 0.80f,
            boundingBox = geo.boundingBox
        )

        // Frame 1: Only 1 hit -> should not trigger alert yet
        val out1 = namingEngine.fuseAndValidate(listOf(geo), listOf(det), currentTimestampMs = 1000L)
        assertTrue("Single-frame detection must not produce alert", out1.isEmpty())

        // Frame 2: 2 hits -> still waiting for 3rd hit
        val out2 = namingEngine.fuseAndValidate(listOf(geo), listOf(det), currentTimestampMs = 1100L)
        assertTrue("Two-frame detection must not produce alert for non-critical hazard", out2.isEmpty())

        // Frame 3: 3 hits -> Confirmed!
        val out3 = namingEngine.fuseAndValidate(listOf(geo), listOf(det), currentTimestampMs = 1200L)
        assertEquals(1, out3.size)
        assertEquals(AssistiveClass.POLE_BOLLARD, out3.first().assistiveClass)
    }

    @Test
    fun testCriticalDropOffConfirmedInTwoFrames() {
        val dropOffGeo = createGeoObstacle(dist = 1.2f, type = GeometricHazardType.DROPOFF)
        val dropOffDet = DetectedObstacle(
            assistiveClass = AssistiveClass.POTHOLE_OPEN_TRENCH,
            confidence = 0.70f,
            boundingBox = dropOffGeo.boundingBox
        )

        // Frame 1
        val out1 = namingEngine.fuseAndValidate(listOf(dropOffGeo), listOf(dropOffDet), currentTimestampMs = 1000L)
        assertTrue(out1.isEmpty())

        // Frame 2: Critical hazard confirmed in 2 of 3 frames!
        val out2 = namingEngine.fuseAndValidate(listOf(dropOffGeo), listOf(dropOffDet), currentTimestampMs = 1100L)
        assertEquals(1, out2.size)
        assertEquals(AssistiveClass.POTHOLE_OPEN_TRENCH, out2.first().assistiveClass)
    }

    @Test
    fun testIndoorItemNeverAnnouncedAndFallsBackToGenericObstacle() {
        val geo = createGeoObstacle(dist = 1.8f)

        // Simulate an unwhitelisted / generic item (e.g. laptop or table in front of camera)
        // If detector has no whitelist match or candidate was blacklisted
        for (f in 1..4) {
            val confirmed = namingEngine.fuseAndValidate(
                geometricHazards = listOf(geo),
                rawDetections = emptyList(), // No valid whitelist detection
                currentTimestampMs = 1000L + f * 100L
            )
            if (f >= 3) {
                assertEquals(1, confirmed.size)
                // MUST fall back to generic obstacle, NEVER guess a name like 'table'
                assertEquals(AssistiveClass.GENERIC_OBSTACLE, confirmed.first().assistiveClass)
                assertNotEquals("table", confirmed.first().assistiveClass.technicalName)
            }
        }
    }

    @Test
    fun testLowConfidenceBelowCalibratedThresholdFallsBackToGeneric() {
        val geo = createGeoObstacle(dist = 2.0f)
        // Static object threshold is 0.65; this detection has 0.40 (too low)
        val lowConfDet = DetectedObstacle(
            assistiveClass = AssistiveClass.POLE_BOLLARD,
            confidence = 0.40f,
            boundingBox = geo.boundingBox
        )

        for (f in 1..4) {
            val confirmed = namingEngine.fuseAndValidate(
                geometricHazards = listOf(geo),
                rawDetections = listOf(lowConfDet),
                currentTimestampMs = 1000L + f * 100L
            )
            if (f >= 3) {
                assertEquals(1, confirmed.size)
                // Low confidence pole discarded -> falls back to GENERIC_OBSTACLE
                assertEquals(AssistiveClass.GENERIC_OBSTACLE, confirmed.first().assistiveClass)
            }
        }
    }
}

