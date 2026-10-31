package org.drishti.core.model

/**
 * Relative motion state of a tracked obstacle relative to the walking user.
 */
enum class MotionState(val englishLabel: String, val hindiLabel: String) {
    APPROACHING("Approaching", "Aapki taraf aa raha hai"),
    RECEDING("Moving away", "Door ja raha hai"),
    CROSSING("Crossing path", "Raasta cross kar raha hai"),
    PARALLEL("Moving alongside", "Saath chal raha hai"),
    STATIONARY("Stationary", "Ruka hua hai"),
    UNKNOWN("Calculating", "Jaanch jari")
}

/**
 * Spatial trajectory relative to the user's predicted walking path.
 */
enum class PathTrajectory(val englishLabel: String, val hindiLabel: String) {
    IN_PATH_DIRECTLY("Directly in walking path", "Seedhe aapke raaste mein"),
    CROSSING_PATH("Crossing walking path", "Raaste ko kaat raha hai"),
    MOVING_PARALLEL("Moving parallel to path", "Raaste ke barabar chal raha hai"),
    OUTSIDE_PATH("Outside walking corridor", "Raaste ke bahar hai")
}

/**
 * Estimated 2D image plane velocity and real-world approach speed.
 */
data class ObjectVelocity(
    val vxPixelsPerSec: Float = 0.0f,
    val vyPixelsPerSec: Float = 0.0f,
    val vDistanceMetersPerSec: Float = 0.0f
) {
    val speed2D: Float get() = kotlin.math.sqrt(vxPixelsPerSec * vxPixelsPerSec + vyPixelsPerSec * vyPixelsPerSec)
    val isApproaching: Boolean get() = vDistanceMetersPerSec < -0.25f // Distance decreasing
    val isReceding: Boolean get() = vDistanceMetersPerSec > 0.25f    // Distance increasing
    val isStationary: Boolean get() = speed2D < 0.08f && kotlin.math.abs(vDistanceMetersPerSec) < 0.25f
}

/**
 * Lifecyle state of a tracking target in the ByteTrack manager.
 */
enum class TrackState {
    NEW,
    TRACKED,
    LOST,
    REMOVED
}

/**
 * Historical observation point for smoothing and velocity calculation.
 */
data class TrackPoint(
    val boundingBox: BoundingBox,
    val distanceMeters: Float,
    val timestampMs: Long
)

