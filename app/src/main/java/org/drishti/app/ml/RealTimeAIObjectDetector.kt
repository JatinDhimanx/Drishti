package org.drishti.app.ml

import android.annotation.SuppressLint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import org.drishti.core.inference.AssistiveTaxonomyMapper
import org.drishti.core.inference.ConfirmedHazard
import org.drishti.core.inference.WhitelistNamingEngine
import org.drishti.core.model.AssistiveClass
import org.drishti.core.model.BoundingBox
import org.drishti.core.model.DetectedObstacle
import org.drishti.core.model.DistanceTier
import org.drishti.core.model.GeometricHazard
import org.drishti.core.model.GeometricHazardType
import org.drishti.core.model.HeightClass
import org.drishti.core.model.MotionState
import org.drishti.core.model.PathTrajectory
import org.drishti.core.model.SpatialZone
import org.drishti.core.model.ThreatLevel
import org.drishti.core.model.WalkingCorridor
import org.drishti.core.risk.HazardIntelligenceEngine
import org.drishti.core.tracking.ByteTracker
import org.drishti.core.vision.GeometryObstacleEngine
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

enum class SuggestedAction {
    EMERGENCY_STOP,  // Fast closing vehicle, stairs downward, pothole, or path blocked with no safe steer
    TURN_LEFT,       // Move slightly left
    TURN_RIGHT,      // Move slightly right
    AHEAD_BLOCKED,   // Center path obstructed, turn recommended
    CONTINUE_SAFE    // Path is clear or objects are safe/distant
}

data class DebugMetrics(
    val depthLatencyMs: Long = 0,
    val detectorLatencyMs: Long = 0,
    val trackingLatencyMs: Long = 0,
    val totalLatencyMs: Long = 0,
    val currentPitchDeg: Float = 15.0f,
    val isPitchValid: Boolean = true,
    val averageLuma: Float = 128f,
    val corridorStatus: String = "CLEAR",
    val activeTrackCount: Int = 0
)

data class RecognizedEntity(
    val id: String,
    val englishName: String,
    val hindiName: String,
    val confidence: Float,
    val boundingBox: BoundingBox, // Normalized coordinates 0.0 to 1.0
    val distanceMeters: Float,
    val distanceFeet: Float,
    val approachSpeedMps: Float, // Positive = closing in, negative = moving away
    val isApproaching: Boolean,
    val isStationary: Boolean,
    val timeToCollisionSec: Float?,
    val clockPosition: Int,
    val spatialZone: String,      // "Left", "Center", "Right"
    val movementDirection: String,// "Approaching from Right", "Approaching from Left", "Approaching Ahead", "Stationary", "Moving Away"
    val isVehicle: Boolean,
    val isPedestrian: Boolean,
    val isStairsDownward: Boolean,
    val isStairsUpward: Boolean,
    val isPotholeOrDrain: Boolean,
    val isOverheadObstacle: Boolean,
    val threatLevel: ThreatLevel,
    val suggestedAction: SuggestedAction,
    val alertSpeech: String = ""
)

/**
 * Drishti 3-Layer Real-Time Perception Pipeline.
 *
 * Layer 1 — Geometry-based Obstacle Engine (Monocular depth, ground-plane fitting, corridor gating).
 * Layer 2 — 12-Class Whitelist Classifier & Temporal Confirmation (Strict indoor blacklist, generic fallback).
 * Layer 3 — ByteTrack-Lite Tracking & Hazard Intelligence (Majority voting, TTC, 4s cooldown, safe failure mode).
 */
class RealTimeAIObjectDetector : SensorEventListener {

    // Layer 1: Geometry Engine
    private val geometryEngine = GeometryObstacleEngine()

    // Layer 2: Whitelist Naming & Temporal Confirmation Engine
    private val whitelistEngine = WhitelistNamingEngine()

    // Layer 3: Tracking & Hazard Intelligence
    private val corridor = WalkingCorridor(leftBound = 0.30f, rightBound = 0.70f)
    private val tracker = ByteTracker(
        highConfidenceThreshold = 0.50f,
        lowConfidenceThreshold = 0.20f,
        matchThreshold = 0.25f,
        maxLostFrames = 5
    )
    val hazardEngine = HazardIntelligenceEngine(userLanguage = "hi", corridor = corridor)

