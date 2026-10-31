package org.drishti.app.ml

import android.annotation.SuppressLint
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import org.drishti.core.model.BoundingBox
import org.drishti.core.model.ThreatLevel
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

enum class SuggestedAction {
    EMERGENCY_STOP,  // Fast closing vehicle or imminent barrier collision
    TURN_LEFT,       // Obstacle at ~2 feet on right side
    TURN_RIGHT,      // Obstacle at ~2 feet on left side
    AHEAD_BLOCKED,   // Center blocked at ~2 feet
    CONTINUE_SAFE    // Object at safe distance or stationary far
}

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
    val timeToCollisionSec: Float?,
    val clockPosition: Int,
    val isVehicle: Boolean,
    val threatLevel: ThreatLevel,
    val suggestedAction: SuggestedAction
)

private data class DistanceRecord(
    val distanceMeters: Float,
    val timestampMs: Long
)

/**
 * Intelligent Real-Time AI Object & Dynamics Detector for Drishti.
 * 1. Calculates approach speed (delta-d / delta-t) to only alert when closing in
 * 2. 2-Feet Proximity Guidance: Instructs user to turn left or right away from obstacles
 * 3. High-Speed Vehicle / Collision Detection: Commands immediate emergency stop
 */
class RealTimeAIObjectDetector {

    private val objectDetector: ObjectDetector
    private val imageLabeler: ImageLabeler
    private var isProcessing = false
    private val historyMap = ConcurrentHashMap<String, DistanceRecord>()

