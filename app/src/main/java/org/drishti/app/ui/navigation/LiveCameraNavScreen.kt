package org.drishti.app.ui.navigation

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.net.Uri
import android.os.Looper
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.drishti.app.ml.RealTimeAIObjectDetector
import org.drishti.app.ml.RecognizedEntity
import org.drishti.app.ml.SuggestedAction
import org.drishti.core.feedback.HapticManager
import org.drishti.core.feedback.SpeechManager
import org.drishti.core.model.AssistiveClass
import org.drishti.core.model.DetectedObstacle
import org.drishti.core.model.ThreatLevel
import org.drishti.core.model.TrackedObstacle
import org.drishti.core.model.Waypoint
import java.util.concurrent.Executors

private val PureBlack = Color(0xFF000000)
private val CriticalRed = Color(0xFFFF1744)
private val WarningOrange = Color(0xFFFF9100)
private val SafeGreen = Color(0xFF00E676)
private val SafetyCyan = Color(0xFF00E5FF)
private val HighContrastYellow = Color(0xFFFFEB3B)
private val TextWhite = Color(0xFFFFFFFF)
private val CardBg = Color(0xEE111318)

@SuppressLint("MissingPermission")
@Composable
fun LiveCameraNavScreen(
    speechManager: SpeechManager,
    hapticManager: HapticManager,
    activeRouteDestination: Waypoint? = null,
    onOpenObstacleDetails: (List<TrackedObstacle>) -> Unit,
    onStopNavigation: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val aiDetector = remember { RealTimeAIObjectDetector() }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var cameraProviderHolder by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var cameraInstance by remember { mutableStateOf<Camera?>(null) }
    var isPaused by remember { mutableStateOf(false) }
    var isFlashlightOn by remember { mutableStateOf(false) }
    var isDebugOverlayVisible by remember { mutableStateOf(false) }
    var lastDegradedAnnounceTime by remember { mutableLongStateOf(0L) }
    var currentDebugMetrics by remember { mutableStateOf(org.drishti.app.ml.DebugMetrics()) }

    var detectedEntities by remember { mutableStateOf<List<RecognizedEntity>>(emptyList()) }
    var sceneLabels by remember { mutableStateOf<List<String>>(emptyList()) }
    var lastAlertText by remember { mutableStateOf("Surroundings scan ho rahi hain...") }
    var lastSpokenTime by remember { mutableLongStateOf(0L) }
    var lastSpokenAction by remember { mutableStateOf<SuggestedAction?>(null) }

    // Darkness / Covered Camera Lens State
    var isCameraDarkOrCovered by remember { mutableStateOf(false) }
    var lastDarkAnnounceTime by remember { mutableLongStateOf(0L) }

    // Walk vs Stop State Machine
    var isStoppedByHazard by remember { mutableStateOf(false) }
    var lastHazardAnnounceTime by remember { mutableLongStateOf(0L) }
    var lastResumeWalkAnnounceTime by remember { mutableLongStateOf(0L) }
    var allDetectedEntities by remember { mutableStateOf<List<RecognizedEntity>>(emptyList()) }
    var lastClearPathAnnounceTime by remember { mutableLongStateOf(0L) }
    var clearFrameCount by remember { mutableStateOf(0) }
    var lastHapticVibrateTime by remember { mutableLongStateOf(0L) }

    // GPS & Compass State
    var userLat by remember { mutableDoubleStateOf(28.6139) }
    var userLon by remember { mutableDoubleStateOf(77.2090) }
    var hasUserLocation by remember { mutableStateOf(false) }
    var compassHeading by remember { mutableFloatStateOf(0f) }
    var lastTurnHapticTime by remember { mutableLongStateOf(0L) }

    // 1. Setup GPS Location Updates
    DisposableEffect(context) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    userLat = loc.latitude
                    userLon = loc.longitude
                    hasUserLocation = true
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {}

        onDispose {
            try {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            } catch (e: Exception) {}
        }
    }

    // 2. Setup Compass / Rotation Vector Sensor
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        val sensorListener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val orientationAngles = FloatArray(3)

            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)
                    val azimuthDegrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                    compassHeading = (azimuthDegrees + 360f) % 360f

                    // Bug 1 fix: Compute camera depression angle from rotation matrix R[8]
                    // 0° = looking straight ahead horizontally, + = looking down towards ground
                    val depressionDeg = org.drishti.core.vision.GeometryObstacleEngine.computeDepressionDegrees(rotationMatrix)
                    aiDetector.setPitchDegrees(depressionDeg)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager?.registerListener(sensorListener, rotationSensor, SensorManager.SENSOR_DELAY_UI)

        onDispose {
            sensorManager?.unregisterListener(sensorListener)
        }
    }

    // Navigation Geometry Calculations
    val distanceToDestinationMeters = remember(userLat, userLon, activeRouteDestination) {
        if (activeRouteDestination != null) {
            calculateDistanceMeters(userLat, userLon, activeRouteDestination.latitude, activeRouteDestination.longitude)
        } else 0.0
    }

    val targetBearingDegrees = remember(userLat, userLon, activeRouteDestination) {
        if (activeRouteDestination != null) {
            calculateBearingDegrees(userLat, userLon, activeRouteDestination.latitude, activeRouteDestination.longitude)
        } else 0f
    }

    val relativeAngleDegrees = remember(targetBearingDegrees, compassHeading) {
        (targetBearingDegrees - compassHeading + 360f) % 360f
    }

    // Determine Turn Maneuver Direction
    val (turnIcon, turnManeuverHi, turnManeuverEn, isTurnNeeded) = remember(
        distanceToDestinationMeters,
        relativeAngleDegrees,
        activeRouteDestination
    ) {
        if (activeRouteDestination == null) {
            Tuple4("📷", "AI Vision Assistance", "AI Vision Assistance", false)
        } else if (distanceToDestinationMeters <= 8.0) {
            Tuple4("🎯", "Aap manzil par pahunch gaye hain!", "You have arrived at destination!", false)
        } else if (relativeAngleDegrees in 315f..360f || relativeAngleDegrees in 0f..45f) {
            Tuple4("⬆️", "Seedhe chalein", "Head Straight", false)
        } else if (relativeAngleDegrees in 45f..135f) {
            Tuple4("➡️", "Daayein mudiye", "Turn Right", true)
        } else if (relativeAngleDegrees in 135f..225f) {
            Tuple4("🔄", "Peeche ghumein", "Turn Around", true)
        } else {
            Tuple4("⬅️", "Baayein mudiye", "Turn Left", true)
        }
    }

    // Periodic Turn Vibration Reminder (every 6 seconds if turn required)
    LaunchedEffect(turnManeuverHi, distanceToDestinationMeters) {
        val now = System.currentTimeMillis()
        if (activeRouteDestination != null && distanceToDestinationMeters <= 8.0) {
            hapticManager.vibrateArrival()
            speechManager.speakGuidance("Aap apni manzil par pahunch gaye hain! Yatra samapt.", flush = true)
        } else if (isTurnNeeded && (now - lastTurnHapticTime > 6000L)) {
            lastTurnHapticTime = now
            if (relativeAngleDegrees in 45f..135f) {
                hapticManager.vibrateTurnRight()
            } else if (relativeAngleDegrees in 225f..315f) {
                hapticManager.vibrateTurnLeft()
            } else if (relativeAngleDegrees in 135f..225f) {
                hapticManager.vibrateOffRoute()
            }
        }
    }

    // Toggle physical flashlight/torch
    LaunchedEffect(isFlashlightOn) {
        try {
            cameraInstance?.cameraControl?.enableTorch(isFlashlightOn)
        } catch (e: Exception) {}
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraInstance?.cameraControl?.enableTorch(false)
                cameraProviderHolder?.unbindAll()
            } catch (e: Exception) {}
            cameraExecutor.shutdown()
            aiDetector.close()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        // Single Tap: Announce complete Navigation & Obstacle Status aloud
                        hapticManager.vibrateClick()
                        val isHi = speechManager.currentLanguage == "hi"
                        val sb = StringBuilder()

                        // 1. Check Camera Darkness / Lens Covered
                        if (isCameraDarkOrCovered) {
                            val msg = if (isHi) "Camera ke aage andhera hai ya lens dhaka hua hai. Kripya phone ko roshni me layein ya torch on karein." else "Camera lens is dark or covered. Please move into light or turn on flashlight."
                            speechManager.speakGuidance(msg, flush = true)
                            return@detectTapGestures
                        }

                        // 2. Navigation status
                        if (activeRouteDestination != null) {
                            val distStr = if (distanceToDestinationMeters >= 1000) {
                                String.format("%.1f km", distanceToDestinationMeters / 1000.0)
                            } else {
                                "${distanceToDestinationMeters.toInt()} meter"
                            }
                            if (distanceToDestinationMeters <= 8.0) {
                                sb.append(if (isHi) "Aap apni manzil ${activeRouteDestination.label} par pahunch gaye hain. " else "Arrived at ${activeRouteDestination.label}. ")
                            } else {
                                sb.append(if (isHi) "Manzil ${activeRouteDestination.label} $distStr baki hai. $turnManeuverHi. " else "${activeRouteDestination.label} is $distStr away. $turnManeuverEn. ")
                            }
                        }

                        // 3. Obstacle / Safe walking status
                        if (isStoppedByHazard) {
                            sb.append(if (isHi) "Rukiye! Aage khatra hai, chalna band rakhein." else "Stop! Obstacle ahead, please stay stopped.")
                        } else if (detectedEntities.isNotEmpty()) {
                            val nearest = detectedEntities.minByOrNull { it.distanceMeters }!!
                            val distFt = (nearest.distanceFeet * 10).toInt() / 10.0
                            when (nearest.suggestedAction) {
                                SuggestedAction.TURN_RIGHT -> sb.append(if (isHi) "Aage $distFt feet par ${nearest.hindiName} hai, thoda daayein chalein." else "${nearest.englishName} $distFt feet ahead, steer right.")
                                SuggestedAction.TURN_LEFT -> sb.append(if (isHi) "Aage $distFt feet par ${nearest.hindiName} hai, thoda baayein chalein." else "${nearest.englishName} $distFt feet ahead, steer left.")
                                SuggestedAction.EMERGENCY_STOP -> {
                                    if (nearest.isPedestrian) sb.append(if (isHi) "Rukiye! Samne insaan khada hai!" else "Stop! Person right ahead!")
                                    else if (nearest.isVehicle) sb.append(if (isHi) "Rukiye! Samne tez gadi aa rahi hai!" else "Stop! Vehicle approaching fast!")
                                    else sb.append(if (isHi) "Rukiye! Samne ${nearest.hindiName} bohot paas hai!" else "Stop! ${nearest.englishName} very close ahead!")
                                }
                                SuggestedAction.AHEAD_BLOCKED -> sb.append(if (isHi) "Rukiye! Raste me ${nearest.hindiName} hai." else "Stop! ${nearest.englishName} is blocking path.")
                                SuggestedAction.CONTINUE_SAFE -> {
                                    // Silence - Never say path clear / rasta saaf hai
                                }
                            }
                        } else {
                            // Silence - No detected threat. Never announce "path clear" / "rasta saaf hai"
                        }

                        val toSpeak = sb.toString().trim()
                        if (toSpeak.isNotEmpty()) {
                            speechManager.speakGuidance(toSpeak, flush = true)
                        }
                    },
                    onDoubleTap = {
                        // Double Tap: Open obstacle detail modal
                        hapticManager.vibrateClick()
                        val trackedList = detectedEntities.mapIndexed { idx, entity ->
                            TrackedObstacle(
                                trackId = (entity.englishName.hashCode().toLong() + idx),
                                obstacle = DetectedObstacle(
                                    assistiveClass = AssistiveClass.GENERIC_OBSTACLE,
                                    confidence = entity.confidence,
                                    boundingBox = entity.boundingBox,
                                    timestampMs = System.currentTimeMillis()
                                ),
                                estimatedDistanceMeters = entity.distanceMeters,
                                azimuthDegrees = (entity.clockPosition - 12) * 30.0f,
                                clockPosition = entity.clockPosition,
                                timeToCollisionSeconds = entity.distanceMeters / 1.2f,
                                threatLevel = entity.threatLevel
                            )
                        }
                        onOpenObstacleDetails(trackedList)
                    }
                )
            }
    ) {
        // 1. Camera Preview
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        cameraProviderHolder = cameraProvider
                        cameraProvider.unbindAll()

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            if (!isPaused) {
                                aiDetector.processImageProxy(imageProxy) { entities, allEntities, labels, isDarkOrCovered, degradedNotice ->
                                    val now = System.currentTimeMillis()
                                    val isHi = speechManager.currentLanguage == "hi"
                                    currentDebugMetrics = aiDetector.latestDebugMetrics

                                    // Degraded / Watchdog Alert
                                    if (degradedNotice != null && now - lastDegradedAnnounceTime > 5000L) {
                                        lastDegradedAnnounceTime = now
                                        hapticManager.vibrateCaution()
                                        speechManager.speakGuidance(
                                            if (isHi) "Dhyan dein, cane ka prayog karein." else "System degraded, use cane carefully.",
                                            flush = true
                                        )
                                    }

                                    // Check Darkness / Camera Lens Covered
                                    if (isDarkOrCovered) {
                                        isCameraDarkOrCovered = true
                                        detectedEntities = emptyList()
                                        allDetectedEntities = emptyList()
                                        lastAlertText = if (isHi) "Camera ke aage andhera hai" else "Camera lens is dark or covered"

                                        if (now - lastDarkAnnounceTime > 4000L) {
                                            lastDarkAnnounceTime = now
                                            lastSpokenTime = now
                                            hapticManager.vibrateCaution()
                                            speechManager.speakGuidance(
                                                if (isHi) "Camera ke aage andhera hai, kripya roshni me jayein ya torch on karein."
                                                else "Camera lens is dark or covered. Please move into light or turn on flashlight.",
                                                flush = true
                                            )
                                        }
                                        return@processImageProxy
                                    }

                                    // Recover from dark if light returned
                                    if (isCameraDarkOrCovered) {
                                        isCameraDarkOrCovered = false
                                        speechManager.speakGuidance(
                                            if (isHi) "Roshni wapas aa gayi hai, scanning chalu hai." else "Lighting restored, scanning active.",
                                            flush = true
                                        )
                                    }

                                    detectedEntities = entities
                                    allDetectedEntities = allEntities
                                    sceneLabels = labels

                                    // Check Strict 2-Meter Collision & Emergency Threat
                                    val criticalOrBlockedEntity = entities.firstOrNull {
                                        it.suggestedAction == SuggestedAction.EMERGENCY_STOP ||
                                        it.suggestedAction == SuggestedAction.AHEAD_BLOCKED ||
                                        it.threatLevel == ThreatLevel.CRITICAL ||
                                        it.isStairsDownward ||
                                        it.isPotholeOrDrain ||
                                        (it.distanceMeters <= 2.0f && (it.isApproachingUser || (it.inCentralCorridor && it.relativeClosingSpeed > 0.20f))) ||
                                        (it.isVehicle && it.isApproaching && it.distanceMeters < 3.5f)
                                    }

                                    if (criticalOrBlockedEntity != null) {
                                        clearFrameCount = 0

                                        // Crisp 3-4 word alert with escape direction
                                        val warningSpeech = when {
                                            criticalOrBlockedEntity.isStairsDownward -> {
                                                if (isHi) "Rukiye! Aage sidhiyan neeche ja rahi hain." else "Stop! Stairs going down ahead."
                                            }
                                            criticalOrBlockedEntity.isPotholeOrDrain -> {
                                                if (isHi) "Rukiye! Aage khadda hai." else "Stop! Pothole or drain ahead."
                                            }
                                            criticalOrBlockedEntity.isVehicle && criticalOrBlockedEntity.isApproaching -> {
                                                if (isHi) "Rukiye! Gadi aa rahi hai!" else "Stop! Vehicle approaching!"
                                            }
                                            criticalOrBlockedEntity.isPedestrian && criticalOrBlockedEntity.isApproachingUser -> {
                                                val side = if (criticalOrBlockedEntity.spatialZone == "Left") "daayein chalein" else "baayein chalein"
                                                val sideEn = if (criticalOrBlockedEntity.spatialZone == "Left") "step right" else "step left"
                                                if (isHi) "Insaan aa raha, $side." else "Person approaching, $sideEn."
                                            }
                                            criticalOrBlockedEntity.distanceMeters <= 2.0f -> {
                                                val side = if (criticalOrBlockedEntity.spatialZone == "Left") "daayein chalein" else "baayein chalein"
                                                val sideEn = if (criticalOrBlockedEntity.spatialZone == "Left") "step right" else "step left"
                                                if (isHi) "${criticalOrBlockedEntity.hindiName} samne, $side." else "${criticalOrBlockedEntity.englishName} ahead, $sideEn."
                                            }
                                            else -> {
                                                if (isHi) "Rukiye! Aage turant khatra hai." else "Stop! Immediate hazard ahead."
                                            }
                                        }

                                        lastAlertText = warningSpeech

                                        if (!isStoppedByHazard || (now - lastHazardAnnounceTime > 2200L)) {
                                            isStoppedByHazard = true
                                            lastHazardAnnounceTime = now
                                            lastSpokenTime = now
                                            lastSpokenAction = SuggestedAction.EMERGENCY_STOP

                                            // Trigger distinct 2-meter vibration pattern
                                            if (criticalOrBlockedEntity.isMoving && criticalOrBlockedEntity.isApproachingUser && criticalOrBlockedEntity.distanceMeters <= 2.0f) {
                                                hapticManager.vibrateApproachingHazard2M()
                                            } else if (!criticalOrBlockedEntity.isMoving && criticalOrBlockedEntity.distanceMeters <= 2.0f) {
                                                hapticManager.vibrateImminentStaticCollision2M()
                                            } else if (criticalOrBlockedEntity.isStairsDownward || criticalOrBlockedEntity.isPotholeOrDrain) {
                                                hapticManager.vibrateDropOff()
                                            } else {
                                                hapticManager.vibrateCriticalStop()
                                            }

                                            speechManager.speakCritical(warningSpeech)
                                        }
                                    } else {
                                        // Path is clear of emergency stops
                                        if (isStoppedByHazard) {
                                            isStoppedByHazard = false
                                            lastAlertText = ""
                                            hapticManager.vibrateClick()
                                        }

                                        // Strict 2-Meter Haptic Dispatch on Warning Level
                                        val closeApproaching = entities.firstOrNull {
                                            it.distanceMeters <= 2.0f && it.isMoving && it.isApproachingUser
                                        }
                                        val closeStaticCollision = entities.firstOrNull {
                                            it.distanceMeters <= 2.0f && !it.isMoving && it.inCentralCorridor && it.relativeClosingSpeed > 0.20f
                                        }

                                        if (closeApproaching != null && (now - lastHapticVibrateTime > 1500L)) {
                                            lastHapticVibrateTime = now
                                            hapticManager.vibrateApproachingHazard2M()
                                        } else if (closeStaticCollision != null && (now - lastHapticVibrateTime > 1800L)) {
                                            lastHapticVibrateTime = now
                                            hapticManager.vibrateImminentStaticCollision2M()
                                        }

                                        // Handle Directional Steering (Turn Left/Right around verified obstacles)
                                        val steerEntity = entities.firstOrNull {
                                            it.suggestedAction == SuggestedAction.TURN_LEFT ||
                                            it.suggestedAction == SuggestedAction.TURN_RIGHT
                                        }

                                        if (steerEntity != null) {
                                            clearFrameCount = 0
                                            val action = steerEntity.suggestedAction
                                            val steerMsg = if (steerEntity.isPedestrian) {
                                                if (action == SuggestedAction.TURN_LEFT) {
                                                    if (isHi) "Aage insaan hai, thoda baayein chalein" else "Person ahead, move slightly left"
                                                } else {
                                                    if (isHi) "Aage insaan hai, thoda daayein chalein" else "Person ahead, move slightly right"
                                                }
                                            } else {
                                                if (action == SuggestedAction.TURN_LEFT) {
                                                    if (isHi) "Thoda baayein chalein" else "Move slightly left"
                                                } else {
                                                    if (isHi) "Thoda daayein chalein" else "Move slightly right"
                                                }
                                            }
                                            lastAlertText = steerMsg

                                            if (lastSpokenAction != action || (now - lastSpokenTime > 3500L)) {
                                                lastSpokenTime = now
                                                lastSpokenAction = action
                                                if (action == SuggestedAction.TURN_LEFT) {
                                                    hapticManager.vibrateTurnLeft()
                                                } else {
                                                    hapticManager.vibrateTurnRight()
                                                }
                                                speechManager.speakGuidance(steerMsg)
                                            }
                                        } else {
                                            // Proactive "Path Clear -> Walk Ahead" Announcement
                                            val isCorridorClear = entities.none {
                                                it.threatLevel == ThreatLevel.CRITICAL ||
                                                it.threatLevel == ThreatLevel.WARNING ||
                                                it.distanceMeters <= 2.0f
                                            }

                                            if (isCorridorClear) {
                                                clearFrameCount++
                                            } else {
                                                clearFrameCount = 0
                                            }

                                            // After 3+ consecutive clear frames (≈0.5s), announce "aage badhiye" (cooldown 9s)
                                            if (clearFrameCount >= 3 && (now - lastClearPathAnnounceTime > 9000L)) {
                                                lastClearPathAnnounceTime = now
                                                clearFrameCount = 0
                                                val clearMsg = if (isHi) "Raasta saaf hai, aage badhiye." else "Path is clear, walk ahead."
                                                speechManager.speakGuidance(clearMsg)
                                            }

                                            // Normal verified clear path display
                                            if (activeRouteDestination != null) {
                                                lastAlertText = "$turnIcon $turnManeuverHi"
                                            } else if (entities.isNotEmpty()) {
                                                val nearest = entities.minByOrNull { it.distanceMeters }!!
                                                val distFt = (nearest.distanceFeet * 10).toInt() / 10.0
                                                val icon = if (nearest.isPedestrian) "🧍" else if (nearest.isVehicle) "🚗" else "⚠️"
                                                lastAlertText = "$icon ${nearest.hindiName} • ${distFt}ft (${nearest.spatialZone})"
                                            } else if (labels.isNotEmpty()) {
                                                val labelDesc = labels.take(2).map { aiDetector.translateToHindi(it) }.joinToString(", ")
                                                lastAlertText = "Dikh raha hai: $labelDesc"
                                            } else {
                                                lastAlertText = if (isCorridorClear) "🟢 Raasta saaf hai" else ""
                                            }
                                        }
                                    }
                                }
                                } else {
                                    imageProxy.close()
                                }
                            }

                        val boundCamera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                        cameraInstance = boundCamera
                    } catch (e: Exception) {}
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        // 2. High-Contrast Assistive Canvas Overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Walking Corridor Safe Guide Lines
            val corridorColor = when {
                isCameraDarkOrCovered -> WarningOrange.copy(alpha = 0.5f)
                isStoppedByHazard -> CriticalRed.copy(alpha = 0.6f)
                else -> SafeGreen.copy(alpha = 0.5f)
            }
            drawLine(
                color = corridorColor,
                start = Offset(w * 0.22f, h),
                end = Offset(w * 0.38f, h * 0.45f),
                strokeWidth = 3.dp.toPx()
            )
            drawLine(
                color = corridorColor,
                start = Offset(w * 0.78f, h),
                end = Offset(w * 0.62f, h * 0.45f),
                strokeWidth = 3.dp.toPx()
            )

            // Draw All Detected Objects with Color-Coded Bounding Boxes
            if (!isPaused && !isCameraDarkOrCovered) {
                val textPaint = Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 40f
                    isFakeBoldText = true
                    setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
                }
                val bgPaint = Paint().apply {
                    color = android.graphics.Color.argb(220, 18, 20, 26)
                    style = Paint.Style.FILL
                }

                // Render ALL detected objects so nothing is hidden from the user/companion
                allDetectedEntities.forEach { entity ->
                    val box = entity.boundingBox
                    val boxColor = when (entity.threatLevel) {
                        ThreatLevel.CRITICAL -> CriticalRed
                        ThreatLevel.WARNING -> WarningOrange
                        ThreatLevel.CAUTION -> HighContrastYellow
                        ThreatLevel.ADVISORY -> SafetyCyan
                        ThreatLevel.NONE -> Color(0x66AAAAAA) // Dim gray for safe background objects
                    }
                    val strokeWidth = if (entity.threatLevel == ThreatLevel.NONE) 2.dp else 4.dp

                    val left = box.left * w
                    val top = box.top * h
                    val right = box.right * w
                    val bottom = box.bottom * h

                    // Outline
                    drawRect(
                        color = boxColor,
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        style = Stroke(width = strokeWidth.toPx())
                    )

                    // Tag above bounding box (for threats or close objects <= 3m)
                    if (entity.threatLevel != ThreatLevel.NONE || entity.distanceMeters <= 3.0f) {
                        val distFt = (entity.distanceFeet * 10).toInt() / 10.0
                        val speedText = if (entity.approachSpeedMps > 0.4f) String.format(" • %.1f m/s", entity.approachSpeedMps) else ""
                        val icon = if (entity.isPedestrian) "🧍 " else if (entity.isVehicle) "🚗 " else ""
                        val tagText = "$icon${entity.hindiName} • ${distFt}ft (${entity.movementDirection}$speedText)"

                        val tagWidth = textPaint.measureText(tagText) + 24f
                        val tagTop = (top - 54f).coerceAtLeast(10f)

                        drawContext.canvas.nativeCanvas.drawRoundRect(
                            left,
                            tagTop,
                            left + tagWidth,
                            tagTop + 50f,
                            12f,
                            12f,
                            bgPaint
                        )

                        drawContext.canvas.nativeCanvas.drawText(
                            tagText,
                            left + 12f,
                            tagTop + 36f,
                            textPaint
                        )
                    }
                }
            }
        }

        // 3. Top Header Bar & Live Navigation Turn-by-Turn Card
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp)
        ) {
            // Top Controls Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Exit / Back Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(CriticalRed)
                        .clickable {
                            hapticManager.vibrateClick()
                            speechManager.speakGuidance("Camera navigation band kar rahe hain.", flush = true)
                            onStopNavigation()
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .semantics {
                            contentDescription = "Navigation band karein aur pichhli screen par jayein"
                            role = Role.Button
                        }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = TextWhite, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("EXIT", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Torch / Flashlight Toggle
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (isFlashlightOn) HighContrastYellow else CardBg)
                            .border(1.dp, SafetyCyan, CircleShape)
                            .clickable {
                                isFlashlightOn = !isFlashlightOn
                                hapticManager.vibrateClick()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isFlashlightOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                            contentDescription = "Flashlight Toggle",
                            tint = if (isFlashlightOn) PureBlack else TextWhite,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Developer Debug Overlay Toggle
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (isDebugOverlayVisible) HighContrastYellow else CardBg)
                            .border(1.dp, SafetyCyan, CircleShape)
                            .clickable {
                                isDebugOverlayVisible = !isDebugOverlayVisible
                                hapticManager.vibrateClick()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "DBG",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDebugOverlayVisible) PureBlack else TextWhite
                        )
                    }
                }
            }

            // Developer Debug HUD Overlay
            if (isDebugOverlayVisible) {
                Spacer(modifier = Modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xDD0A0C10))
                        .border(1.dp, SafetyCyan, RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "🔬 PIPELINE DEBUG OVERLAY",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SafetyCyan
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Latency: Depth ${currentDebugMetrics.depthLatencyMs}ms | Det ${currentDebugMetrics.detectorLatencyMs}ms | Track ${currentDebugMetrics.trackingLatencyMs}ms | Total ${currentDebugMetrics.totalLatencyMs}ms",
                        fontSize = 11.sp,
                        color = if (currentDebugMetrics.totalLatencyMs > 150) CriticalRed else SafeGreen
                    )
                    Text(
                        text = "Pitch: ${currentDebugMetrics.currentPitchDeg.toInt()}° (Safe: ${currentDebugMetrics.isPitchValid}) | Luma: ${currentDebugMetrics.averageLuma.toInt()}",
                        fontSize = 11.sp,
                        color = TextWhite
                    )
                    Text(
                        text = "Corridor: ${currentDebugMetrics.corridorStatus} | Active Tracks: ${currentDebugMetrics.activeTrackCount}",
                        fontSize = 11.sp,
                        color = HighContrastYellow
                    )
                    Text(
                        text = "Frames In: ${currentDebugMetrics.framesIn} | Rej Dk: ${currentDebugMetrics.rejectedDark} | Rej Pitch: ${currentDebugMetrics.rejectedPitch}",
                        fontSize = 11.sp,
                        color = if (currentDebugMetrics.rejectedDark > 0 || currentDebugMetrics.rejectedPitch > 0) HighContrastYellow else TextWhite
                    )
                    Text(
                        text = "ML Box: ${currentDebugMetrics.mlBoxes} -> AftBL: ${currentDebugMetrics.afterBlacklist} -> WLCand: ${currentDebugMetrics.whitelistCandidates} -> Geo: ${currentDebugMetrics.geoHazards} -> Conf: ${currentDebugMetrics.confirmed} -> Trk: ${currentDebugMetrics.tracked} | Spoken: ${currentDebugMetrics.alertsSpoken}",
                        fontSize = 10.sp,
                        color = SafetyCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Navigation Destination & Turn Guidance HUD Card (if Destination Active)
            if (activeRouteDestination != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardBg)
                        .border(2.dp, SafetyCyan, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "MANZIL: ${activeRouteDestination.label.uppercase()}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = HighContrastYellow,
                                maxLines = 1
                            )
                            val distText = if (distanceToDestinationMeters >= 1000) {
                                String.format("%.1f km baki", distanceToDestinationMeters / 1000.0)
                            } else {
                                "${distanceToDestinationMeters.toInt()}m baki (~${(distanceToDestinationMeters / 0.75).toInt()} steps)"
                            }
                            Text(
                                text = distText,
                                fontSize = 12.sp,
                                color = TextWhite.copy(alpha = 0.85f)
                            )
                        }

                        // Google Maps Walking Navigation Shortcut
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E88E5))
                                .clickable {
                                    hapticManager.vibrateClick()
                                    val uri = Uri.parse("google.navigation:q=${activeRouteDestination.latitude},${activeRouteDestination.longitude}&mode=w")
                                    val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                                        setPackage("com.google.android.apps.maps")
                                    }
                                    try {
                                        context.startActivity(mapIntent)
                                    } catch (e: Exception) {
                                        val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${activeRouteDestination.latitude},${activeRouteDestination.longitude}&travelmode=walking"))
                                        context.startActivity(fallback)
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "🗺️ Maps",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Large Turn Indicator Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (distanceToDestinationMeters <= 8.0) SafeGreen.copy(alpha = 0.3f)
                                else Color(0xFF263238)
                            )
                            .border(
                                1.5.dp,
                                if (distanceToDestinationMeters <= 8.0) SafeGreen else SafetyCyan,
                                RoundedCornerShape(10.dp)
                            )
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = turnIcon,
                                fontSize = 24.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$turnManeuverHi ($turnManeuverEn)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (distanceToDestinationMeters <= 8.0) SafeGreen else SafetyCyan,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // 4. Bottom Live Walk State Banner ("Rukna kab hai vs Chalna kab hai with vibration & Darkness detection")
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val bannerBg = when {
                isCameraDarkOrCovered -> Color(0xFF23172E)
                isStoppedByHazard -> CriticalRed
                detectedEntities.any { it.suggestedAction == SuggestedAction.TURN_LEFT || it.suggestedAction == SuggestedAction.TURN_RIGHT } -> WarningOrange
                else -> Color(0xFF00332C)
            }

            val borderColor = when {
                isCameraDarkOrCovered -> HighContrastYellow
                isStoppedByHazard -> CriticalRed
                detectedEntities.any { it.suggestedAction == SuggestedAction.TURN_LEFT || it.suggestedAction == SuggestedAction.TURN_RIGHT } -> WarningOrange
                else -> SafeGreen
            }

            val headline = when {
                isCameraDarkOrCovered -> "🌑 CAMERA KE AAGE ANDHERA HAI"
                isStoppedByHazard -> "🛑 RUKIYE! AAGE KHATRA HAI"
                detectedEntities.any { it.suggestedAction == SuggestedAction.TURN_LEFT } -> "⬅️ THODA BAAYEIN CHALEIN"
                detectedEntities.any { it.suggestedAction == SuggestedAction.TURN_RIGHT } -> "➡️ THODA DAAYEIN CHALEIN"
                detectedEntities.isEmpty() && allDetectedEntities.isNotEmpty() -> "🟢 AAGE BADHIYE • RAASTA SAAF HAI"
                detectedEntities.isEmpty() -> "🟢 DRISHTI ACTIVE • RAASTA SAAF HAI"
                else -> "🟢 DRISHTI ACTIVE"
            }

            val subtext = when {
                isCameraDarkOrCovered -> "Lens dhaka hua hai ya andhera hai • Torch on karein"
                isStoppedByHazard -> lastAlertText
                detectedEntities.isEmpty() && allDetectedEntities.isNotEmpty() -> {
                    val safeCount = allDetectedEntities.size
                    if (activeRouteDestination != null) "$turnIcon $turnManeuverHi • $safeCount background objects"
                    else "$safeCount chizein dikh rahi hain • Koi khatra nahi"
                }
                detectedEntities.isEmpty() -> {
                    if (activeRouteDestination != null) "$turnIcon $turnManeuverHi"
                    else "Path is clear ahead"
                }
                else -> lastAlertText
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(bannerBg)
                    .border(3.dp, borderColor, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = headline,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = TextWhite,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = subtext,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HighContrastYellow,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "1-Tap: Voice Status • 2-Tap: Hazard List • Auto Vibration Active",
                        fontSize = 11.sp,
                        color = TextWhite.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private fun calculateBearingDegrees(userLat: Double, userLon: Double, destLat: Double, destLon: Double): Float {
    val lat1 = Math.toRadians(userLat)
    val lon1 = Math.toRadians(userLon)
    val lat2 = Math.toRadians(destLat)
    val lon2 = Math.toRadians(destLon)
    val dLon = lon2 - lon1
    val y = Math.sin(dLon) * Math.cos(lat2)
    val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
    val bearing = Math.toDegrees(Math.atan2(y, x)).toFloat()
    return (bearing + 360f) % 360f
}

private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0 // meters
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return r * c
}

private data class Tuple4<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