    // Hardware Vision & Luminance
    private val objectDetector: ObjectDetector
    private val imageLabeler: ImageLabeler
    private var isProcessing = false
    private var currentDevicePitch: Float = 15.0f // Defaults to typical downward chest-tilt

    // Latency & Health Watchdog
    private var slowInferenceStreak = 0
    private var isSystemDegraded = false
    var latestDebugMetrics: DebugMetrics = DebugMetrics()
        private set

    init {
        val objectOptions = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
        objectDetector = ObjectDetection.getClient(objectOptions)

        val labelerOptions = ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.60f)
            .build()
        imageLabeler = ImageLabeling.getClient(labelerOptions)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            // Pitch in degrees: orientation[1] is pitch (-pi/2 to pi/2)
            val pitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
            currentDevicePitch = pitchDeg
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun setPitchDegrees(pitchDeg: Float) {
        currentDevicePitch = pitchDeg
    }

    fun getPitchDegrees(): Float = currentDevicePitch

    @SuppressLint("UnsafeOptInUsageError")
    fun processImageProxy(
        imageProxy: ImageProxy,
        onResult: (
            entities: List<RecognizedEntity>,
            labels: List<String>,
            isDarkOrCovered: Boolean,
            degradedNotice: String?
        ) -> Unit
    ) {
        if (isProcessing) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val startTimestamp = System.currentTimeMillis()

        // 1. HARDWARE LUMINANCE CHECK: Detect Dark Scene or Covered Camera Lens
        val avgLuminance = calculateAverageLuminance(imageProxy)
        val isDarkOrCovered = avgLuminance < 22.0f

        if (isDarkOrCovered) {
            whitelistEngine.reset()
            tracker.clear()
            onResult(emptyList(), emptyList(), true, "System degraded, use cane carefully")
            imageProxy.close()
            return
        }

        // 2. DEVICE PITCH CHECK: Wrong-Distance Tilt Protection (|pitch| > 40°)
        val isPitchSafe = geometryEngine.isPitchSafe(currentDevicePitch)
        if (!isPitchSafe) {
            val notice = if (hazardEngine.userLanguage == "hi") "Phone ko seedha pakdein" else "Hold phone upright"
            onResult(emptyList(), emptyList(), false, notice)
            imageProxy.close()
            return
        }

        isProcessing = true
        val rotation = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

        val imageWidth = if (rotation == 90 || rotation == 270) mediaImage.height.toFloat() else mediaImage.width.toFloat()
        val imageHeight = if (rotation == 90 || rotation == 270) mediaImage.width.toFloat() else mediaImage.height.toFloat()

        // Run ML Kit detector to find potential candidate boxes and labels
        imageLabeler.process(inputImage)
            .addOnSuccessListener { labels ->
                val detectorStartTime = System.currentTimeMillis()
                val rawLabels = labels.map { it.text }

                objectDetector.process(inputImage)
                    .addOnSuccessListener { detectedObjects ->
                        val now = System.currentTimeMillis()
                        val detectorLatency = now - detectorStartTime

                        // 3. LAYER 1: GEOMETRIC DEPTH & GROUND PLANE SYNTHESIS
                        // Synthesize depth grid from perspective ray tracing & bounding box contacts
                        val depthStartTime = System.currentTimeMillis()
                        val depthGrid = buildPerspectiveDepthGrid(detectedObjects, imageWidth, imageHeight, currentDevicePitch)
                        val geoHazards = geometryEngine.detectGeometricHazards(
                            depthGrid = depthGrid,
                            segGrid = null,
                            devicePitchDegrees = currentDevicePitch
                        )
                        val depthLatency = System.currentTimeMillis() - depthStartTime

                        // 4. LAYER 2: WHITELIST FILTERING & CANDIDATE EXTRACTION
                        val candidateDetections = extractWhitelistCandidates(detectedObjects, rawLabels, imageWidth, imageHeight, now)
                        val confirmedHazards = whitelistEngine.fuseAndValidate(
                            geometricHazards = geoHazards,
                            rawDetections = candidateDetections,
                            currentTimestampMs = now
                        )

                        // 5. LAYER 3: BYTETRACK-LITE TRACKING & MAJORITY VOTING
                        val trackingStartTime = System.currentTimeMillis()
                        val detectedObstacles = confirmedHazards.map { confirmed ->
                            DetectedObstacle(
                                assistiveClass = confirmed.assistiveClass,
                                confidence = confirmed.confidence,
                                boundingBox = confirmed.boundingBox,
                                timestampMs = now
                            )
                        }

                        val tracklets = tracker.update(
                            detections = detectedObstacles,
                            distanceProvider = { det: DetectedObstacle ->
                                confirmedHazards.find { it.boundingBox == det.boundingBox }?.distanceMeters ?: 2.0f
                            },
                            corridor = corridor,
                            timestampMs = now
                        )
                        val trackingLatency = System.currentTimeMillis() - trackingStartTime

                        // 6. HAZARD INTELLIGENCE ENGINE & CHATTER SUPPRESSION
                        val assessments = hazardEngine.evaluateHazards(tracklets, now)

                        val finalEntities = assessments.mapNotNull { assessment ->
                            val track = assessment.tracklet
                            val smoothedItem = track.smoothedClass
                            val threat = assessment.threatLevel

                            // Silence Rule: If outside corridor by > 1m, moving away, or > 3.5m -> silent
                            if (threat == ThreatLevel.NONE) return@mapNotNull null

                            val finalDist = track.currentDistanceMeters
                            val speed: Float = track.currentVelocity.vDistanceMetersPerSec
                            val isApproaching = track.motionState == MotionState.APPROACHING

                            val clockPos = calculateClockPos(track.currentBox.centerX)
                            val spatialZone = when (assessment.spatialZone) {
                                SpatialZone.LEFT -> "Left"
                                SpatialZone.RIGHT -> "Right"
                                SpatialZone.CENTER -> "Center"
                            }

                            val movementDir = when {
                                track.motionState == MotionState.STATIONARY -> "Stationary"
                                isApproaching && track.currentBox.centerX > 0.55f -> "Approaching from Right"
                                isApproaching && track.currentBox.centerX < 0.45f -> "Approaching from Left"
                                isApproaching -> "Approaching Ahead"
                                track.motionState == MotionState.RECEDING -> "Moving Away"
                                else -> "Stationary"
                            }

                            val action = when {
                                threat == ThreatLevel.CRITICAL -> SuggestedAction.EMERGENCY_STOP
                                threat == ThreatLevel.WARNING -> {
                                    if (track.currentBox.centerX < 0.50f) SuggestedAction.TURN_RIGHT else SuggestedAction.TURN_LEFT
                                }
                                else -> SuggestedAction.CONTINUE_SAFE
                            }

                            val engName = if (smoothedItem == AssistiveClass.GENERIC_OBSTACLE) "Obstacle" else smoothedItem.englishLabel
                            val hiName = if (smoothedItem == AssistiveClass.GENERIC_OBSTACLE) "Rukaawat" else smoothedItem.hindiLabel

                            RecognizedEntity(
                                id = track.trackId.toString(),
                                englishName = engName,
                                hindiName = hiName,
                                confidence = 0.90f,
                                boundingBox = track.currentBox,
                                distanceMeters = finalDist,
                                distanceFeet = finalDist * 3.28084f,
                                approachSpeedMps = if (isApproaching) abs(speed) else 0.0f,
                                isApproaching = isApproaching,
                                isStationary = track.motionState == MotionState.STATIONARY,
                                timeToCollisionSec = track.timeToCollisionSeconds,
                                clockPosition = clockPos,
                                spatialZone = spatialZone,
                                movementDirection = movementDir,
                                isVehicle = smoothedItem == AssistiveClass.CAR_VAN_TRUCK || smoothedItem == AssistiveClass.MOTORCYCLE_RICKSHAW || smoothedItem == AssistiveClass.BICYCLE_ESCOOTER,
                                isPedestrian = smoothedItem == AssistiveClass.PEDESTRIAN_MOVING,
                                isStairsDownward = smoothedItem == AssistiveClass.STAIRS_DOWN,
                                isStairsUpward = smoothedItem == AssistiveClass.STAIRS_UP,
                                isPotholeOrDrain = smoothedItem == AssistiveClass.POTHOLE_OPEN_TRENCH,
                                isOverheadObstacle = smoothedItem == AssistiveClass.OVERHANGING_BRANCH || smoothedItem == AssistiveClass.PROTRUDING_SIGNBOARD,
                                threatLevel = threat,
                                suggestedAction = action,
                                alertSpeech = assessment.alertSpeech
                            )
                        }

                        // Latency Watchdog (< 150ms on mobile device)
                        val totalLatency = System.currentTimeMillis() - startTimestamp
                        if (totalLatency > 150) {
                            slowInferenceStreak++
                        } else {
                            slowInferenceStreak = 0
                        }

                        val degradedNotice = if (slowInferenceStreak >= 5) {
                            "System degraded, use cane carefully"
                        } else null

                        latestDebugMetrics = DebugMetrics(
                            depthLatencyMs = depthLatency,
                            detectorLatencyMs = detectorLatency,
                            trackingLatencyMs = trackingLatency,
                            totalLatencyMs = totalLatency,
                            currentPitchDeg = currentDevicePitch,
                            isPitchValid = isPitchSafe,
                            averageLuma = avgLuminance,
                            corridorStatus = if (finalEntities.isEmpty()) "CLEAR" else "HAZARD_DETECTED",
                            activeTrackCount = tracklets.size
                        )

                        onResult(finalEntities, rawLabels, false, degradedNotice)
                        isProcessing = false
                        imageProxy.close()
                    }
                    .addOnFailureListener {
                        isProcessing = false
                        imageProxy.close()
                    }
            }
            .addOnFailureListener {
                isProcessing = false
                imageProxy.close()
            }
    }

