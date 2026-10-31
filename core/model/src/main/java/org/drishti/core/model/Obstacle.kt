package org.drishti.core.model

data class DetectedObstacle(
    val assistiveClass: AssistiveClass,
    val confidence: Float,
    val boundingBox: BoundingBox,
    val timestampMs: Long = System.currentTimeMillis()
)

data class TrackedObstacle(
    val trackId: Long,
    val obstacle: DetectedObstacle,
    val estimatedDistanceMeters: Float,
    val azimuthDegrees: Float,
    val clockPosition: Int, // 1 to 12 o'clock
    val timeToCollisionSeconds: Float?,
    val threatLevel: ThreatLevel,
    val lastAnnouncedTimestampMs: Long = 0L,
    val spatialZone: SpatialZone = SpatialZone.CENTER,
    val distanceTier: DistanceTier = DistanceTier.fromDistance(estimatedDistanceMeters),
    val isMetricCalibrated: Boolean = true,
    val isMoving: Boolean = false,
    val isApproachingUser: Boolean = false,
    val relativeClosingSpeed: Float = 0.0f
)
