package org.drishti.core.risk

import org.drishti.core.model.AssistiveClass
import org.drishti.core.model.DetectedObstacle
import org.drishti.core.model.DistanceTier
import org.drishti.core.model.SpatialZone
import org.drishti.core.model.ThreatLevel
import org.drishti.core.model.TrackedObstacle
import org.drishti.core.model.WalkingCorridor
import kotlin.math.roundToInt

/**
 * Real-Time Risk Assessment & Spatial Collision Engine for Drishti.
 * Evaluates obstacle proximity, ground-plane distance, walking corridor gating,
 * and handles deduplication with priority escalation.
 */
class RiskAssessmentEngine(
    var userLanguage: String = "hi",
    var cameraHeightMeters: Float = 1.35f, // Standard chest height
    var devicePitchDegrees: Float = 15.0f  // Default forward pitch angle
) {
    val depthEstimator = GroundContactDepthEstimator(
        cameraHeightMeters = cameraHeightMeters,
        defaultPitchDegrees = devicePitchDegrees
    )
    val spatialAnalyzer = SpatialEnvironmentAnalyzer(depthEstimator = depthEstimator)

    // Cooldown map: TrackId -> Last Announce Timestamp
    private val announcedCooldowns = mutableMapOf<Long, Long>()
    private val announcedThreatLevels = mutableMapOf<Long, ThreatLevel>()
    private val COOLDOWN_MS = 4000L // 4.0 seconds deduplication cooldown

    /**
     * Evaluate detected obstacles and produce prioritized, tracked obstacles with threat scoring.
     */
    fun evaluateObstacles(
        detections: List<DetectedObstacle>,
        currentPitch: Float = devicePitchDegrees
    ): List<TrackedObstacle> {
        return detections.mapIndexed { index, detection ->
            val trackId = (index + 1).toLong()

            // 1. Calculate Estimated Ground Distance using monocular depth estimator
            val depthResult = depthEstimator.estimateDistance(detection, currentPitch)
            val estimatedDistance = depthResult.distanceMeters

            // 2. Calculate Clock Position & Azimuth
            val (azimuth, clockPos) = calculateClockPosition(detection)

            // 3. Classify Spatial Zone
            val zone = spatialAnalyzer.corridor.classifyZone(detection.boundingBox)

            // 4. Determine Threat Level
            val threatLevel = evaluateThreatLevel(
                detection = detection,
                distance = estimatedDistance,
                azimuth = azimuth,
                isMoving = false,
                isApproaching = false,
                closingSpeed = 0f
            )

            TrackedObstacle(
                trackId = trackId,
                obstacle = detection,
                estimatedDistanceMeters = estimatedDistance,
                azimuthDegrees = azimuth,
                clockPosition = clockPos,
                timeToCollisionSeconds = estimatedDistance / 1.2f, // Assuming 1.2 m/s walk speed
                threatLevel = threatLevel,
                lastAnnouncedTimestampMs = announcedCooldowns[trackId] ?: 0L,
                spatialZone = zone,
                distanceTier = depthResult.distanceTier,
                isMetricCalibrated = depthResult.isMetricCalibrated,
                isMoving = false,
                isApproachingUser = false,
                relativeClosingSpeed = 0.0f
            )
        }.sortedByDescending { it.threatLevel.priority }
    }

    /**
     * Determines if a tracked obstacle should trigger an immediate spoken/haptic alert.
     */
    fun shouldAlert(tracked: TrackedObstacle): Boolean {
        if (tracked.threatLevel == ThreatLevel.NONE) return false

        val currentTime = System.currentTimeMillis()
        val lastAnnounced = announcedCooldowns[tracked.trackId] ?: 0L
        val lastThreat = announcedThreatLevels[tracked.trackId] ?: ThreatLevel.NONE

        // Immediate alert if threat escalated (e.g. from Caution to Critical)
        if (tracked.threatLevel.priority > lastThreat.priority) {
            recordAnnouncement(tracked.trackId, tracked.threatLevel, currentTime)
            return true
        }

        // Otherwise enforce 4.0s cooldown
        if (currentTime - lastAnnounced > COOLDOWN_MS) {
            recordAnnouncement(tracked.trackId, tracked.threatLevel, currentTime)
            return true
        }

        return false
    }

    /**
     * Generate concise, accessible spoken text for an obstacle in chosen language.
     */
    fun formatAlertSpeech(tracked: TrackedObstacle): String {
        val distRound = (tracked.estimatedDistanceMeters * 10).roundToInt() / 10f
        val itemClass = tracked.obstacle.assistiveClass

        val distTextHi = if (tracked.isMetricCalibrated) "$distRound meter" else tracked.distanceTier.hindiLabel
        val distTextEn = if (tracked.isMetricCalibrated) "$distRound meters" else tracked.distanceTier.englishLabel

        return if (userLanguage == "hi") {
            val directionText = when (tracked.spatialZone) {
                SpatialZone.CENTER -> "Samne"
                SpatialZone.LEFT -> "Baayein"
                SpatialZone.RIGHT -> "Daayein"
            }
            if (tracked.threatLevel == ThreatLevel.CRITICAL) {
                "Rukiye! $directionText $distTextHi par ${itemClass.hindiLabel} hai!"
            } else {
                "$directionText $distTextHi, ${itemClass.hindiLabel}."
            }
        } else {
            val directionText = when (tracked.spatialZone) {
                SpatialZone.CENTER -> "Ahead"
                SpatialZone.LEFT -> "On your left"
                SpatialZone.RIGHT -> "On your right"
            }
            val clockText = "${tracked.clockPosition} o'clock"
            if (tracked.threatLevel == ThreatLevel.CRITICAL) {
                "Stop! $directionText ($clockText), $distTextEn, ${itemClass.englishLabel}!"
            } else {
                "$directionText ($clockText), $distTextEn, ${itemClass.englishLabel}."
            }
        }
    }

    private fun calculateClockPosition(detection: DetectedObstacle): Pair<Float, Int> {
        val normalizedX = detection.boundingBox.centerX // 0.0 (left) to 1.0 (right)
        val hFov = 70.0 // Standard mobile camera HFOV
        val azimuth = ((normalizedX - 0.5f) * hFov).toFloat()

        val clock = when {
            azimuth in -15f..15f -> 12
            azimuth in 15f..45f -> 1
            azimuth in 45f..75f -> 2
            azimuth > 75f -> 3
            azimuth in -45f..-15f -> 11
            azimuth in -75f..-45f -> 10
            else -> 9
        }
        return Pair(azimuth, clock)
    }

    /**
     * Evaluates strict 2-Meter Collision & Motion Rule for haptic feedback.
     * Rule 1 — Static & Far (>2.0m) or Off-Path: NEVER vibrate.
     * Rule 2 — Approaching Dynamic Object <= 2.0m: VIBRATE.
     * Rule 3 — Imminent User Collision with Static Object <= 2.0m: VIBRATE only if in path and closing.
     */
    fun shouldVibrate(tracked: TrackedObstacle): Boolean {
        // Critical ground drop-offs or trenches warrant vibration within 2.5m
        if (tracked.obstacle.assistiveClass.category.isGroundTrap && tracked.estimatedDistanceMeters <= 2.5f) {
            return true
        }
        // Head-level hazard within 2.0m warrants immediate vibration
        if (tracked.obstacle.assistiveClass.category.isHeadHazard && tracked.estimatedDistanceMeters <= 2.0f) {
            return true
        }

        val inCentralCorridor = spatialAnalyzer.corridor.isInCentralCollisionCorridor(tracked.obstacle.boundingBox)

        // Rule 1: Static & Far (> 2.0m) or Off-Path = NO VIBRATION
        if (!tracked.isMoving && (tracked.estimatedDistanceMeters > 2.0f || !inCentralCorridor)) {
            return false
        }

        // Rule 2: Approaching Dynamic Object at <= 2.0m = VIBRATE
        if (tracked.isMoving && tracked.isApproachingUser && tracked.estimatedDistanceMeters <= 2.0f) {
            return true
        }

        // Rule 3: Imminent User Collision with Static Object at <= 2.0m = VIBRATE
        if (!tracked.isMoving && inCentralCorridor && tracked.estimatedDistanceMeters <= 2.0f && tracked.relativeClosingSpeed > 0.20f) {
            return true
        }

        return false
    }

    private fun evaluateThreatLevel(
        detection: DetectedObstacle,
        distance: Float,
        azimuth: Float,
        isMoving: Boolean = false,
        isApproaching: Boolean = false,
        closingSpeed: Float = 0.0f
    ): ThreatLevel {
        val isCentralCorridor = spatialAnalyzer.corridor.isInCentralCollisionCorridor(detection.boundingBox) || azimuth in -15f..15f
        val isGroundHazard = detection.assistiveClass.category.isGroundTrap
        val isHeadHazard = detection.assistiveClass.category.isHeadHazard

        // Rule 2: Approaching dynamic hazard <= 2.0m -> Immediate CRITICAL
        if (isMoving && isApproaching && distance <= 2.0f) {
            return ThreatLevel.CRITICAL
        }

        // Rule 3: Imminent collision with static object in central path <= 2.0m -> CRITICAL
        if (!isMoving && isCentralCorridor && distance <= 2.0f && closingSpeed > 0.20f) {
            return ThreatLevel.CRITICAL
        }

        // Drop-offs or head obstacles close in walking path -> Critical
        if (isCentralCorridor && (distance < 1.6f || (isGroundHazard && distance < 2.5f) || (isHeadHazard && distance < 2.0f))) {
            return ThreatLevel.CRITICAL
        }

        // Approaching hazards within 3.5m in corridor -> Caution
        if (isCentralCorridor && distance < 3.8f) {
            return ThreatLevel.CAUTION
        }

        // Rule 1: Static & Far (> 2.0m) or Off-Path -> ADVISORY / LOW (rendered, but never vibrate)
        if (!isMoving && (distance > 2.0f || !isCentralCorridor)) {
            return ThreatLevel.ADVISORY
        }

        // Peripheral or farther obstacles -> Advisory
        return if (distance < 6.0f) {
            ThreatLevel.ADVISORY
        } else {
            ThreatLevel.NONE
        }
    }

    private fun recordAnnouncement(trackId: Long, threat: ThreatLevel, timestamp: Long) {
        announcedCooldowns[trackId] = timestamp
        announcedThreatLevels[trackId] = threat
    }
}
