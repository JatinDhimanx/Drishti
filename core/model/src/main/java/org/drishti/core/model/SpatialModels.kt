package org.drishti.core.model

/**
 * Three-zone lateral spatial classification for pedestrian path planning.
 */
enum class SpatialZone(val displayName: String, val hindiLabel: String) {
    LEFT("Left", "Baayein"),
    CENTER("Center", "Samne"),
    RIGHT("Right", "Daayein")
}

/**
 * Discrete qualitative distance categories to prevent fabricated metric false-precision.
 */
enum class DistanceTier(
    val minDistanceMeters: Float,
    val maxDistanceMeters: Float,
    val englishLabel: String,
    val hindiLabel: String
) {
    VERY_NEAR(0.0f, 1.5f, "Very Close", "Bahut Paas"),
    NEAR(1.5f, 3.5f, "Close", "Paas"),
    MEDIUM(3.5f, 6.0f, "Medium Distance", "Thodi Door"),
    FAR(6.0f, 15.0f, "Ahead", "Door");

    companion object {
        fun fromDistance(meters: Float): DistanceTier {
            return when {
                meters < 1.5f -> VERY_NEAR
                meters < 3.5f -> NEAR
                meters < 6.0f -> MEDIUM
                else -> FAR
            }
        }
    }
}

/**
 * Surface type classification for walkable-area analysis.
 */
enum class SurfaceType(val englishLabel: String, val hindiLabel: String) {
    WALKABLE_FOOTPATH("Footpath", "Footpath"),
    ROAD_SURFACE("Road Surface", "Sadak"),
    UNEVEN_TERRAIN("Uneven Pavement", "Khabad-khabad zameen"),
    NON_WALKABLE("Non-walkable Area", "Chalney yogya nahi"),
    UNKNOWN("Unknown Surface", "Agyaat satah")
}

/**
 * Walking corridor boundaries defined in normalized frame coordinates [0.0, 1.0].
 * Dynamically adjusts for camera mounting yaw offset.
 */
data class WalkingCorridor(
    val leftBound: Float = 0.30f,
    val rightBound: Float = 0.70f,
    val yawOffsetDegrees: Float = 0.0f
) {
    val corridorWidth: Float get() = rightBound - leftBound
    val corridorCenter: Float get() = (leftBound + rightBound) / 2.0f

    /**
     * Checks if a point (normalized X) falls inside the walking corridor.
     */
    fun contains(x: Float): Boolean = x in leftBound..rightBound

    /**
     * Determines whether an obstacle bounding box overlaps with the active walking corridor.
     * Returns an overlap ratio from 0.0 (no overlap) to 1.0 (completely occupies corridor).
     */
    fun computeOverlap(box: BoundingBox): Float {
        val overlapLeft = maxOf(leftBound, box.left)
        val overlapRight = minOf(rightBound, box.right)
        val overlapWidth = maxOf(0f, overlapRight - overlapLeft)
        return (overlapWidth / corridorWidth).coerceIn(0f, 1f)
    }

    /**
     * Classifies a bounding box center into LEFT, CENTER, or RIGHT.
     */
    fun classifyZone(box: BoundingBox): SpatialZone {
        return when {
            box.centerX < leftBound -> SpatialZone.LEFT
            box.centerX > rightBound -> SpatialZone.RIGHT
            else -> SpatialZone.CENTER
        }
    }
}

/**
 * Corridor traversal status summary for speech and navigation guidance.
 */
enum class CorridorStatus(val englishLabel: String, val hindiLabel: String) {
    CLEAR("Corridor is clear", "Raasta saaf hai"),
    PARTIALLY_OBSTRUCTED("Partial obstruction ahead", "Aage rukaawat hai"),
    BLOCKED("Path is completely blocked", "Raasta poori tarah band hai")
}

/**
 * Detailed spatial analysis result for a single detected obstacle.
 */
data class SpatialObstacleAnalysis(
    val obstacle: DetectedObstacle,
    val zone: SpatialZone,
    val distanceTier: DistanceTier,
    val estimatedDistanceMeters: Float,
    val isMetricCalibrated: Boolean,
    val corridorOverlapRatio: Float,
    val groundContactY: Float
)

