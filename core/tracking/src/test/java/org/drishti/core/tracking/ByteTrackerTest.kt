package org.drishti.core.tracking

import org.drishti.core.model.AssistiveClass
import org.drishti.core.model.BoundingBox
import org.drishti.core.model.DetectedObstacle
import org.drishti.core.model.MotionState
import org.drishti.core.model.PathTrajectory
import org.drishti.core.model.TrackState
import org.drishti.core.model.WalkingCorridor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ByteTrackerTest {

    private lateinit var tracker: ByteTracker
    private val corridor = WalkingCorridor(0.30f, 0.70f)

    @Before
    fun setup() {
        tracker = ByteTracker(
            highConfidenceThreshold = 0.50f,
            lowConfidenceThreshold = 0.20f,
            matchThreshold = 0.25f,
            maxLostFrames = 5
        )
    }

    @Test
    fun testTrackInitializationAndPersistence() {
        var time = 1000L

        // Frame 1: Detection appears
        val det1 = DetectedObstacle(
            assistiveClass = AssistiveClass.CAR_VAN_TRUCK,
            confidence = 0.85f,
            boundingBox = BoundingBox(0.40f, 0.30f, 0.60f, 0.50f)
        )
        val tracks1 = tracker.update(listOf(det1), { 8.0f }, corridor, time)
        assertEquals(1, tracks1.size)
        val initialId = tracks1.first().trackId

        // Frame 2: Same object slightly closer (0.1s later)
        time += 100L
        val det2 = DetectedObstacle(
            assistiveClass = AssistiveClass.CAR_VAN_TRUCK,
            confidence = 0.84f,
            boundingBox = BoundingBox(0.40f, 0.32f, 0.60f, 0.52f)
        )
        val tracks2 = tracker.update(listOf(det2), { 7.8f }, corridor, time)
        assertEquals(1, tracks2.size)
        assertEquals("Track ID must remain identical across frames", initialId, tracks2.first().trackId)
        assertEquals(TrackState.TRACKED, tracks2.first().state)
    }

    @Test
    fun testApproachingObjectMotionClassification() {
        var time = 1000L
        val corridor = WalkingCorridor(0.30f, 0.70f)

        // Simulate 4 frames of a vehicle approaching down the center corridor
        // Smooth frame-to-frame box growth at realistic video frame rate
        val distances = listOf(9.0f, 7.0f, 5.0f, 3.0f)
        val boxes = listOf(
            BoundingBox(0.44f, 0.25f, 0.56f, 0.42f),
            BoundingBox(0.42f, 0.30f, 0.58f, 0.50f),
            BoundingBox(0.40f, 0.36f, 0.60f, 0.60f),
            BoundingBox(0.38f, 0.44f, 0.62f, 0.72f)
        )

        var lastTracks: List<ByteTracklet> = emptyList()

        for (i in distances.indices) {
            val det = DetectedObstacle(
                assistiveClass = AssistiveClass.MOTORCYCLE_RICKSHAW,
                confidence = 0.88f,
                boundingBox = boxes[i]
            )
            lastTracks = tracker.update(listOf(det), { distances[i] }, corridor, time)
            time += 100L // 100ms per frame
        }

        assertEquals(1, lastTracks.size)
        val track = lastTracks.first()

        assertEquals("Should classify as approaching", MotionState.APPROACHING, track.motionState)
        assertEquals("Should classify as in-path directly", PathTrajectory.IN_PATH_DIRECTLY, track.pathTrajectory)
        assertNotNull(track.timeToCollisionSeconds)
        assertTrue("TTC should be computed and urgent (< 5.0s)", track.timeToCollisionSeconds!! < 5.0f)
    }

    @Test
    fun testStationaryObjectClassification() {
        var time = 1000L
        val staticBox = BoundingBox(0.75f, 0.30f, 0.90f, 0.80f) // Static pole on right

        var lastTracks: List<ByteTracklet> = emptyList()
        for (i in 0..4) {
            val det = DetectedObstacle(
                assistiveClass = AssistiveClass.POLE_BOLLARD,
                confidence = 0.80f,
                boundingBox = staticBox
            )
            lastTracks = tracker.update(listOf(det), { 3.5f }, corridor, time)
            time += 150L
        }

        val track = lastTracks.first()
        assertEquals(MotionState.STATIONARY, track.motionState)
    }

    @Test
    fun testByteTrackOcclusionRecoveryWithLowScoreDetection() {
        var time = 1000L

        // Frame 1: High confidence detection
        val det1 = DetectedObstacle(
            assistiveClass = AssistiveClass.PEDESTRIAN_MOVING,
            confidence = 0.85f,
            boundingBox = BoundingBox(0.40f, 0.30f, 0.55f, 0.70f)
        )
        val tracks1 = tracker.update(listOf(det1), { 5.0f }, corridor, time)
        val originalTrackId = tracks1.first().trackId

        // Frame 2: High confidence detection (Track confirmed)
        time += 100L
        val det2 = DetectedObstacle(
            assistiveClass = AssistiveClass.PEDESTRIAN_MOVING,
            confidence = 0.82f,
            boundingBox = BoundingBox(0.41f, 0.31f, 0.56f, 0.71f)
        )
        tracker.update(listOf(det2), { 4.8f }, corridor, time)

        // Frame 3: Sudden visual occlusion (e.g. shadow / partial obstruction)
        // Detection confidence drops below high threshold (0.35f, which is between 0.20 and 0.50)
        time += 100L
        val occludedDet = DetectedObstacle(
            assistiveClass = AssistiveClass.PEDESTRIAN_MOVING,
            confidence = 0.35f, // LOW CONFIDENCE! Standard tracker would discard and lose track
            boundingBox = BoundingBox(0.42f, 0.32f, 0.57f, 0.72f)
        )
        val recoveredTracks = tracker.update(listOf(occludedDet), { 4.6f }, corridor, time)

        // ByteTrack MUST recover this track via second-stage low-confidence matching!
        assertEquals("ByteTrack must recover occluded track", 1, recoveredTracks.size)
        assertEquals("Track ID must be preserved during occlusion recovery", originalTrackId, recoveredTracks.first().trackId)
    }

    @Test
    fun testTrackTimeoutAndRemoval() {
        var time = 1000L

        // Detection appears
        val det = DetectedObstacle(
            assistiveClass = AssistiveClass.BICYCLE_ESCOOTER,
            confidence = 0.80f,
            boundingBox = BoundingBox(0.10f, 0.20f, 0.25f, 0.60f)
        )
        tracker.update(listOf(det), { 6.0f }, corridor, time)

        // Target disappears (e.g. moved out of frame)
        // Update with empty detections for 10 frames (maxLostFrames is 5)
        var tracks: List<ByteTracklet> = emptyList()
        for (i in 0..7) {
            time += 100L
            tracks = tracker.update(emptyList(), { 6.0f }, corridor, time)
        }

        // Stale track must be removed
        assertTrue("Track must be removed after exceeding max lost frames", tracks.isEmpty())
    }
}

