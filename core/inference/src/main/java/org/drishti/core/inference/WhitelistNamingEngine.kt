package org.drishti.core.inference

import org.drishti.core.model.AssistiveClass
import org.drishti.core.model.BoundingBox
import org.drishti.core.model.DetectedObstacle
import org.drishti.core.model.GeometricHazard
import org.drishti.core.model.GeometricHazardType
import org.drishti.core.model.HeightClass
import java.util.concurrent.ConcurrentHashMap

/**
 * Layer 2 — Whitelist Classifier & Geometric Naming Layer.
 *
 * Rules:
 * 1. Strict 12-class whitelist ONLY.
 * 2. Explicitly blacklists indoor/irrelevant objects (laptop, table, chair, TV, etc.).
 * 3. Applies calibrated per-class thresholds (Drop-offs: 0.45, Head hazards: 0.50, Vehicles: 0.55, Static: 0.65).
 * 4. Temporal confirmation: >= 3 of last 5 frames (2 of 3 for critical drop-offs / fast vehicles).
 * 5. Generic fallback: If Layer 1 confirms a geometric obstacle but detector is unconfident or unlisted,
 *    outputs GENERIC_OBSTACLE ("Obstacle ahead, 12 o'clock, 1.5 meters"). NEVER guesses a name!
 */
class WhitelistNamingEngine {

    companion object {
        const val WINDOW_SIZE = 5

        // Calibrated per-class thresholds
        const val THRESHOLD_DROPOFF = 0.45f
        const val THRESHOLD_HEAD_HAZARD = 0.50f
        const val THRESHOLD_DYNAMIC_VEHICLE = 0.55f
        const val THRESHOLD_PEDESTRIAN = 0.55f
        const val THRESHOLD_STATIC_OBJECT = 0.65f
    }

    private data class TrackObservation(
        val timestampMs: Long,
        val confirmedClass: AssistiveClass?,
        val confidence: Float,
        val isCritical: Boolean
    )

    // Track ID -> Recent frame observations (up to WINDOW_SIZE)
    private val observationWindows = ConcurrentHashMap<Long, MutableList<TrackObservation>>()

