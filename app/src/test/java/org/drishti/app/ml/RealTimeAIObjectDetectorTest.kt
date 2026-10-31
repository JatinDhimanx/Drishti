package org.drishti.app.ml

import org.drishti.core.model.AssistiveClass
import org.drishti.core.model.BoundingBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RealTimeAIObjectDetectorTest {

    @Test
    fun testLaptopBoxDoesNotBecomeVehicleRegardlessOfSceneLabels() {
        val now = 1000L
        val rawBoxes = listOf(
            RealTimeAIObjectDetector.RawBox(
                box = BoundingBox(0.35f, 0.40f, 0.65f, 0.80f),
                label = "laptop",
                confidence = 0.85f
            )
        )

        // Extraction is based strictly on per-box labels:
        // Even if scene-level label is "Car" or "Tree", the box label "laptop" is blacklisted
        // so it must fall back to GENERIC_OBSTACLE and NEVER become CAR_VAN_TRUCK!
        val candidates = RealTimeAIObjectDetector.extractWhitelistCandidatesFromBoxes(rawBoxes, now)

        assertEquals(1, candidates.size)
        assertEquals(AssistiveClass.GENERIC_OBSTACLE, candidates[0].assistiveClass)
        assertNotEquals(AssistiveClass.CAR_VAN_TRUCK, candidates[0].assistiveClass)
    }

    @Test
    fun testCarBoxIsNotDroppedEvenIfSceneLabelIsFloorOrWall() {
        val now = 1000L
        val rawBoxes = listOf(
            RealTimeAIObjectDetector.RawBox(
                box = BoundingBox(0.20f, 0.30f, 0.80f, 0.90f),
                label = "car",
                confidence = 0.92f
            )
        )

        // The car box must be preserved as CAR_VAN_TRUCK.
        // Scene labels like "floor" or "tile" must NEVER veto or drop a box.
        val candidates = RealTimeAIObjectDetector.extractWhitelistCandidatesFromBoxes(rawBoxes, now)

        assertEquals(1, candidates.size)
        assertEquals(AssistiveClass.CAR_VAN_TRUCK, candidates[0].assistiveClass)
    }

    @Test
    fun testIndoorFurnitureBoxesBecomeGenericObstacle() {
        val now = 1000L
        val rawBoxes = listOf(
            RealTimeAIObjectDetector.RawBox(BoundingBox(0.1f, 0.1f, 0.3f, 0.3f), "chair", 0.80f),
            RealTimeAIObjectDetector.RawBox(BoundingBox(0.4f, 0.1f, 0.6f, 0.3f), "table", 0.75f),
            RealTimeAIObjectDetector.RawBox(BoundingBox(0.7f, 0.1f, 0.9f, 0.3f), "dining table", 0.90f),
            RealTimeAIObjectDetector.RawBox(BoundingBox(0.1f, 0.4f, 0.3f, 0.6f), "desk", 0.70f),
            RealTimeAIObjectDetector.RawBox(BoundingBox(0.4f, 0.4f, 0.6f, 0.6f), "tv", 0.88f)
        )

        val candidates = RealTimeAIObjectDetector.extractWhitelistCandidatesFromBoxes(rawBoxes, now)
        assertEquals(5, candidates.size)
        for (candidate in candidates) {
            assertEquals("Indoor furniture must map to GENERIC_OBSTACLE", AssistiveClass.GENERIC_OBSTACLE, candidate.assistiveClass)
        }
    }

    @Test
    fun testWhitelistedClassesPreserved() {
        val now = 1000L
        val rawBoxes = listOf(
            RealTimeAIObjectDetector.RawBox(BoundingBox(0.1f, 0.1f, 0.3f, 0.3f), "person", 0.85f),
            RealTimeAIObjectDetector.RawBox(BoundingBox(0.4f, 0.1f, 0.6f, 0.3f), "bicycle", 0.80f),
            RealTimeAIObjectDetector.RawBox(BoundingBox(0.7f, 0.1f, 0.9f, 0.3f), "motorcycle", 0.78f)
        )

        val candidates = RealTimeAIObjectDetector.extractWhitelistCandidatesFromBoxes(rawBoxes, now)
        assertEquals(3, candidates.size)
        assertEquals(AssistiveClass.PEDESTRIAN_MOVING, candidates[0].assistiveClass)
        assertEquals(AssistiveClass.BICYCLE_ESCOOTER, candidates[1].assistiveClass)
        assertEquals(AssistiveClass.MOTORCYCLE_RICKSHAW, candidates[2].assistiveClass)
    }

    @Test
    fun testUnlabelledBoxFallsBackToGenericObstacle() {
        val now = 1000L
        val rawBoxes = listOf(
            RealTimeAIObjectDetector.RawBox(BoundingBox(0.2f, 0.2f, 0.5f, 0.5f), "", 0.60f)
        )

        val candidates = RealTimeAIObjectDetector.extractWhitelistCandidatesFromBoxes(rawBoxes, now)
        assertEquals(1, candidates.size)
        assertEquals(AssistiveClass.GENERIC_OBSTACLE, candidates[0].assistiveClass)
    }
}