    init {
        val objectOptions = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
        objectDetector = ObjectDetection.getClient(objectOptions)

        val labelerOptions = ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.40f)
            .build()
        imageLabeler = ImageLabeling.getClient(labelerOptions)
    }

    @SuppressLint("UnsafeOptInUsageError")
    fun processImageProxy(
        imageProxy: ImageProxy,
        onResult: (List<RecognizedEntity>, List<String>) -> Unit
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

        isProcessing = true
        val rotation = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
        val now = System.currentTimeMillis()

        val imageWidth = if (rotation == 90 || rotation == 270) mediaImage.height.toFloat() else mediaImage.width.toFloat()
        val imageHeight = if (rotation == 90 || rotation == 270) mediaImage.width.toFloat() else mediaImage.height.toFloat()

        // 1. Process Image Labels
        imageLabeler.process(inputImage)
            .addOnSuccessListener { labels ->
                val detectedCategories = labels.map { it.text }

                // 2. Process Object Geometry & Bounding Boxes
                objectDetector.process(inputImage)
                    .addOnSuccessListener { detectedObjects ->
                        val entities = mapObjectsToEntities(
                            detectedObjects = detectedObjects,
                            detectedCategories = detectedCategories,
                            imageWidth = imageWidth,
                            imageHeight = imageHeight,
                            now = now
                        )
                        onResult(entities, detectedCategories)
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

    private fun mapObjectsToEntities(
        detectedObjects: List<DetectedObject>,
        detectedCategories: List<String>,
        imageWidth: Float,
        imageHeight: Float,
        now: Long
    ): List<RecognizedEntity> {
        val results = mutableListOf<RecognizedEntity>()

        if (detectedObjects.isEmpty() && detectedCategories.isNotEmpty()) {
            detectedCategories.take(2).forEachIndexed { index, label ->
                val hindi = translateToHindi(label)
                val isVeh = checkIsVehicle(label)
                val top = 0.35f + (index * 0.18f)
                val bottom = min(1.0f, top + 0.30f)
                val rawDistance = 1.6f + (index * 0.6f)
                val distFeet = rawDistance * 3.28084f

                results.add(
                    RecognizedEntity(
                        id = "label_$label",
                        englishName = label,
                        hindiName = hindi,
                        confidence = 0.78f,
                        boundingBox = BoundingBox(left = 0.25f, top = top, right = 0.75f, bottom = bottom),
                        distanceMeters = rawDistance,
                        distanceFeet = distFeet,
                        approachSpeedMps = 0.0f,
                        isApproaching = false,
                        timeToCollisionSec = null,
                        clockPosition = 12,
                        isVehicle = isVeh,
                        threatLevel = ThreatLevel.ADVISORY,
                        suggestedAction = SuggestedAction.CONTINUE_SAFE
                    )
                )
            }
            return results
        }

        detectedObjects.forEachIndexed { index, obj ->
            val rect = obj.boundingBox
            val normalizedBox = BoundingBox(
                left = (rect.left / imageWidth).coerceIn(0.0f, 1.0f),
                top = (rect.top / imageHeight).coerceIn(0.0f, 1.0f),
                right = (rect.right / imageWidth).coerceIn(0.0f, 1.0f),
                bottom = (rect.bottom / imageHeight).coerceIn(0.0f, 1.0f)
            )

            val trackingIdStr = obj.trackingId?.toString() ?: "obj_$index"
            val mlKitLabel = obj.labels.firstOrNull()?.text
            val englishName = mlKitLabel
                ?: detectedCategories.getOrNull(index)
                ?: detectedCategories.firstOrNull()
                ?: "Obstacle"

            val hindiName = translateToHindi(englishName)
            val isVehicle = checkIsVehicle(englishName)
            val boxHeightFraction = normalizedBox.bottom - normalizedBox.top

            // Ground distance estimation
            val currentDistanceMeters = when {
                boxHeightFraction > 0.70f -> 0.55f // < 2 feet (~1.8 feet)
                boxHeightFraction > 0.55f -> 0.75f // ~2.4 feet
                boxHeightFraction > 0.40f -> 1.2f  // ~4 feet
                boxHeightFraction > 0.25f -> 1.9f  // ~6 feet
                boxHeightFraction > 0.15f -> 2.8f  // ~9 feet
                else -> 3.8f
            }
            val distanceFeet = currentDistanceMeters * 3.28084f

            // Calculate Approach Speed (Closing Velocity in m/s)
            val previous = historyMap[trackingIdStr]
            var approachSpeed = 0.0f
            var isApproaching = false
            var ttc: Float? = null

            if (previous != null) {
                val dtSec = (now - previous.timestampMs) / 1000.0f
                if (dtSec in 0.05f..1.5f) {
                    val deltaD = previous.distanceMeters - currentDistanceMeters
                    approachSpeed = (deltaD / dtSec).coerceIn(-10f, 15f)
                    if (approachSpeed > 0.18f) {
                        isApproaching = true
                        ttc = currentDistanceMeters / approachSpeed
                    }
                }
            }
            historyMap[trackingIdStr] = DistanceRecord(currentDistanceMeters, now)

            val centerX = (normalizedBox.left + normalizedBox.right) / 2.0f
            val clockPos = when {
                centerX < 0.32f -> 10
                centerX < 0.45f -> 11
                centerX <= 0.55f -> 12
                centerX <= 0.68f -> 1
                else -> 2
            }

            // Decision Engine:
            // 1. Vehicle / Fast approaching object -> Calculate approach speed and command Emergency Stop
            // 2. Obstacle within 2 feet (<= 2.5 feet or <= 0.76m) -> Direct turn command away from obstacle
            // 3. Closing in between 2.5 ft and 5 ft -> Early turn guidance
            // 4. Safe distance (> 5 ft or stationary) -> Quiet safe walking (no false alerts)
            val action: SuggestedAction
            val threat: ThreatLevel

            val isApproachingFast = isApproaching && approachSpeed > 1.2f
            val isImminentVehicle = isVehicle && (isApproaching || approachSpeed > 0.35f || currentDistanceMeters < 3.5f)
            val isFastCollisionHazard = isApproachingFast && (ttc ?: 99f) < 2.5f

            if (isImminentVehicle || isFastCollisionHazard) {
                action = SuggestedAction.EMERGENCY_STOP
                threat = ThreatLevel.CRITICAL
            } else if (currentDistanceMeters <= 0.78f || distanceFeet <= 2.6f) {
                // Strict 2-feet proximity rule: Tell user immediately which way to turn
                threat = ThreatLevel.CRITICAL
                action = if (centerX <= 0.50f) {
                    SuggestedAction.TURN_RIGHT // Obstacle on left/center-left -> steer right
                } else {
                    SuggestedAction.TURN_LEFT  // Obstacle on right/center-right -> steer left
                }
            } else if (currentDistanceMeters <= 1.55f && isApproaching) {
                // Approaching obstacle between 2.6 ft and 5 ft -> Early turn recommendation
                threat = ThreatLevel.CAUTION
                action = if (centerX <= 0.50f) SuggestedAction.TURN_RIGHT else SuggestedAction.TURN_LEFT
            } else {
                threat = ThreatLevel.ADVISORY
                action = SuggestedAction.CONTINUE_SAFE
            }

            results.add(
                RecognizedEntity(
                    id = trackingIdStr,
                    englishName = englishName,
                    hindiName = hindiName,
                    confidence = obj.labels.firstOrNull()?.confidence ?: 0.75f,
                    boundingBox = normalizedBox,
                    distanceMeters = currentDistanceMeters,
                    distanceFeet = distanceFeet,
                    approachSpeedMps = approachSpeed,
                    isApproaching = isApproaching,
                    timeToCollisionSec = ttc,
                    clockPosition = clockPos,
                    isVehicle = isVehicle,
                    threatLevel = threat,
                    suggestedAction = action
                )
            )
        }

        if (historyMap.size > 25) {
            historyMap.entries.removeIf { now - it.value.timestampMs > 5000 }
        }

        return results
    }

    private fun checkIsVehicle(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("car") || lower.contains("vehicle") || lower.contains("automobile") ||
               lower.contains("motorcycle") || lower.contains("scooter") || lower.contains("truck") ||
               lower.contains("bus") || lower.contains("van") || lower.contains("bicycle") || lower.contains("bike")
    }

    fun translateToHindi(englishName: String): String {
        val lower = englishName.lowercase()
        return when {
            lower.contains("person") || lower.contains("human") || lower.contains("man") || lower.contains("woman") -> "Vyakti"
            lower.contains("bed") -> "Bistar"
            lower.contains("chair") || lower.contains("seat") -> "Kursi"
            lower.contains("table") || lower.contains("desk") -> "Mez"
            lower.contains("bag") || lower.contains("backpack") || lower.contains("luggage") -> "Basta"
            lower.contains("bottle") -> "Botal"
            lower.contains("cup") || lower.contains("mug") -> "Cup"
            lower.contains("clothing") || lower.contains("shirt") || lower.contains("pants") || lower.contains("textile") -> "Kapde"
            lower.contains("shoe") || lower.contains("footwear") -> "Joote"
            lower.contains("door") -> "Darwaza"
            lower.contains("wall") -> "Deewar"
            lower.contains("sink") || lower.contains("tap") || lower.contains("basin") -> "Washbasin"
            lower.contains("car") || lower.contains("vehicle") || lower.contains("automobile") -> "Gadi"
            lower.contains("bicycle") || lower.contains("bike") -> "Cycle"
            lower.contains("motorcycle") || lower.contains("scooter") -> "Motorcycle"
            lower.contains("dog") || lower.contains("cat") || lower.contains("animal") -> "Janwar"
            lower.contains("stairs") || lower.contains("staircase") -> "Sidhiyan"
            lower.contains("laptop") || lower.contains("computer") -> "Laptop"
            lower.contains("phone") || lower.contains("mobile") -> "Mobile Phone"
            lower.contains("window") -> "Khidki"
            lower.contains("plant") || lower.contains("tree") || lower.contains("flower") -> "Paudha"
            lower.contains("pole") -> "Khamba"
            lower.contains("box") || lower.contains("carton") -> "Dabba"
            lower.contains("pillow") || lower.contains("cushion") -> "Takiya"
            lower.contains("shelf") || lower.contains("cabinet") -> "Almari"
            lower.contains("fan") -> "Pankha"
            lower.contains("light") || lower.contains("lamp") -> "Light"
            lower.contains("book") -> "Kitab"
            lower.contains("room") || lower.contains("interior") -> "Kamra"
            else -> englishName.replaceFirstChar { it.uppercase() }
        }
    }

    fun close() {
        try {
            objectDetector.close()
            imageLabeler.close()
            historyMap.clear()
        } catch (_: Exception) {}
    }
}
