package org.drishti.core.tracking

import org.drishti.core.model.DetectedObstacle
import org.drishti.core.model.TrackState
import org.drishti.core.model.WalkingCorridor
import java.util.concurrent.atomic.AtomicLong

/**
 * ByteTrack Multi-Object Tracker Implementation for Drishti.
 *
 * Utilizes two-stage association (high-confidence and low-confidence detections)
 * to maintain persistent object identities through motion blur, scale changes,
 * and temporary visual occlusions without false ID switching.
 */
class ByteTracker(
    val highConfidenceThreshold: Float = 0.45f,
    val lowConfidenceThreshold: Float = 0.20f,
    val matchThreshold: Float = 0.25f,
    val maxLostFrames: Int = 25
) {
    private val nextTrackId = AtomicLong(1L)
    private val activeTracklets = mutableListOf<ByteTracklet>()

    /**
     * Updates tracker state with a new frame of detections.
     *
     * @param detections Raw detections from on-device AI detector.
     * @param distanceProvider Lambda computing monocular distance for a given detection.
     * @param corridor Active walking corridor for trajectory estimation.
     * @param timestampMs Frame timestamp.
     * @return List of currently confirmed, active tracklets.
     */
    fun update(
        detections: List<DetectedObstacle>,
        distanceProvider: (DetectedObstacle) -> Float,
        corridor: WalkingCorridor = WalkingCorridor(),
        timestampMs: Long = System.currentTimeMillis()
    ): List<ByteTracklet> {
        // Step 1: Predict positions for all active and lost tracklets
        val existingTracks = activeTracklets.filter { it.state != TrackState.REMOVED }
        for (track in existingTracks) {
            track.predict(timestampMs)
        }

        // Step 2: Partition detections by confidence
        val highDets = mutableListOf<DetectedObstacle>()
        val lowDets = mutableListOf<DetectedObstacle>()

        for (det in detections) {
            when {
                det.confidence >= highConfidenceThreshold -> highDets.add(det)
                det.confidence >= lowConfidenceThreshold -> lowDets.add(det)
            }
        }

        // Step 3: First Association (Active Tracks <-> High Confidence Detections)
        val trackedPool = existingTracks.filter { it.state == TrackState.TRACKED || it.state == TrackState.NEW }
        val (matched1, unmatchedTracks1, unmatchedHighDets) = associateDetections(
            trackedPool,
            highDets,
            matchThreshold
        )

        for ((track, det) in matched1) {
            val dist = distanceProvider(det)
            track.update(det, dist, corridor, timestampMs)
        }

        // Step 4: Second Association (Unmatched Tracks + Lost Tracks <-> Low Confidence Detections)
        // ByteTrack key feature: recovers occluded/blurred targets that experienced a dip in detection score
        val secondTrackPool = (unmatchedTracks1 + existingTracks.filter { it.state == TrackState.LOST }).distinctBy { it.trackId }
        val (matched2, unmatchedTracks2, _) = associateDetections(
            secondTrackPool,
            lowDets,
            matchThreshold * 0.8f // Slightly more permissive for recovering low-score boxes
        )

        for ((track, det) in matched2) {
            val dist = distanceProvider(det)
            track.update(det, dist, corridor, timestampMs)
        }

        // Step 5: Initialize New Tracks from Unmatched High-Confidence Detections
        for (newDet in unmatchedHighDets) {
            val dist = distanceProvider(newDet)
            val newTrack = ByteTracklet(
                trackId = nextTrackId.getAndIncrement(),
                assistiveClass = newDet.assistiveClass,
                initialDetection = newDet,
                initialDistanceMeters = dist,
                lastUpdateTimestampMs = timestampMs
            )
            activeTracklets.add(newTrack)
        }

        // Step 6: Mark Missed Tracks & Remove Stale Tracks
        for (unmatchedTrack in unmatchedTracks2) {
            unmatchedTrack.markMissed(maxLostFrames)
        }

        activeTracklets.removeAll { it.state == TrackState.REMOVED }

        // Step 7: Return Confirmed Tracks
        return activeTracklets.filter {
            it.state == TrackState.TRACKED || (it.state == TrackState.NEW && it.consecutiveHits >= 1)
        }
    }

    /**
     * Greedy Bipartite IoU matching between tracklets and detections.
     */
    private fun associateDetections(
        tracks: List<ByteTracklet>,
        dets: List<DetectedObstacle>,
        iouThreshold: Float
    ): AssociationResult {
        if (tracks.isEmpty() || dets.isEmpty()) {
            return AssociationResult(emptyList(), tracks, dets)
        }

        val matchedPairs = mutableListOf<Pair<ByteTracklet, DetectedObstacle>>()
        val matchedTracks = mutableSetOf<Long>()
        val matchedDets = mutableSetOf<Int>()

        // Compute Cost Matrix (IoU)
        data class MatchCandidate(val trackIndex: Int, val detIndex: Int, val iou: Float)
        val candidates = mutableListOf<MatchCandidate>()

        for (tIdx in tracks.indices) {
            val trackBox = tracks[tIdx].currentBox
            for (dIdx in dets.indices) {
                val detBox = dets[dIdx].boundingBox
                val iou = trackBox.iou(detBox)
                if (iou >= iouThreshold) {
                    candidates.add(MatchCandidate(tIdx, dIdx, iou))
                }
            }
        }

        // Sort by highest IoU first
        candidates.sortByDescending { it.iou }

        for (candidate in candidates) {
            val track = tracks[candidate.trackIndex]
            val det = dets[candidate.detIndex]

            if (track.trackId !in matchedTracks && candidate.detIndex !in matchedDets) {
                matchedTracks.add(track.trackId)
                matchedDets.add(candidate.detIndex)
                matchedPairs.add(track to det)
            }
        }

        val unmatchedTracks = tracks.filter { it.trackId !in matchedTracks }
        val unmatchedDets = dets.filterIndexed { index, _ -> index !in matchedDets }

        return AssociationResult(matchedPairs, unmatchedTracks, unmatchedDets)
    }

    private data class AssociationResult(
        val matched: List<Pair<ByteTracklet, DetectedObstacle>>,
        val unmatchedTracks: List<ByteTracklet>,
        val unmatchedDetections: List<DetectedObstacle>
    )

    /**
     * Resets all internal tracking state.
     */
    fun reset() {
        activeTracklets.clear()
        nextTrackId.set(1L)
    }
}

