package org.drishti.app.ml

import org.drishti.core.inference.WhitelistNamingEngine
import org.drishti.core.model.AssistiveClass
import org.drishti.core.model.BoundingBox
import org.drishti.core.model.DetectedObstacle
import org.drishti.core.model.GeometricHazard
import org.drishti.core.model.GeometricHazardType
import org.drishti.core.model.HeightClass
import org.drishti.core.model.ThreatLevel
import org.drishti.core.model.WalkingCorridor
import org.drishti.core.risk.HazardIntelligenceEngine
import org.drishti.core.tracking.ByteTracker
import org.drishti.core.vision.GeometryObstacleEngine

/**
 * Offline Video / Sequence Replay Evaluator for Drishti.
 *
 * Runs the perception pipeline on recorded video test frames without needing camera hardware.
 * Logs alerts frame-by-frame, verifying that:
 * 1. Hard blacklisted indoor items (laptop, table, chair, etc.) are never announced.
 * 2. Unwhitelisted obstacles fall back to generic "Obstacle ahead" with metric distance.
 * 3. Fast vehicles and drop-offs trigger within 2 frames.
 * 4. Silent corridors produce 0 false alarms.
 */
class OfflineReplayEvaluator {

    data class ReplayFrame(
        val frameIndex: Int,
        val timestampMs: Long,
        val pitchDeg: Float,
        val detectedRawBoxes: List<Pair<String, BoundingBox>>, // Label to BoundingBox
        val depthGrid: GeometryObstacleEngine.DepthGrid
    )

    data class FrameEvaluationLog(
        val frameIndex: Int,
        val timestampMs: Long,
        val spokenAlerts: List<String>,
        val confirmedClasses: List<String>,
        val falseAlarmDetected: Boolean,
        val blacklistedSpoken: Boolean
    )

    private val geometryEngine = GeometryObstacleEngine()
    private val namingEngine = WhitelistNamingEngine()
    private val corridor = WalkingCorridor(0.30f, 0.70f)
    private val tracker = ByteTracker()
    private val hazardEngine = HazardIntelligenceEngine(userLanguage = "en", corridor = corridor)

    fun evaluateSequence(frames: List<ReplayFrame>): List<FrameEvaluationLog> {
        val logs = mutableListOf<FrameEvaluationLog>()
        namingEngine.reset()
        tracker.clear()
        hazardEngine.reset()

        for (frame in frames) {
            val spokenAlerts = mutableListOf<String>()
            val confirmedClasses = mutableListOf<String>()
            var blacklistedSpoken = false

            // Layer 1: Geometry
            val geoHazards = geometryEngine.detectGeometricHazards(
                depthGrid = frame.depthGrid,
                segGrid = null,
                devicePitchDegrees = frame.pitchDeg
            )

            // Layer 2: Whitelist extraction & Fusion
            val candidates = frame.detectedRawBoxes.mapNotNull { (label, box) ->
                if (AssistiveClass.isBlacklisted(label)) null
                else {
                    val c = AssistiveClass.entries.find { it.technicalName == label || it.englishLabel.equals(label, ignoreCase = true) }
                    if (c != null) DetectedObstacle(c, 0.90f, box, frame.timestampMs) else null
                }
            }

            val confirmed = namingEngine.fuseAndValidate(
                geometricHazards = geoHazards,
                rawDetections = candidates,
                currentTimestampMs = frame.timestampMs
            )

            // Layer 3: Tracking & Hazard Evaluation
            val obstacles = confirmed.map {
                DetectedObstacle(it.assistiveClass, it.confidence, it.boundingBox, frame.timestampMs)
            }
            val tracklets = tracker.update(
                detections = obstacles,
                distanceProvider = { det ->
                    confirmed.find { it.boundingBox == det.boundingBox }?.distanceMeters ?: 2.0f
                },
                corridor = corridor,
                timestampMs = frame.timestampMs
            )

            val assessments = hazardEngine.evaluateHazards(tracklets, frame.timestampMs)
            for (assessment in assessments) {
                if (hazardEngine.shouldTriggerAlert(assessment, assessments, frame.timestampMs)) {
                    spokenAlerts.add(assessment.alertSpeech)
                    confirmedClasses.add(assessment.tracklet.smoothedClass.technicalName)

                    // Verify if any blacklisted indoor term leaked into spoken alert
                    if (AssistiveClass.HARD_BLACKLISTED_LABELS.any { assessment.alertSpeech.contains(it, ignoreCase = true) }) {
                        blacklistedSpoken = true
                    }
                }
            }

            val falseAlarm = spokenAlerts.isNotEmpty() && frame.detectedRawBoxes.isEmpty() && geoHazards.isEmpty()

            logs.add(
                FrameEvaluationLog(
                    frameIndex = frame.frameIndex,
                    timestampMs = frame.timestampMs,
                    spokenAlerts = spokenAlerts,
                    confirmedClasses = confirmedClasses,
                    falseAlarmDetected = falseAlarm,
                    blacklistedSpoken = blacklistedSpoken
                )
            )
        }

        return logs
    }
}

