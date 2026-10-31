package org.drishti.core.risk

import org.drishti.core.model.AssistiveClass
import org.drishti.core.model.CorridorStatus
import org.drishti.core.model.DetectedObstacle
import org.drishti.core.model.DistanceTier
import org.drishti.core.model.SpatialObstacleAnalysis
import org.drishti.core.model.SpatialZone
import org.drishti.core.model.SurfaceType
import org.drishti.core.model.WalkingCorridor

/**
 * Complete Spatial Environment and Walking Corridor Analyzer for Drishti.
 * Analyzes three-zone layout (Left, Center, Right), ground contact,
 * surface walkability, and corridor obstruction states.
 */
class SpatialEnvironmentAnalyzer(
    val depthEstimator: GroundContactDepthEstimator = GroundContactDepthEstimator(),
    var corridor: WalkingCorridor = WalkingCorridor()
) {

    data class EnvironmentSummary(
        val corridorStatus: CorridorStatus,
        val recommendedDetour: DetourRecommendation,
        val detectedSurface: SurfaceType,
        val analyses: List<SpatialObstacleAnalysis>,
        val criticalGroundHazards: List<SpatialObstacleAnalysis>
    )

    enum class DetourRecommendation(val english: String, val hindi: String) {
        MAINTAIN_COURSE("Walk straight", "Seedhe chaliye"),
        VEER_LEFT("Step slightly left", "Thoda baayein chaliye"),
        VEER_RIGHT("Step slightly right", "Thoda daayein chaliye"),
        STOP_IMMEDIATELY("Stop immediately", "Turant rukiye")
    }

    /**
     * Performs full spatial analysis on a list of raw detections.
     */
    fun analyzeEnvironment(
        detections: List<DetectedObstacle>,
        currentPitchDegrees: Float = depthEstimator.defaultPitchDegrees,
        yawOffsetDegrees: Float = 0.0f
    ): EnvironmentSummary {
        // Dynamically adjust corridor if user has a yaw offset
        val activeCorridor = if (yawOffsetDegrees != 0.0f) {
            val shift = (yawOffsetDegrees / 60.0f).coerceIn(-0.2f, 0.2f)
            WalkingCorridor(
                leftBound = (corridor.leftBound + shift).coerceIn(0.1f, 0.4f),
                rightBound = (corridor.rightBound + shift).coerceIn(0.6f, 0.9f),
                yawOffsetDegrees = yawOffsetDegrees
            )
        } else {
            corridor
        }

        val analyses = mutableListOf<SpatialObstacleAnalysis>()
        val criticalGroundHazards = mutableListOf<SpatialObstacleAnalysis>()

        for (detection in detections) {
            val depth = depthEstimator.estimateDistance(detection, currentPitchDegrees)
            val zone = activeCorridor.classifyZone(detection.boundingBox)
            val overlap = activeCorridor.computeOverlap(detection.boundingBox)

            val analysis = SpatialObstacleAnalysis(
                obstacle = detection,
                zone = zone,
                distanceTier = depth.distanceTier,
                estimatedDistanceMeters = depth.distanceMeters,
                isMetricCalibrated = depth.isMetricCalibrated,
                corridorOverlapRatio = overlap,
                groundContactY = detection.boundingBox.groundContactY
            )
            analyses.add(analysis)

            // Ground-level hazard identification
            if (detection.assistiveClass.category == AssistiveClass.HazardCategory.GROUND_HAZARD) {
                if (depth.distanceTier == DistanceTier.VERY_NEAR || depth.distanceTier == DistanceTier.NEAR) {
                    criticalGroundHazards.add(analysis)
                }
            }
        }

        // Evaluate corridor obstruction
        val inCorridorObstacles = analyses.filter {
            it.zone == SpatialZone.CENTER || it.corridorOverlapRatio > 0.25f
        }

        val veryNearInCorridor = inCorridorObstacles.filter { it.distanceTier == DistanceTier.VERY_NEAR }
        val nearInCorridor = inCorridorObstacles.filter { it.distanceTier == DistanceTier.NEAR }

        val status: CorridorStatus
        val detour: DetourRecommendation

        when {
            criticalGroundHazards.isNotEmpty() || veryNearInCorridor.any { it.corridorOverlapRatio > 0.45f } -> {
                status = CorridorStatus.BLOCKED
                detour = DetourRecommendation.STOP_IMMEDIATELY
            }
            veryNearInCorridor.isNotEmpty() || nearInCorridor.any { it.corridorOverlapRatio > 0.20f } -> {
                status = CorridorStatus.PARTIALLY_OBSTRUCTED
                // Determine whether left or right side has more clearance
                val leftObstructions = analyses.count {
                    it.zone == SpatialZone.LEFT ||
                    (it.zone == SpatialZone.CENTER && it.obstacle.boundingBox.centerX < activeCorridor.corridorCenter)
                }
                val rightObstructions = analyses.count {
                    it.zone == SpatialZone.RIGHT ||
                    (it.zone == SpatialZone.CENTER && it.obstacle.boundingBox.centerX >= activeCorridor.corridorCenter)
                }
                detour = if (leftObstructions > rightObstructions) {
                    DetourRecommendation.VEER_RIGHT
                } else {
                    DetourRecommendation.VEER_LEFT
                }
            }
            nearInCorridor.isNotEmpty() -> {
                status = CorridorStatus.PARTIALLY_OBSTRUCTED
                detour = DetourRecommendation.MAINTAIN_COURSE
            }
            else -> {
                status = CorridorStatus.CLEAR
                detour = DetourRecommendation.MAINTAIN_COURSE
            }
        }

        // Infer surface type from detections (tactile paving, road vehicles, zebra crossings)
        val surface = inferSurfaceType(detections)

        return EnvironmentSummary(
            corridorStatus = status,
            recommendedDetour = detour,
            detectedSurface = surface,
            analyses = analyses,
            criticalGroundHazards = criticalGroundHazards
        )
    }

    /**
     * Differentiates between footpath, road surface, and uneven terrain based on detected contextual objects.
     */
    private fun inferSurfaceType(detections: List<DetectedObstacle>): SurfaceType {
        val classes = detections.map { it.assistiveClass }

        return when {
            classes.any { it == AssistiveClass.POTHOLE_TRENCH || it == AssistiveClass.PUDDLE_SLIPPERY } -> SurfaceType.UNEVEN_TERRAIN
            classes.any { it == AssistiveClass.CAR_VAN_TRUCK || it == AssistiveClass.MOTORCYCLE_RICKSHAW } -> SurfaceType.ROAD_SURFACE
            classes.contains(AssistiveClass.STAIRS_UPWARD) || classes.contains(AssistiveClass.STAIRS_DOWNWARD) ||
            classes.contains(AssistiveClass.CURB_STEP_UP) || classes.contains(AssistiveClass.CURB_STEP_DOWN) -> SurfaceType.WALKABLE_FOOTPATH
            else -> SurfaceType.WALKABLE_FOOTPATH
        }
    }
}

