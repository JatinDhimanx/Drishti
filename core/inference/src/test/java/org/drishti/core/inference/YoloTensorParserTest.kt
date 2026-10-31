package org.drishti.core.inference

import org.drishti.core.model.AssistiveClass
import org.drishti.core.model.BoundingBox
import org.drishti.core.model.DetectedObstacle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YoloTensorParserTest {

    @Test
    fun testIouCalculationIdenticalBoxes() {
        val boxA = BoundingBox(0.2f, 0.2f, 0.8f, 0.8f)
        val boxB = BoundingBox(0.2f, 0.2f, 0.8f, 0.8f)

        val iou = YoloTensorParser.calculateIou(boxA, boxB)
        assertEquals(1.0f, iou, 0.001f)
    }

    @Test
    fun testIouCalculationDisjointBoxes() {
        val boxA = BoundingBox(0.0f, 0.0f, 0.3f, 0.3f)
        val boxB = BoundingBox(0.6f, 0.6f, 0.9f, 0.9f)

        val iou = YoloTensorParser.calculateIou(boxA, boxB)
        assertEquals(0.0f, iou, 0.001f)
    }

    @Test
    fun testNmsSuppressionOfOverlappingDetections() {
        val obstacle1 = DetectedObstacle(
            assistiveClass = AssistiveClass.PEDESTRIAN_MOVING,
            confidence = 0.92f,
            boundingBox = BoundingBox(0.30f, 0.20f, 0.70f, 0.80f)
        )
        // Overlapping detection with lower confidence
        val obstacle2 = DetectedObstacle(
            assistiveClass = AssistiveClass.PEDESTRIAN_MOVING,
            confidence = 0.75f,
            boundingBox = BoundingBox(0.32f, 0.21f, 0.68f, 0.79f)
        )
        // Distinct non-overlapping detection
        val obstacle3 = DetectedObstacle(
            assistiveClass = AssistiveClass.BICYCLE_ESCOOTER,
            confidence = 0.80f,
            boundingBox = BoundingBox(0.05f, 0.40f, 0.25f, 0.75f)
        )

        val pruned = YoloTensorParser.applyNms(listOf(obstacle1, obstacle2, obstacle3), iouThreshold = 0.45f)
        assertEquals(2, pruned.size)
        assertTrue(pruned.contains(obstacle1))
        assertTrue(pruned.contains(obstacle3))
    }
}

