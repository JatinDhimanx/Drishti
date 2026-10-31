package org.drishti.feature.navigation

import org.drishti.core.location.PedestrianNavigationEngine
import org.drishti.core.model.CorridorStatus
import org.drishti.core.model.SpatialZone
import org.drishti.core.model.ThreatLevel
import org.drishti.core.model.TurnManeuver
import org.drishti.core.risk.HazardIntelligenceEngine
import org.drishti.core.risk.SpatialEnvironmentAnalyzer

/**
 * Fused AI Guidance & Navigation Coordinator for Drishti.
 *
 * Merges real-time CameraX obstacle perception with GPS pedestrian turn maneuvers
 * into unified, natural instructions while ensuring safety-critical obstacle preemption.
 */
class FusedGuidanceCoordinator(
    var userLanguage: String = "hi"
) {

    data class FusedOutput(
        val speechText: String,
        val isCriticalPreemption: Boolean,
        val requiresHaptic: Boolean,
        val activeManeuver: TurnManeuver?,
        val primaryThreat: ThreatLevel
    )

    /**
     * Synthesizes real-time camera hazard assessments with pedestrian turn guidance.
     */
    fun synthesizeGuidance(
        hazardAssessment: HazardIntelligenceEngine.HazardAssessment?,
        navGuidance: PedestrianNavigationEngine.NavGuidance?,
        envSummary: SpatialEnvironmentAnalyzer.EnvironmentSummary? = null
    ): FusedOutput {
        val threatLevel = hazardAssessment?.threatLevel ?: ThreatLevel.NONE

        // 1. Emergency Preemption: Critical obstacle immediately silences all navigation
        if (threatLevel == ThreatLevel.CRITICAL && hazardAssessment != null) {
            return FusedOutput(
                speechText = hazardAssessment.alertSpeech,
                isCriticalPreemption = true,
                requiresHaptic = true,
                activeManeuver = navGuidance?.maneuver,
                primaryThreat = ThreatLevel.CRITICAL
            )
        }

        // 2. Corridor Blockage Override: Obstruction ahead requires detouring before continuing route
        if (envSummary?.corridorStatus == CorridorStatus.BLOCKED) {
            val blockedSpeech = if (userLanguage == "hi") {
                "Rukiye! Aage raasta band hai. Kripya rukein aur surakshit disha chunein."
            } else {
                "Stop! Walking corridor blocked ahead. Please halt and re-orient."
            }
            return FusedOutput(
                speechText = blockedSpeech,
                isCriticalPreemption = true,
                requiresHaptic = true,
                activeManeuver = navGuidance?.maneuver,
                primaryThreat = ThreatLevel.CRITICAL
            )
        }

        // 3. Fused Compound Guidance: Turn maneuver + nearby peripheral obstacle
        if (navGuidance != null && hazardAssessment != null &&
            (threatLevel == ThreatLevel.WARNING || threatLevel == ThreatLevel.CAUTION)
        ) {
            val item = hazardAssessment.tracklet.assistiveClass
            val sideHi = when (hazardAssessment.spatialZone) {
                SpatialZone.LEFT -> "baayein taraf"
                SpatialZone.RIGHT -> "daayein taraf"
                SpatialZone.CENTER -> "samne"
            }
            val sideEn = when (hazardAssessment.spatialZone) {
                SpatialZone.LEFT -> "on your left"
                SpatialZone.RIGHT -> "on your right"
                SpatialZone.CENTER -> "ahead"
            }

            val steps = navGuidance.estimatedStepsToNextTurn
            val fusedText = if (userLanguage == "hi") {
                when (navGuidance.maneuver) {
                    TurnManeuver.TURN_LEFT -> "Lagbhag $steps kadam baad sambhal kar baayein mudiye. Dhyan dijiye, $sideHi ${item.hindiLabel} hai."
                    TurnManeuver.TURN_RIGHT -> "Lagbhag $steps kadam baad sambhal kar daayein mudiye. Dhyan dijiye, $sideHi ${item.hindiLabel} hai."
                    TurnManeuver.CONTINUE_STRAIGHT -> "Seedhe chaliye, lekin dhyan rahe $sideHi ${item.hindiLabel} hai."
                    TurnManeuver.OFF_PATH_WARNING -> "Aap raste se hat gaye hain. Dhyan dijiye, $sideHi ${item.hindiLabel} hai."
                    TurnManeuver.DESTINATION_ARRIVED -> "Aap manzil par pahunch gaye hain. $sideHi ${item.hindiLabel} hai."
                }
            } else {
                when (navGuidance.maneuver) {
                    TurnManeuver.TURN_LEFT -> "In about $steps steps, turn left carefully. Note: ${item.englishLabel} is $sideEn."
                    TurnManeuver.TURN_RIGHT -> "In about $steps steps, turn right carefully. Note: ${item.englishLabel} is $sideEn."
                    TurnManeuver.CONTINUE_STRAIGHT -> "Continue straight. Note: ${item.englishLabel} $sideEn."
                    TurnManeuver.OFF_PATH_WARNING -> "Off path, please turn around. Caution: ${item.englishLabel} $sideEn."
                    TurnManeuver.DESTINATION_ARRIVED -> "You have arrived. Caution: ${item.englishLabel} $sideEn."
                }
            }

            return FusedOutput(
                speechText = fusedText,
                isCriticalPreemption = false,
                requiresHaptic = threatLevel == ThreatLevel.WARNING,
                activeManeuver = navGuidance.maneuver,
                primaryThreat = threatLevel
            )
        }

        // 4. Standalone Obstacle Alert (when no active turn maneuver or distance is far)
        if (hazardAssessment != null && threatLevel != ThreatLevel.NONE) {
            return FusedOutput(
                speechText = hazardAssessment.alertSpeech,
                isCriticalPreemption = false,
                requiresHaptic = threatLevel == ThreatLevel.WARNING,
                activeManeuver = navGuidance?.maneuver,
                primaryThreat = threatLevel
            )
        }

        // 5. Standalone Pedestrian Turn Guidance (path is completely clear of hazards)
        if (navGuidance != null) {
            return FusedOutput(
                speechText = navGuidance.spokenInstruction,
                isCriticalPreemption = false,
                requiresHaptic = navGuidance.maneuver == TurnManeuver.OFF_PATH_WARNING || navGuidance.maneuver == TurnManeuver.DESTINATION_ARRIVED,
                activeManeuver = navGuidance.maneuver,
                primaryThreat = ThreatLevel.NONE
            )
        }

        // 6. Default idle state
        return FusedOutput(
            speechText = "",
            isCriticalPreemption = false,
            requiresHaptic = false,
            activeManeuver = null,
            primaryThreat = ThreatLevel.NONE
        )
    }
}

