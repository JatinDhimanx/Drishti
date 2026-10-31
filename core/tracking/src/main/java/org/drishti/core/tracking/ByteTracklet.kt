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
 *
 * Implements multi-cue approach velocity detection:
 * - Bounding box area expansion rate (dA / dt)
 * - Ground-contact bottom edge movement (dY / dt)
 * - Monocular distance rate (dD / dt)
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

    // 10-Frame Majority Vote Class History
    val classHistory = mutableListOf<AssistiveClass>()
    private val MAX_CLASS_HISTORY = 10

    var consecutiveHits: Int = 1
    var consecutiveMisses: Int = 0

    var currentDistanceMeters: Float = initialDistanceMeters
    var currentVelocity: ObjectVelocity = ObjectVelocity()
    var motionState: MotionState = MotionState.UNKNOWN
    var pathTrajectory: PathTrajectory = PathTrajectory.OUTSIDE_PATH
    var timeToCollisionSeconds: Float? = null

    // Motion & 2-Meter Collision Tracking Metrics
    var isMoving: Boolean = false
    var isApproachingUser: Boolean = false
    var relativeClosingSpeed: Float = 0.0f

    // Dynamics metrics
    var areaExpansionRate: Float = 0f
        private set
    var bottomShiftRate: Float = 0f
        private set

    val currentBox: BoundingBox get() = predictor.currentBox

    /**
     * Majority vote class of the last 10 frames.
     * If votes conflict or class is not consistent, falls back to GENERIC_OBSTACLE.
     */
    val smoothedClass: AssistiveClass
        get() {
            if (classHistory.isEmpty()) return assistiveClass
            val votes = classHistory.groupingBy { it }.eachCount()
            val topEntry = votes.maxByOrNull { it.value } ?: return AssistiveClass.GENERIC_OBSTACLE
            val majorityRatio = topEntry.value.toFloat() / classHistory.size
            return if (majorityRatio >= 0.55f && !AssistiveClass.isBlacklisted(topEntry.key.technicalName)) {
                topEntry.key
            } else {
                AssistiveClass.GENERIC_OBSTACLE
            }
        }

    init {
        history.add(TrackPoint(initialDetection.boundingBox, initialDistanceMeters, lastUpdateTimestampMs))
        classHistory.add(initialDetection.assistiveClass)
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

        // Update Kalman Box Predictor with variable dt compensation
        predictor.update(detection.boundingBox, dtSeconds)
        currentDistanceMeters = estimatedDistanceMeters
        assistiveClass = detection.assistiveClass

        classHistory.add(detection.assistiveClass)
        if (classHistory.size > MAX_CLASS_HISTORY) {
            classHistory.removeAt(0)
        }

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

        // 1. 2D image plane velocity
        val deltaX = newest.boundingBox.centerX - oldest.boundingBox.centerX
        val deltaY = newest.boundingBox.centerY - oldest.boundingBox.centerY
        val deltaDist = newest.distanceMeters - oldest.distanceMeters

        // 2. Bounding box area expansion rate
        val oldArea = oldest.boundingBox.width * oldest.boundingBox.height
        val newArea = newest.boundingBox.width * newest.boundingBox.height
        areaExpansionRate = (newArea - oldArea) / totalDt

        // 3. Bottom edge downward shift (y increases downwards)
        val deltaBottomY = newest.boundingBox.bottom - oldest.boundingBox.bottom
        bottomShiftRate = deltaBottomY / totalDt

        val vx = deltaX / totalDt
        val vy = deltaY / totalDt
        val vDist = deltaDist / totalDt

        currentVelocity = ObjectVelocity(
            vxPixelsPerSec = vx,
            vyPixelsPerSec = vy,
            vDistanceMetersPerSec = vDist
        )

        // 4. Multi-cue Motion State Classification
        val isMovingFastLaterally = abs(vx) > 0.15f
        val isAreaExpanding = areaExpansionRate > 0.04f
        val isBottomDescending = bottomShiftRate > 0.03f
        val isDistanceClosing = vDist < -0.25f

        val isApproaching = (isDistanceClosing && (isAreaExpanding || isBottomDescending)) ||
                (isAreaExpanding && isBottomDescending) ||
                (vDist < -0.40f)

        val isReceding = (vDist > 0.30f) || (areaExpansionRate < -0.04f && bottomShiftRate < -0.03f)

        motionState = when {
            history.size < 3 -> MotionState.UNKNOWN
            isApproaching -> MotionState.APPROACHING
            isReceding -> MotionState.RECEDING
            currentVelocity.isStationary && abs(areaExpansionRate) < 0.03f -> MotionState.STATIONARY
            isMovingFastLaterally -> MotionState.CROSSING
            else -> MotionState.PARALLEL
        }

        // Relative Closing Speed (m/s): Rate at which distance is shrinking
        relativeClosingSpeed = if (vDist < 0f) abs(vDist) else 0.0f

        // 5. Path Trajectory Classification relative to Walking Corridor
        val box = newest.boundingBox
        val isInCorridor = corridor.contains(box.centerX, box.groundContactY) || corridor.computeOverlap(box) > 0.20f

        // isApproachingUser: expanding area or bottom-center ground contact descending toward frame bottom while in/entering walking path
        isApproachingUser = isApproaching || (isAreaExpanding && isInCorridor) || (isBottomDescending && isInCorridor) || (relativeClosingSpeed > 0.35f && isInCorridor)

        // isMoving: whether the object itself is in physical motion (not a stationary pole/wall/curb)
        val isDynamicClass = smoothedClass.category.isDynamicHazard || smoothedClass == AssistiveClass.PEDESTRIAN_MOVING
        val isInherentlyStatic = smoothedClass.category.isStaticBlocker || smoothedClass.category.isGroundTrap
        isMoving = when {
            isInherentlyStatic -> false
            isDynamicClass -> motionState != MotionState.STATIONARY || abs(vx) > 0.06f || relativeClosingSpeed > 1.35f
            else -> !currentVelocity.isStationary && (abs(vx) > 0.10f || motionState == MotionState.APPROACHING || motionState == MotionState.CROSSING)
        }

        pathTrajectory = when {
            isInCorridor && (motionState == MotionState.APPROACHING || motionState == MotionState.STATIONARY) -> {
                PathTrajectory.IN_PATH_DIRECTLY
            }
            motionState == MotionState.CROSSING && (corridor.computeOverlap(box) > 0.10f || (box.left < corridor.rightBoundAtY(box.groundContactY) && box.right > corridor.leftBoundAtY(box.groundContactY))) -> {
                PathTrajectory.CROSSING_PATH
            }
            !isInCorridor && (motionState == MotionState.APPROACHING || motionState == MotionState.RECEDING || motionState == MotionState.PARALLEL) -> {
                PathTrajectory.MOVING_PARALLEL
            }
            else -> {
                PathTrajectory.OUTSIDE_PATH
            }
        }

        // 6. Time to Collision (TTC) in seconds
        timeToCollisionSeconds = if (vDist < -0.2f) {
            (currentDistanceMeters / abs(vDist)).coerceIn(0.5f, 30.0f)
        } else if (isApproaching && areaExpansionRate > 0.05f) {
            // Estimate TTC from expansion rate
            (1.0f / areaExpansionRate.coerceAtLeast(0.05f)).coerceIn(0.5f, 30.0f)
        } else {
            // If stationary, assume standard pedestrian walking speed 1.2 m/s
            (currentDistanceMeters / 1.2f).coerceIn(0.5f, 30.0f)
        }
    }
}