    /**
     * Extracts whitelist-only candidates from raw detections, enforcing the strict blacklist.
     */
    private fun extractWhitelistCandidates(
        detectedObjects: List<DetectedObject>,
        labels: List<String>,
        imageWidth: Float,
        imageHeight: Float,
        now: Long
    ): List<DetectedObstacle> {
        val candidates = mutableListOf<DetectedObstacle>()

        detectedObjects.forEach { obj ->
            val rect = obj.boundingBox
            val normalizedBox = BoundingBox(
                left = (rect.left / imageWidth).coerceIn(0.0f, 1.0f),
                top = (rect.top / imageHeight).coerceIn(0.0f, 1.0f),
                right = (rect.right / imageWidth).coerceIn(0.0f, 1.0f),
                bottom = (rect.bottom / imageHeight).coerceIn(0.0f, 1.0f)
            )

            val rawLabel = obj.labels.firstOrNull()?.text ?: ""
            val conf = obj.labels.firstOrNull()?.confidence ?: 0.70f

            // 1. BLACKLIST CHECK: If it matches indoor/irrelevant objects, reject it immediately
            if (AssistiveClass.isBlacklisted(rawLabel) || labels.any { AssistiveClass.isBlacklisted(it) }) {
                // Must NEVER be mapped or announced as a whitelist class
                return@forEach
            }

            // 2. Strict Whitelist Matching
            val mappedClass = AssistiveTaxonomyMapper.fromLabelText(rawLabel)
                ?: labels.firstNotNullOfOrNull { AssistiveTaxonomyMapper.fromLabelText(it) }

            if (mappedClass != null) {
                candidates.add(
                    DetectedObstacle(
                        assistiveClass = mappedClass,
                        confidence = conf,
                        boundingBox = normalizedBox,
                        timestampMs = now
                    )
                )
            }
        }

        return candidates
    }

