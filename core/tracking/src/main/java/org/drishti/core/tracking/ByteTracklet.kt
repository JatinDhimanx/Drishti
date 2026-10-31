package org.drishti.core.tracking

import org.drishti.core.model.AssistiveClass
import org.drishti.core.model.BoundingBox
import org.drishti.core.model.DetectedObstacle
import org.drishti.core.model.MotionState
import org.drishti.core.model.ObjectVelocity
import org.drishti.core.model.PathTrajectory
import org.drishti.core.model.TrackPoint
import org.drishti.core.model.TrackState
import org.drishti.core.model.WalkingCorridor
import kotlin.math.abs

/**
 * A persistent tracklet representing an object tracked across multiple video frames.
 */
class ByteTracklet(
    val trackId: Long,
    var assistiveClass: AssistiveClass,
    initialDetection: DetectedObstacle,
    initialDistanceMeters: Float,
    var lastUpdateTimestampMs: Long = System.currentTimeMillis()
) {
    var state: TrackState = TrackState.NEW
    val predictor: KalmanBoxPredictor = KalmanBoxPredictor(initialDetection.boundingBox)

    val history = mutableListOf<TrackPoint>()
    private val MAX_HISTORY = 10

    var consecutiveHits: Int = 1
    var consecutiveMisses: Int = 0

    var currentDistanceMeters: Float = initialDistanceMeters
    var currentVelocity: ObjectVelocity = ObjectVelocity()
    var motionState: MotionState = MotionState.UNKNOWN
    var pathTrajectory: PathTrajectory = PathTrajectory.OUTSIDE_PATH
    var timeToCollisionSeconds: Float? = null

    val currentBox: BoundingBox get() = predictor.currentBox

    init {
        history.add(TrackPoint(initialDetection.boundingBox, initialDistanceMeters, lastUpdateTimestampMs))
    }

    /**
     * Advance Kalman prediction state to current frame timestamp.
     */
    fun predict(currentTimestampMs: Long): BoundingBox {
        val dtSeconds = ((currentTimestampMs - lastUpdateTimestampMs) / 1000f).coerceIn(0.016f, 0.3f)
        return predictor.predict(dtSeconds)
    }

    /**
     * Update tracklet with an associated detection.
     */
    fun update(
        detection: DetectedObstacle,
        estimatedDistanceMeters: Float,
        corridor: WalkingCorridor,
        currentTimestampMs: Long
    ) {
        val dtSeconds = ((currentTimestampMs - lastUpdateTimestampMs) / 1000f).coerceIn(0.016f, 0.5f)
        lastUpdateTimestampMs = currentTimestampMs

        // Update Kalman Box Predictor
        predictor.update(detection.boundingBox, dtSeconds)
        currentDistanceMeters = estimatedDistanceMeters
        assistiveClass = detection.assistiveClass

        consecutiveHits++
        consecutiveMisses = 0

        if (state == TrackState.NEW && consecutiveHits >= 2) {
            state = TrackState.TRACKED
        } else if (state == TrackState.LOST) {
            state = TrackState.TRACKED
        }

        // Add to history
        history.add(TrackPoint(predictor.currentBox, estimatedDistanceMeters, currentTimestampMs))
        if (history.size > MAX_HISTORY) {
            history.removeAt(0)
        }

        // Compute Velocity, Motion State, and Path Trajectory
        computeDynamics(corridor)
    }

    /**
     * Record a frame miss where no detection matched this track.
     */
    fun markMissed(maxMissesBeforeRemoval: Int = 20) {
        consecutiveMisses++
        consecutiveHits = 0

        if (consecutiveMisses > maxMissesBeforeRemoval) {
            state = TrackState.REMOVED
        } else if (consecutiveMisses > 1) {
            state = TrackState.LOST
        }
    }

    private fun computeDynamics(corridor: WalkingCorridor) {
        if (history.size < 2) return

        val oldest = history.first()
        val newest = history.last()
        val totalDt = ((newest.timestampMs - oldest.timestampMs) / 1000f).coerceAtLeast(0.05f)

        // 2D image plane velocity
        val deltaX = newest.boundingBox.centerX - oldest.boundingBox.centerX
        val deltaY = newest.boundingBox.centerY - oldest.boundingBox.centerY
        val deltaDist = newest.distanceMeters - oldest.distanceMeters

        val vx = deltaX / totalDt
        val vy = deltaY / totalDt
        val vDist = deltaDist / totalDt

        currentVelocity = ObjectVelocity(
            vxPixelsPerSec = vx,
            vyPixelsPerSec = vy,
            vDistanceMetersPerSec = vDist
        )

        // Motion state classification
        val isMovingFastLaterally = abs(vx) > 0.15f
        motionState = when {
            history.size < 3 -> MotionState.UNKNOWN
            currentVelocity.isStationary -> MotionState.STATIONARY
            vDist < -0.30f -> MotionState.APPROACHING
            vDist > 0.30f -> MotionState.RECEDING
            isMovingFastLaterally -> MotionState.CROSSING
            else -> MotionState.PARALLEL
        }

        // Path Trajectory Classification relative to Walking Corridor
        val box = newest.boundingBox
        val isInCorridor = corridor.contains(box.centerX) || corridor.computeOverlap(box) > 0.25f

        pathTrajectory = when {
            isInCorridor && (motionState == MotionState.APPROACHING || motionState == MotionState.STATIONARY) -> {
                PathTrajectory.IN_PATH_DIRECTLY
            }
            motionState == MotionState.CROSSING && (corridor.computeOverlap(box) > 0.10f || (box.left < corridor.rightBound && box.right > corridor.leftBound)) -> {
                PathTrajectory.CROSSING_PATH
            }
            !isInCorridor && (motionState == MotionState.APPROACHING || motionState == MotionState.RECEDING || motionState == MotionState.PARALLEL) -> {
                PathTrajectory.MOVING_PARALLEL
            }
            else -> {
                PathTrajectory.OUTSIDE_PATH
            }
        }

        // Time to Collision (TTC) in seconds
        timeToCollisionSeconds = if (vDist < -0.2f) {
            (currentDistanceMeters / abs(vDist)).coerceIn(0.5f, 30.0f)
        } else {
            // If stationary, assume standard pedestrian walking speed 1.2 m/s
            (currentDistanceMeters / 1.2f).coerceIn(0.5f, 30.0f)
        }
    }
}