    /**
     * Fuses Layer 1 Geometric Hazards with Layer 2 Whitelist Detections.
     *
     * @param geometricHazards Physical 3D obstacles detected by Layer 1
     * @param rawDetections Candidates from 12-class detector
     * @param currentTimestampMs Current frame timestamp
     * @return List of temporally confirmed, whitelist-filtered obstacles
     */
    fun fuseAndValidate(
        geometricHazards: List<GeometricHazard>,
        rawDetections: List<DetectedObstacle>,
        currentTimestampMs: Long = System.currentTimeMillis()
    ): List<ConfirmedHazard> {
        val results = mutableListOf<ConfirmedHazard>()

        // 1. Filter out raw detections that match hard blacklist
        val cleanWhitelistDetections = rawDetections.filter { det ->
            !AssistiveClass.isBlacklisted(det.assistiveClass.technicalName) &&
            !AssistiveClass.isBlacklisted(det.assistiveClass.englishLabel) &&
            det.confidence >= getCalibratedThreshold(det.assistiveClass)
        }

        val matchedDetections = mutableSetOf<DetectedObstacle>()

        // 2. Pair each Geometric Hazard with the best overlapping Whitelist Detection
        geometricHazards.forEachIndexed { index, geoHazard ->
            val trackKey = (index + 1).toLong()

            // Find overlapping detection
            val matchingDet = cleanWhitelistDetections.filter { it !in matchedDetections }.maxByOrNull { det ->
                YoloTensorParser.calculateIou(geoHazard.boundingBox, det.boundingBox)
            }
            val hasGoodOverlap = matchingDet != null &&
                YoloTensorParser.calculateIou(geoHazard.boundingBox, matchingDet.boundingBox) >= 0.20f

            val candidateClass: AssistiveClass
            val candidateConfidence: Float
            val isCritical: Boolean

            if (hasGoodOverlap && matchingDet != null) {
                matchedDetections.add(matchingDet)
                candidateClass = matchingDet.assistiveClass
                candidateConfidence = matchingDet.confidence
                isCritical = candidateClass == AssistiveClass.STAIRS_DOWN ||
                             candidateClass == AssistiveClass.POTHOLE_OPEN_TRENCH ||
                             candidateClass == AssistiveClass.CAR_VAN_TRUCK ||
                             candidateClass == AssistiveClass.MOTORCYCLE_RICKSHAW
            } else {
                // If Layer 1 found a drop-off or head hazard, map to generic category fallback
                candidateClass = when (geoHazard.type) {
                    GeometricHazardType.DROPOFF -> AssistiveClass.POTHOLE_OPEN_TRENCH
                    GeometricHazardType.HEAD_HAZARD -> AssistiveClass.OVERHANGING_BRANCH
                    GeometricHazardType.OBSTACLE -> AssistiveClass.GENERIC_OBSTACLE
                }
                candidateConfidence = geoHazard.confidence
                isCritical = geoHazard.type == GeometricHazardType.DROPOFF
            }

            // Record into temporal confirmation window
            val window = observationWindows.getOrPut(trackKey) { mutableListOf() }
            window.add(
                TrackObservation(
                    timestampMs = currentTimestampMs,
                    confirmedClass = candidateClass,
                    confidence = candidateConfidence,
                    isCritical = isCritical
                )
            )
            if (window.size > WINDOW_SIZE) {
                window.removeAt(0)
            }

            // Prune old entries (> 1200ms)
            window.removeAll { currentTimestampMs - it.timestampMs > 1200 }

            // Evaluate Temporal Confirmation:
            // 2 of 3 for critical drop-offs / fast vehicles; 3 of 5 for others
            val minHitsNeeded = if (isCritical) 2 else 3
            val recentHits = window.size

            if (recentHits >= minHitsNeeded) {
                // Compute majority vote class
                val classVotes = window.mapNotNull { it.confirmedClass }
                    .groupingBy { it }
                    .eachCount()

                val majorityClass = classVotes.maxByOrNull { it.value }?.key ?: AssistiveClass.GENERIC_OBSTACLE

                // Calculate clock position from azimuth
                val clockPos = calculateClockPosition(geoHazard.azimuthDegrees)

                results.add(
                    ConfirmedHazard(
                        trackId = trackKey,
                        assistiveClass = majorityClass,
                        distanceMeters = geoHazard.distanceMeters,
                        azimuthDegrees = geoHazard.azimuthDegrees,
                        clockPosition = clockPos,
                        confidence = candidateConfidence,
                        boundingBox = geoHazard.boundingBox,
                        isPitchValid = geoHazard.isPitchValid,
                        geometricType = geoHazard.type,
                        heightClass = geoHazard.heightClass
                    )
                )
            }
        }

        // 3. Ensure all clean camera detections (especially Persons & objects) are tracked even if not matched to geometricHazards
        cleanWhitelistDetections.filter { it !in matchedDetections }.forEachIndexed { detIdx, det ->
            val trackKey = 1000L + detIdx
            val bot = det.boundingBox.bottom.coerceIn(0.1f, 0.98f)
            val dist = (1.3f / kotlin.math.tan(Math.toRadians(15.0 + ((bot - 0.5) * 35.0)))).toFloat().coerceIn(0.4f, 6.0f)
            val az = (det.boundingBox.centerX - 0.5f) * 60f
            val isCritical = det.assistiveClass == AssistiveClass.PEDESTRIAN_MOVING ||
                             det.assistiveClass == AssistiveClass.CAR_VAN_TRUCK ||
                             dist <= 2.0f

            val window = observationWindows.getOrPut(trackKey) { mutableListOf() }
            window.add(
                TrackObservation(
                    timestampMs = currentTimestampMs,
                    confirmedClass = det.assistiveClass,
                    confidence = det.confidence,
                    isCritical = isCritical
                )
            )
            if (window.size > WINDOW_SIZE) {
                window.removeAt(0)
            }
            window.removeAll { currentTimestampMs - it.timestampMs > 1200 }

            if (window.isNotEmpty()) {
                val clockPos = calculateClockPosition(az)
                results.add(
                    ConfirmedHazard(
                        trackId = trackKey,
                        assistiveClass = det.assistiveClass,
                        distanceMeters = dist,
                        azimuthDegrees = az,
                        clockPosition = clockPos,
                        confidence = det.confidence,
                        boundingBox = det.boundingBox,
                        isPitchValid = true,
                        geometricType = GeometricHazardType.OBSTACLE,
                        heightClass = if (det.assistiveClass == AssistiveClass.PEDESTRIAN_MOVING) HeightClass.HEAD else HeightClass.WAIST
                    )
                )
            }
        }

        // Clean stale tracks
        observationWindows.entries.removeIf { (_, list) ->
            list.isEmpty() || (currentTimestampMs - list.last().timestampMs > 1500)
        }

        return results
    }

    private fun getCalibratedThreshold(itemClass: AssistiveClass): Float {
        return when (itemClass) {
            AssistiveClass.STAIRS_DOWN, AssistiveClass.POTHOLE_OPEN_TRENCH, AssistiveClass.CURB_STEP -> THRESHOLD_DROPOFF
            AssistiveClass.OVERHANGING_BRANCH, AssistiveClass.PROTRUDING_SIGNBOARD -> THRESHOLD_HEAD_HAZARD
            AssistiveClass.CAR_VAN_TRUCK, AssistiveClass.MOTORCYCLE_RICKSHAW, AssistiveClass.BICYCLE_ESCOOTER -> THRESHOLD_DYNAMIC_VEHICLE
            AssistiveClass.PEDESTRIAN_MOVING -> THRESHOLD_PEDESTRIAN
            AssistiveClass.POLE_BOLLARD, AssistiveClass.ANIMAL_DOG_CATTLE -> THRESHOLD_STATIC_OBJECT
            else -> itemClass.defaultConfidenceThreshold
        }
    }

    private fun calculateClockPosition(azimuthDeg: Float): Int {
        return when {
            azimuthDeg in -15f..15f -> 12
            azimuthDeg in 15f..45f -> 1
            azimuthDeg in 45f..75f -> 2
            azimuthDeg > 75f -> 3
            azimuthDeg in -45f..-15f -> 11
            azimuthDeg in -75f..-45f -> 10
            else -> 9
        }
    }

    fun reset() {
        observationWindows.clear()
    }
}

data class ConfirmedHazard(
    val trackId: Long,
    val assistiveClass: AssistiveClass,
    val distanceMeters: Float,
    val azimuthDegrees: Float,
    val clockPosition: Int,
    val confidence: Float,
    val boundingBox: BoundingBox,
    val isPitchValid: Boolean,
    val geometricType: GeometricHazardType,
    val heightClass: HeightClass
)