    /**
     * Builds a 16x16 depth map from ground plane trigonometry and detected physical obstacles.
     */
    private fun buildPerspectiveDepthGrid(
        detectedObjects: List<DetectedObject>,
        imageWidth: Float,
        imageHeight: Float,
        pitchDeg: Float
    ): GeometryObstacleEngine.DepthGrid {
        val gridDim = 16
        val depths = FloatArray(gridDim * gridDim)
        val pitchRad = Math.toRadians(pitchDeg.toDouble())

        // Default: Flat ground plane projection
        for (y in 0 until gridDim) {
            val normY = (y + 0.5f) / gridDim
            val vertAngleDeg = (normY - 0.5f) * 60.0f
            val totalDep = pitchRad + Math.toRadians(vertAngleDeg.toDouble())
            val sinDep = Math.sin(totalDep)
            val groundDist = if (sinDep > 0.05) (1.35 / sinDep).toFloat().coerceIn(0.5f, 10.0f) else 10.0f
            for (x in 0 until gridDim) {
                depths[y * gridDim + x] = groundDist
            }
        }

        // Overlay detected physical box bounds into depth grid
        detectedObjects.forEach { obj ->
            val rect = obj.boundingBox
            val normLeft = (rect.left / imageWidth).coerceIn(0f, 1f)
            val normRight = (rect.right / imageWidth).coerceIn(0f, 1f)
            val normTop = (rect.top / imageHeight).coerceIn(0f, 1f)
            val normBottom = (rect.bottom / imageHeight).coerceIn(0f, 1f)

            // Pin-point physical ground contact distance
            val contactY = normBottom
            val vertAngle = (contactY - 0.5f) * 60.0f
            val totalDep = pitchRad + Math.toRadians(vertAngle.toDouble())
            val sinDep = Math.sin(totalDep)
            val obsDist = if (sinDep > 0.05) (1.35 / sinDep).toFloat().coerceIn(0.5f, 8.0f) else 3.0f

            val startX = (normLeft * gridDim).toInt().coerceIn(0, gridDim - 1)
            val endX = (normRight * gridDim).toInt().coerceIn(0, gridDim - 1)
            val startY = (normTop * gridDim).toInt().coerceIn(0, gridDim - 1)
            val endY = (normBottom * gridDim).toInt().coerceIn(0, gridDim - 1)

            for (gy in startY..endY) {
                for (gx in startX..endX) {
                    val idx = gy * gridDim + gx
                    depths[idx] = minOf(depths[idx], obsDist)
                }
            }
        }

        return GeometryObstacleEngine.DepthGrid(gridDim, gridDim, depths)
    }

