package org.drishti.core.risk

import org.drishti.core.model.AssistiveClass
import org.drishti.core.model.DistanceTier
import org.drishti.core.model.MotionState
import org.drishti.core.model.PathTrajectory
import org.drishti.core.model.SpatialZone
import org.drishti.core.model.ThreatLevel
import org.drishti.core.model.WalkingCorridor
import org.drishti.core.tracking.ByteTracklet
import kotlin.math.roundToInt

/**
 * Intelligent Hazard Intelligence & Alert Suppression Engine for Drishti.
 *
 * Implements context-aware threat scoring, collision gating, dynamic alert ranking,
 * and ultra-short actionable speech alerts (<3-5 words: [Hazard] + [Distance/Direction] + [Action])
 * with a 4s cooldown on static objects and complete silence on clear paths.
 */
class HazardIntelligenceEngine(
    var userLanguage: String = "hi",
    var corridor: WalkingCorridor = WalkingCorridor()
) {
    // Cooldown maps: TrackId -> State
    private val announcedTimestamps = mutableMapOf<Long, Long>()
    private val announcedThreatLevels = mutableMapOf<Long, ThreatLevel>()
    private val announcedDistances = mutableMapOf<Long, Float>()

    data class HazardAssessment(
        val tracklet: ByteTracklet,
        val threatLevel: ThreatLevel,
        val threatScore: Float,
        val spatialZone: SpatialZone,
        val distanceTier: DistanceTier,
        val alertSpeech: String,
        val requiresImmediateHaptic: Boolean,
        val isApproachingDynamic2M: Boolean = false,
        val isImminentStaticCollision2M: Boolean = false
    )

    /**
     * Evaluates a collection of active ByteTracklets and returns prioritized, de-duplicated assessments.
     */
    fun evaluateHazards(
        tracklets: List<ByteTracklet>,
        currentTimestampMs: Long = System.currentTimeMillis()
    ): List<HazardAssessment> {
        val assessments = tracklets.map { track ->
            val score = calculateThreatScore(track)
            val level = scoreToThreatLevel(score, track)
            val zone = corridor.classifyZone(track.currentBox)
            val distTier = DistanceTier.fromDistance(track.currentDistanceMeters)
            val speech = formatActionableSpeech(track, level, zone, distTier)

            val inCentralCorridor = corridor.isInCentralCollisionCorridor(track.currentBox)
            val isWithin2M = track.currentDistanceMeters <= 2.0f

            // Rule 2: Approaching Dynamic Hazard <= 2.0m (Person, vehicle, dog closing in)
            val isApproachingDynamic2M = track.isMoving && track.isApproachingUser && isWithin2M

            // Rule 3: Imminent Static Collision <= 2.0m (In central walking path and user walking into it)
            val isImminentStaticCollision2M = !track.isMoving && inCentralCorridor && isWithin2M && track.relativeClosingSpeed > 0.20f

            // Ground drop-offs or head obstacles close in path
            val isGroundOrHeadCritical = (track.smoothedClass.category.isGroundTrap && track.currentDistanceMeters <= 2.5f) ||
                    (track.smoothedClass.category.isHeadHazard && isWithin2M)

            // Strict Haptic Gating:
            // Rule 1: Static & Far (>2.0m) or Off-Path -> NEVER vibrate (requiresImmediateHaptic = false)
            val requiresHaptic = when {
                isGroundOrHeadCritical -> true
                isApproachingDynamic2M -> true
                isImminentStaticCollision2M -> true
                !track.isMoving && (!isWithin2M || !inCentralCorridor) -> false // Rule 1
                level == ThreatLevel.CRITICAL -> true
                level == ThreatLevel.WARNING && inCentralCorridor -> true
                else -> false
            }

            HazardAssessment(
                tracklet = track,
                threatLevel = level,
                threatScore = score,
                spatialZone = zone,
                distanceTier = distTier,
                alertSpeech = speech,
                requiresImmediateHaptic = requiresHaptic,
                isApproachingDynamic2M = isApproachingDynamic2M,
                isImminentStaticCollision2M = isImminentStaticCollision2M
            )
        }.sortedByDescending { it.threatScore }

        return assessments
    }

    /**
     * Determines whether an assessment should produce an audible and haptic alert.
     * Enforces strict chatter suppression and priority preemption.
     */
    fun shouldTriggerAlert(
        assessment: HazardAssessment,
        allAssessments: List<HazardAssessment>,
        currentTimestampMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (assessment.threatLevel == ThreatLevel.NONE) return false
        if (assessment.alertSpeech.isBlank()) return false

        // Preemption Rule: If there is an active CRITICAL hazard, suppress all lower-priority alerts
        val hasActiveCritical = allAssessments.any { it.threatLevel == ThreatLevel.CRITICAL }
        if (hasActiveCritical && assessment.threatLevel != ThreatLevel.CRITICAL) {
            return false // Suppressed to keep audio channel free for urgent danger
        }

        val trackId = assessment.tracklet.trackId
        val lastAnnouncedTime = announcedTimestamps[trackId] ?: 0L
        val lastThreat = announcedThreatLevels[trackId] ?: ThreatLevel.NONE
        val lastDistance = announcedDistances[trackId] ?: Float.MAX_VALUE

        // Escalation Breakthrough: Immediate alert if threat escalated (e.g. Warning -> Critical)
        if (assessment.threatLevel.priority > lastThreat.priority) {
            recordAnnouncement(trackId, assessment.threatLevel, assessment.tracklet.currentDistanceMeters, currentTimestampMs)
            return true
        }

        // Distance delta breakthrough: If user or obstacle closed in by >= 0.6 meters, re-alert immediately
        val distanceDelta = lastDistance - assessment.tracklet.currentDistanceMeters
        if (distanceDelta >= 0.6f && assessment.threatLevel != ThreatLevel.ADVISORY) {
            recordAnnouncement(trackId, assessment.threatLevel, assessment.tracklet.currentDistanceMeters, currentTimestampMs)
            return true
        }

        // Cooldown: 4.0s for stationary obstacles, 2.0s for fast closing threats
        val cooldownMs = when (assessment.threatLevel) {
            ThreatLevel.CRITICAL -> 2000L
            ThreatLevel.WARNING -> 3500L
            ThreatLevel.CAUTION -> 4000L
            ThreatLevel.ADVISORY -> 5000L
            ThreatLevel.NONE -> Long.MAX_VALUE
        }

        if (currentTimestampMs - lastAnnouncedTime > cooldownMs) {
            recordAnnouncement(trackId, assessment.threatLevel, assessment.tracklet.currentDistanceMeters, currentTimestampMs)
            return true
        }

        return false
    }

    /**
     * Contextual Threat Scoring Model.
     */
    private fun calculateThreatScore(track: ByteTracklet): Float {
        val category = track.smoothedClass.category

        // 1. Base Hazard Weight
        val baseScore = when {
            category.isGroundTrap -> 95f
            category.isHeadHazard -> 90f
            category.isDynamicHazard -> 75f
            else -> 50f
        }

        // 2. Trajectory Multiplier
        val trajectoryMultiplier = when (track.pathTrajectory) {
            PathTrajectory.IN_PATH_DIRECTLY -> 1.45f
            PathTrajectory.CROSSING_PATH -> 1.25f
            PathTrajectory.MOVING_PARALLEL -> 0.65f
            PathTrajectory.OUTSIDE_PATH -> 0.20f
        }
        var score = baseScore * trajectoryMultiplier

        // 3. Proximity & Time to Collision (TTC) Bonuses
        val ttc = track.timeToCollisionSeconds
        val dist = track.currentDistanceMeters

        if (ttc != null && ttc < 2.5f && track.motionState == MotionState.APPROACHING) {
            score += 55f // Approaching fast on collision course
        } else if (dist < 1.5f) {
            score += 45f // Immediate stepping hazard
        } else if (dist < 3.5f) {
            score += 25f // Within walking range
        }

        // 4. Motion State Adjustments
        when (track.motionState) {
            MotionState.APPROACHING -> score += 25f
            MotionState.RECEDING -> score *= 0.20f // Moving away presents minimal risk
            MotionState.STATIONARY -> {
                if (track.pathTrajectory == PathTrajectory.OUTSIDE_PATH) score *= 0.15f
            }
            else -> {}
        }

        return score
    }

    private fun scoreToThreatLevel(score: Float, track: ByteTracklet): ThreatLevel {
        // Strict corridor & distance silence gating:
        // Objects moving away, or farther than 3.5m (unless fast closing vehicle), or outside corridor -> silent
        val isMovingAway = track.motionState == MotionState.RECEDING
        val isFar = track.currentDistanceMeters > 3.5f
        val isFastVehicle = (track.smoothedClass == AssistiveClass.CAR_VAN_TRUCK || track.smoothedClass == AssistiveClass.MOTORCYCLE_RICKSHAW) &&
                track.motionState == MotionState.APPROACHING &&
                (track.timeToCollisionSeconds ?: 10f) < 3.0f

        if (isMovingAway) {
            return ThreatLevel.NONE
        }

        if (isFar && !isFastVehicle) {
            return ThreatLevel.NONE
        }

        if (track.pathTrajectory == PathTrajectory.OUTSIDE_PATH && track.currentDistanceMeters > 2.0f && !isFastVehicle) {
            return ThreatLevel.NONE
        }

        return when {
            score >= 120f -> ThreatLevel.CRITICAL
            score >= 85f -> ThreatLevel.WARNING
            score >= 55f -> ThreatLevel.CAUTION
            score >= 25f -> ThreatLevel.ADVISORY
            else -> ThreatLevel.NONE
        }
    }

    /**
     * Formats ultra-short actionable speech (<3-5 words):
     * [Hazard] + [Distance/Direction] + [Action]
     * Never outputs "path clear" or "rasta saaf hai".
     */
    private fun formatActionableSpeech(
        track: ByteTracklet,
        level: ThreatLevel,
        zone: SpatialZone,
        distTier: DistanceTier
    ): String {
        val distM = (track.currentDistanceMeters * 10).roundToInt() / 10f
        val item = track.smoothedClass

        val zoneHi = when (zone) {
            SpatialZone.CENTER -> "Samne"
            SpatialZone.LEFT -> "Baayein"
            SpatialZone.RIGHT -> "Daayein"
        }

        val zoneEn = when (zone) {
            SpatialZone.CENTER -> "ahead"
            SpatialZone.LEFT -> "on your left"
            SpatialZone.RIGHT -> "on your right"
        }

        // Action recommendation
        val actionHi = when (zone) {
            SpatialZone.LEFT -> "daayein chalein"
            SpatialZone.RIGHT -> "baayein chalein"
            SpatialZone.CENTER -> "daayein mudiye"
        }

        val actionEn = when (zone) {
            SpatialZone.LEFT -> "step right"
            SpatialZone.RIGHT -> "step left"
            SpatialZone.CENTER -> "step right"
        }

        val isCloseThreat = track.currentDistanceMeters <= 2.0f

        return if (userLanguage == "hi") {
            if (isCloseThreat) {
                when {
                    item.category.isGroundTrap -> "Rukiye! Aage ${item.hindiLabel}."
                    item.category.isHeadHazard -> "Rukiye! Sar par ${item.hindiLabel}."
                    track.smoothedClass == AssistiveClass.PEDESTRIAN_MOVING -> "Insaan aa raha, $actionHi."
                    track.isMoving && track.isApproachingUser -> "${item.hindiLabel} aa raha, $actionHi."
                    else -> "${item.hindiLabel} samne, $actionHi."
                }
            } else when (level) {
                ThreatLevel.CRITICAL -> {
                    when {
                        item.category.isGroundTrap -> "Rukiye! Aage ${item.hindiLabel}."
                        item.category.isHeadHazard -> "Rukiye! Sar par ${item.hindiLabel}."
                        track.motionState == MotionState.APPROACHING -> "Rukiye! $zoneHi ${item.hindiLabel} aa raha."
                        else -> "Rukiye! Samne ${item.hindiLabel}."
                    }
                }
                ThreatLevel.WARNING -> {
                    "${item.hindiLabel} $zoneHi, $actionHi."
                }
                ThreatLevel.CAUTION -> {
                    "$zoneHi ${item.hindiLabel}."
                }
                ThreatLevel.ADVISORY -> ""
                ThreatLevel.NONE -> ""
            }
        } else {
            if (isCloseThreat) {
                when {
                    item.category.isGroundTrap -> "Stop! ${item.englishLabel} ahead."
                    item.category.isHeadHazard -> "Stop! Head hazard ahead."
                    track.smoothedClass == AssistiveClass.PEDESTRIAN_MOVING -> "Person approaching, $actionEn."
                    track.isMoving && track.isApproachingUser -> "${item.englishLabel} approaching, $actionEn."
                    else -> "${item.englishLabel} ahead, $actionEn."
                }
            } else when (level) {
                ThreatLevel.CRITICAL -> {
                    when {
                        item.category.isGroundTrap -> "Stop! ${item.englishLabel} ahead."
                        item.category.isHeadHazard -> "Stop! Head hazard ahead."
                        track.motionState == MotionState.APPROACHING -> "Stop! ${item.englishLabel} approaching."
                        else -> "Stop! ${item.englishLabel} ahead."
                    }
                }
                ThreatLevel.WARNING -> {
                    "${item.englishLabel} $zoneEn, $actionEn."
                }
                ThreatLevel.CAUTION -> {
                    "${item.englishLabel} $zoneEn."
                }
                ThreatLevel.ADVISORY -> ""
                ThreatLevel.NONE -> ""
            }
        }
    }

    private fun recordAnnouncement(trackId: Long, level: ThreatLevel, distanceMeters: Float, timestampMs: Long) {
        announcedTimestamps[trackId] = timestampMs
        announcedThreatLevels[trackId] = level
        announcedDistances[trackId] = distanceMeters
    }

    fun reset() {
        announcedTimestamps.clear()
        announcedThreatLevels.clear()
        announcedDistances.clear()
    }
}
