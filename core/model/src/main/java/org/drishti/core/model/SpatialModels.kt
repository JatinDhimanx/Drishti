package org.drishti.core.model

/**
 * Three-zone lateral spatial classification for pedestrian path planning.
 */
enum class SpatialZone(val displayName: String, val hindiLabel: String, val clockHour: Int = 12) {
    LEFT("Left", "Baayein", 10),
    CENTER("Center", "Samne", 12),
    RIGHT("Right", "Daayein", 2)
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
 * Dynamically adjusts for camera mounting yaw offset and perspective trapezoid.
 */
data class WalkingCorridor(
    val leftBound: Float = 0.30f,
    val rightBound: Float = 0.70f,
    val topWidthRatio: Float = rightBound - leftBound,
    val bottomWidthRatio: Float = rightBound - leftBound,
    val yawOffsetDegrees: Float = 0.0f
) {
    val corridorWidth: Float get() = rightBound - leftBound
    val corridorCenter: Float get() = (leftBound + rightBound) / 2.0f

    fun leftBoundAtY(y: Float): Float {
        val clampedY = y.coerceIn(0f, 1f)
        val center = corridorCenter
        val halfWidth = (topWidthRatio + (bottomWidthRatio - topWidthRatio) * clampedY) / 2f
        return (center - halfWidth).coerceIn(0f, 1f)
    }

    fun rightBoundAtY(y: Float): Float {
        val clampedY = y.coerceIn(0f, 1f)
        val center = corridorCenter
        val halfWidth = (topWidthRatio + (bottomWidthRatio - topWidthRatio) * clampedY) / 2f
        return (center + halfWidth).coerceIn(0f, 1f)
    }

    /**
     * Checks if a point (normalized X) falls inside the walking corridor.
     */
    fun contains(x: Float): Boolean = contains(x, 0.8f)

    fun contains(x: Float, y: Float): Boolean = x in leftBoundAtY(y)..rightBoundAtY(y)

    /**
     * Determines whether an obstacle bounding box overlaps with the active walking corridor.
     * Returns an overlap ratio from 0.0 (no overlap) to 1.0 (completely occupies corridor).
     */
    fun computeOverlap(box: BoundingBox): Float {
        val left = leftBoundAtY(box.groundContactY)
        val right = rightBoundAtY(box.groundContactY)
        val width = maxOf(0.01f, right - left)
        val overlapLeft = maxOf(left, box.left)
        val overlapRight = minOf(right, box.right)
        val overlapWidth = maxOf(0f, overlapRight - overlapLeft)
        return (overlapWidth / width).coerceIn(0f, 1f)
    }

    /**
     * Checks if bounding box is in the central collision path (center 40%–60% of frame width).
     */
    fun isInCentralCollisionCorridor(box: BoundingBox): Boolean {
        val centralLeft = 0.40f
        val centralRight = 0.60f
        return box.centerX in centralLeft..centralRight || (box.left < centralRight && box.right > centralLeft)
    }

    /**
     * Classifies a bounding box center into LEFT, CENTER, or RIGHT.
     */
    fun classifyZone(box: BoundingBox): SpatialZone {
        val left = leftBoundAtY(box.groundContactY)
        val right = rightBoundAtY(box.groundContactY)
        return when {
            box.centerX < left -> SpatialZone.LEFT
            box.centerX > right -> SpatialZone.RIGHT
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

/**
 * Geometric hazard type identified from geometry, depth, and ground plane analysis.
 */
enum class GeometricHazardType {
    OBSTACLE,     // Protrusion above the ground plane in the walking corridor
    DROPOFF,      // Sudden depth drop-off or discontinuity (stairs down, open trench, curb)
    HEAD_HAZARD   // Obstruction in upper pedestrian volume (0.9m - 2.0m above ground)
}

/**
 * Height classification of a geometric obstacle relative to ground plane.
 */
enum class HeightClass {
    GROUND,   // Ground level / surface level (< 0.4m)
    WAIST,    // Waist / torso level (0.4m - 1.2m)
    HEAD      // Head / eye level (0.9m - 2.0m)
}

/**
 * Geometry-first detected hazard produced by Layer 1.
 */
data class GeometricHazard(
    val distanceMeters: Float,
    val azimuthDegrees: Float,
    val heightClass: HeightClass,
    val type: GeometricHazardType,
    val confidence: Float,
    val boundingBox: BoundingBox,
    val isPitchValid: Boolean = true
)
