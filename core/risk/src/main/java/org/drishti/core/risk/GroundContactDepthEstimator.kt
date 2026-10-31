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
        if (category == AssistiveClass.HazardCategory.HEAD_LEVEL) {
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
        val computedDistance = (cameraHeightMeters / tan(angleRad)).toFloat().coerceIn(0.4f, 15.0f)

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

