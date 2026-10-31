package org.drishti.core.risk

import org.drishti.core.model.AssistiveClass
import org.drishti.core.model.DistanceTier
import org.drishti.core.model.MotionState
import org.drishti.core.model.PathTrajectory
import org.drishti.core.model.SpatialZone
import org.drishti.core.model.ThreatLevel
import org.drishti.core.model.TrackedObstacle
import org.drishti.core.model.WalkingCorridor
import org.drishti.core.tracking.ByteTracklet
import kotlin.math.roundToInt

/**
 * Intelligent Hazard Intelligence & Alert Suppression Engine for Drishti.
 *
 * Implements context-aware threat scoring, collision gating, dynamic alert ranking,
 * and intelligent chatter suppression to protect visually impaired users from audio fatigue.
 */
class HazardIntelligenceEngine(
    var userLanguage: String = "hi",
    var corridor: WalkingCorridor = WalkingCorridor()
) {
    // Cooldown map: TrackId -> Last Announce Timestamp
    private val announcedTimestamps = mutableMapOf<Long, Long>()
    private val announcedThreatLevels = mutableMapOf<Long, ThreatLevel>()

    data class HazardAssessment(
        val tracklet: ByteTracklet,
        val threatLevel: ThreatLevel,
        val threatScore: Float,
        val spatialZone: SpatialZone,
        val distanceTier: DistanceTier,
        val alertSpeech: String,
        val requiresImmediateHaptic: Boolean
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
            val speech = formatSpeech(track, level, zone, distTier)

            HazardAssessment(
                tracklet = track,
                threatLevel = level,
                threatScore = score,
                spatialZone = zone,
                distanceTier = distTier,
                alertSpeech = speech,
                requiresImmediateHaptic = level == ThreatLevel.CRITICAL || level == ThreatLevel.WARNING
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

        // Preemption Rule: If there is an active CRITICAL hazard, suppress all lower-priority alerts
        val hasActiveCritical = allAssessments.any { it.threatLevel == ThreatLevel.CRITICAL }
        if (hasActiveCritical && assessment.threatLevel != ThreatLevel.CRITICAL) {
            return false // Suppressed to keep audio channel free for urgent danger
        }

        val trackId = assessment.tracklet.trackId
        val lastAnnouncedTime = announcedTimestamps[trackId] ?: 0L
        val lastThreat = announcedThreatLevels[trackId] ?: ThreatLevel.NONE

        // Escalation Breakthrough: Immediate alert if threat escalated (e.g. Warning -> Critical)
        if (assessment.threatLevel.priority > lastThreat.priority) {
            recordAnnouncement(trackId, assessment.threatLevel, currentTimestampMs)
            return true
        }

        // Tiered Cooldown to prevent repetitive audio fatigue
        val cooldownMs = when (assessment.threatLevel) {
            ThreatLevel.CRITICAL -> 2500L
            ThreatLevel.WARNING -> 4000L
            ThreatLevel.CAUTION -> 6000L
            ThreatLevel.ADVISORY -> 8000L
            ThreatLevel.NONE -> Long.MAX_VALUE
        }

        if (currentTimestampMs - lastAnnouncedTime > cooldownMs) {
            recordAnnouncement(trackId, assessment.threatLevel, currentTimestampMs)
            return true
        }

        return false
    }

    /**
     * Contextual Threat Scoring Model.
     */
    private fun calculateThreatScore(track: ByteTracklet): Float {
        val category = track.assistiveClass.category

        // 1. Base Hazard Weight
        var baseScore = when (category) {
            AssistiveClass.HazardCategory.GROUND_HAZARD -> 85f
            AssistiveClass.HazardCategory.HEAD_LEVEL -> 90f
            AssistiveClass.HazardCategory.DYNAMIC_ACTOR -> 75f
            AssistiveClass.HazardCategory.STREET_FURNITURE -> 50f
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
            score += 25f // Within 2-3 paces
        }

        // 4. Motion State Adjustments
        when (track.motionState) {
            MotionState.APPROACHING -> score += 20f
            MotionState.RECEDING -> score *= 0.35f // Moving away presents minimal risk
            MotionState.STATIONARY -> {
                // If stationary in path, it's an obstruction; if outside path, largely harmless
                if (track.pathTrajectory == PathTrajectory.OUTSIDE_PATH) score *= 0.30f
            }
            else -> {}
        }

        return score
    }

    private fun scoreToThreatLevel(score: Float, track: ByteTracklet): ThreatLevel {
        // Safe receding objects far away are always ignored
        if (track.motionState == MotionState.RECEDING && track.currentDistanceMeters > 3.0f) {
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

    private fun formatSpeech(
        track: ByteTracklet,
        level: ThreatLevel,
        zone: SpatialZone,
        distTier: DistanceTier
    ): String {
        val distM = (track.currentDistanceMeters * 10).roundToInt() / 10f
        val item = track.assistiveClass

        val zoneHi = when (zone) {
            SpatialZone.CENTER -> "Samne"
            SpatialZone.LEFT -> "Baayein"
            SpatialZone.RIGHT -> "Daayein"
        }

        val zoneEn = when (zone) {
            SpatialZone.CENTER -> "Ahead"
            SpatialZone.LEFT -> "On your left"
            SpatialZone.RIGHT -> "On your right"
        }

        return if (userLanguage == "hi") {
            when (level) {
                ThreatLevel.CRITICAL -> {
                    if (track.motionState == MotionState.APPROACHING) {
                        "Rukiye! $zoneHi ${item.hindiLabel} aa raha hai!"
                    } else {
                        "Rukiye! $zoneHi $distM meter par ${item.hindiLabel} hai!"
                    }
                }
                ThreatLevel.WARNING -> "Savdhan, $zoneHi $distM meter, ${item.hindiLabel}."
                ThreatLevel.CAUTION -> "Dhyan dijiye, $zoneHi ${distTier.hindiLabel}, ${item.hindiLabel}."
                ThreatLevel.ADVISORY -> "$zoneHi ${item.hindiLabel}."
                ThreatLevel.NONE -> ""
            }
        } else {
            when (level) {
                ThreatLevel.CRITICAL -> {
                    if (track.motionState == MotionState.APPROACHING) {
                        "Stop! ${item.englishLabel} approaching $zoneEn!"
                    } else {
                        "Stop! ${item.englishLabel} directly $zoneEn, $distM meters!"
                    }
                }
                ThreatLevel.WARNING -> "Warning, ${item.englishLabel} $zoneEn, $distM meters."
                ThreatLevel.CAUTION -> "Caution, ${item.englishLabel} $zoneEn, ${distTier.englishLabel}."
                ThreatLevel.ADVISORY -> "${item.englishLabel} $zoneEn."
                ThreatLevel.NONE -> ""
            }
        }
    }

    private fun recordAnnouncement(trackId: Long, level: ThreatLevel, timestampMs: Long) {
        announcedTimestamps[trackId] = timestampMs
        announcedThreatLevels[trackId] = level
    }

    fun reset() {
        announcedTimestamps.clear()
        announcedThreatLevels.clear()
    }
}