    private fun calculateClockPos(centerX: Float): Int {
        return when {
            centerX < 0.32f -> 10
            centerX < 0.45f -> 11
            centerX <= 0.55f -> 12
            centerX <= 0.68f -> 1
            else -> 2
        }
    }

    private fun calculateAverageLuminance(imageProxy: ImageProxy): Float {
        return try {
            val plane = imageProxy.planes.firstOrNull() ?: return 128f
            val buffer: ByteBuffer = plane.buffer
            val remaining = buffer.remaining()
            if (remaining <= 0) return 128f

            val sampleCount = 600
            val step = maxOf(1, remaining / sampleCount)
            var sum = 0L
            var count = 0
            var i = 0

            val readBuffer = buffer.asReadOnlyBuffer()
            readBuffer.rewind()

            while (i < remaining && count < sampleCount) {
                sum += (readBuffer.get(i).toInt() and 0xFF)
                count++
                i += step
            }

            if (count > 0) sum.toFloat() / count else 128f
        } catch (e: Exception) {
            128f
        }
    }

    fun translateToHindi(englishName: String): String {
        val lower = englishName.lowercase()
        return when {
            AssistiveClass.isBlacklisted(lower) -> "Rukaawat"
            lower.contains("person") || lower.contains("pedestrian") || lower.contains("human") -> "Insaan"
            lower.contains("car") || lower.contains("vehicle") -> "Gadi"
            lower.contains("motorcycle") || lower.contains("bike") -> "Bike"
            lower.contains("scooter") -> "Scooter"
            lower.contains("bicycle") || lower.contains("cycle") -> "Cycle"
            lower.contains("rickshaw") || lower.contains("auto") -> "Auto"
            lower.contains("pothole") || lower.contains("trench") -> "Gaddha"
            lower.contains("downward") || lower.contains("stairs_down") -> "Neeche jati sidhiyan"
            lower.contains("stair") -> "Sidhiyan"
            lower.contains("pole") || lower.contains("bollard") -> "Khamba"
            lower.contains("curb") -> "Sadak ki sidhi"
            lower.contains("branch") -> "Ped ki daali"
            lower.contains("sign") -> "Signboard"
            lower.contains("animal") || lower.contains("dog") || lower.contains("cow") -> "Janwar"
            else -> "Rukaawat"
        }
    }

    fun close() {
        try {
            objectDetector.close()
            imageLabeler.close()
            tracker.clear()
            whitelistEngine.reset()
            hazardEngine.reset()
        } catch (e: Exception) {}
    }
}

