package org.drishti.core.risk

import org.drishti.core.model.AssistiveClass
import org.drishti.core.model.BoundingBox
import org.drishti.core.model.DetectedObstacle
import org.drishti.core.model.DistanceTier
import kotlin.math.tan

/**
 * Robust Monocular Ground-Contact Distance Estimator.
 * Calculates physical distance to obstacles using calibrated pinhole camera geometry,
 * chest-mount height, and device inclination pitch angle.
 *
 * Prevents fabricated metric readings by validating geometric confidence.
 */
class GroundContactDepthEstimator(
    var cameraHeightMeters: Float = 1.35f,   // Standard adult chest mount height
    var defaultPitchDegrees: Float = 15.0f, // Typical downward inclination when chest-mounted
    val verticalFovDegrees: Float = 60.0f,  // Standard Android back camera VFOV
    val horizontalFovDegrees: Float = 70.0f // Standard Android back camera HFOV
) {

    data class DepthResult(
        val distanceMeters: Float,
        val distanceTier: DistanceTier,
        val isMetricCalibrated: Boolean,
        val confidenceScore: Float
    )

    /**
     * Estimates distance from the bottom contact edge of a detected obstacle.
     */
    fun estimateDistance(
        obstacle: DetectedObstacle,
        currentPitchDegrees: Float = defaultPitchDegrees
    ): DepthResult {
        val category = obstacle.assistiveClass.category
        val box = obstacle.boundingBox
        val groundContactY = box.groundContactY // 0.0 (top) to 1.0 (bottom)

        // Special handling for head-level hazards (not in ground contact)
        if (category.isHeadHazard) {
            return estimateHeadLevelDistance(box, obstacle.confidence)
        }

        // Geometric ground-plane projection:
        // Angular offset from camera optical center
        val angularOffsetDeg = (groundContactY - 0.5f) * verticalFovDegrees
        val totalDepressionAngleDeg = currentPitchDegrees + angularOffsetDeg

        // If depression angle is too flat (near horizon or skyward tilt), ground projection is invalid
        if (totalDepressionAngleDeg <= 3.0f) {
            return DepthResult(
                distanceMeters = 15.0f,
                distanceTier = DistanceTier.FAR,
                isMetricCalibrated = false,
                confidenceScore = 0.3f
            )
        }

        val angleRad = Math.toRadians(totalDepressionAngleDeg.toDouble())
        val computedGroundDistance = (cameraHeightMeters / tan(angleRad)).toFloat().coerceIn(0.4f, 15.0f)

        // Class height prior calibration (blends ground trigonometry with object height scaling)
        val heightPrior = getClassHeightPrior(obstacle.assistiveClass)
        val computedDistance = if (heightPrior != null && box.height > 0.05f) {
            val vFovRad = Math.toRadians(verticalFovDegrees.toDouble())
            // Estimated distance from focal length geometry: D = H_real / (2 * h_norm * tan(VFOV/2))
            val heightEstimatedDist = (heightPrior / (2f * box.height * tan(vFovRad / 2.0).toFloat())).coerceIn(0.5f, 12.0f)

            // Weight ground contact (70%) and height prior (30%) for high accuracy around 2.0m
            if (groundContactY >= 0.40f) {
                0.70f * computedGroundDistance + 0.30f * heightEstimatedDist
            } else {
                computedGroundDistance
            }
        } else {
            computedGroundDistance
        }

        // Metric calibration validity check:
        // Valid only if device pitch is in a reasonable walking orientation (0° to 35°)
        // and ground contact is in the lower 70% of the camera frame
        val isCalibrated = currentPitchDegrees in 0.0f..35.0f && groundContactY >= 0.35f
        val tier = DistanceTier.fromDistance(computedDistance)

        return DepthResult(
            distanceMeters = computedDistance,
            distanceTier = tier,
            isMetricCalibrated = isCalibrated,
            confidenceScore = if (isCalibrated) obstacle.confidence else obstacle.confidence * 0.6f
        )
    }

    private fun getClassHeightPrior(assistiveClass: AssistiveClass): Float? {
        return when (assistiveClass) {
            AssistiveClass.PEDESTRIAN_MOVING -> 1.70f
            AssistiveClass.CAR_VAN_TRUCK -> 1.55f
            AssistiveClass.MOTORCYCLE_RICKSHAW -> 1.25f
            AssistiveClass.BICYCLE_ESCOOTER -> 1.15f
            AssistiveClass.POLE_BOLLARD -> 1.00f
            AssistiveClass.ANIMAL_DOG_CATTLE -> 0.60f
            AssistiveClass.OVERHANGING_BRANCH, AssistiveClass.PROTRUDING_SIGNBOARD -> 1.80f
            else -> null
        }
    }

    /**
     * Head-level hazards (overhanging tree branches, low signboards, construction bars)
     * cannot use ground-contact trigonometry. We estimate distance using normalized box height and width.
     */
    private fun estimateHeadLevelDistance(box: BoundingBox, detectionConfidence: Float): DepthResult {
        val apparentSize = maxOf(box.width, box.height)
        val estimatedDist = when {
            apparentSize > 0.60f -> 1.0f  // Very close head hazard
            apparentSize > 0.35f -> 2.2f  // Close hazard
            apparentSize > 0.15f -> 4.5f  // Medium distance
            else -> 7.0f                  // Far
        }
        return DepthResult(
            distanceMeters = estimatedDist,
            distanceTier = DistanceTier.fromDistance(estimatedDist),
            isMetricCalibrated = false, // Never claim exact millimeter LiDAR precision for head hazards
            confidenceScore = detectionConfidence * 0.75f
        )
    }
}

