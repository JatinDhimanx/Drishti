package org.drishti.app.ui.navigation

import android.content.Intent
import android.graphics.Paint
import android.net.Uri
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import org.drishti.app.ml.RealTimeAIObjectDetector
import org.drishti.app.ml.RecognizedEntity
import org.drishti.app.ml.SuggestedAction
import org.drishti.core.feedback.HapticManager
import org.drishti.core.feedback.SpeechManager
import org.drishti.core.model.AssistiveClass
import org.drishti.core.model.DetectedObstacle
import org.drishti.core.model.ThreatLevel
import org.drishti.core.model.TrackedObstacle
import java.util.concurrent.Executors

private val PureBlack = Color(0xFF000000)
private val CriticalRed = Color(0xFFFF1744)
private val WarningOrange = Color(0xFFFF9100)
private val SafetyCyan = Color(0xFF00E5FF)
private val HighContrastYellow = Color(0xFFFFEB3B)
private val TextWhite = Color(0xFFFFFFFF)
private val CardBg = Color(0xDD111318)

@Composable
fun LiveCameraNavScreen(
    speechManager: SpeechManager,
    hapticManager: HapticManager,
    activeRouteName: String? = null,
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

    var detectedEntities by remember { mutableStateOf<List<RecognizedEntity>>(emptyList()) }
    var sceneLabels by remember { mutableStateOf<List<String>>(emptyList()) }
    var lastAlertText by remember { mutableStateOf("Scanning surroundings...") }
    var lastSpokenTime by remember { mutableStateOf(0L) }
    var lastSpokenAlert by remember { mutableStateOf("") }
    var lastSpokenAction by remember { mutableStateOf<SuggestedAction?>(null) }
    val currentTurnManeuver = activeRouteName?.let { "Heading toward $it (Follow safe walkway)" }

    // Toggle physical camera flashlight/torch
    LaunchedEffect(isFlashlightOn) {
        try {
            cameraInstance?.cameraControl?.enableTorch(isFlashlightOn)
        } catch (_: Exception) {}
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraInstance?.cameraControl?.enableTorch(false)
                cameraProviderHolder?.unbindAll()
            } catch (_: Exception) {}
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
                        // Single Tap: Intelligently report the exact situation aloud
                        hapticManager.vibrateClick()
                        val feedback = if (detectedEntities.isNotEmpty()) {
                            val nearest = detectedEntities.minByOrNull { it.distanceMeters }!!
                            val distFt = (nearest.distanceFeet * 10).toInt() / 10.0
                            when (nearest.suggestedAction) {
                                SuggestedAction.TURN_RIGHT -> "Aage $distFt feet par ${nearest.hindiName} hai, thoda daayein mudiye."
                                SuggestedAction.TURN_LEFT -> "Aage $distFt feet par ${nearest.hindiName} hai, thoda baayein mudiye."
                                SuggestedAction.EMERGENCY_STOP -> {
                                    if (nearest.isVehicle) "Rukiye! Samne se tez raftaar gadi aa rahi hai! Wahi ruk jaiye!"
                                    else "Rukiye! Samne ${nearest.hindiName} bohot paas aa raha hai!"
                                }
                                SuggestedAction.AHEAD_BLOCKED -> "Rukiye! Aage $distFt feet par ${nearest.hindiName} rasta roke hue hai."
                                SuggestedAction.CONTINUE_SAFE -> "Rasta khula hai, aage $distFt feet par ${nearest.hindiName} hai."
                            }
                        } else if (sceneLabels.isNotEmpty()) {
                            val items = sceneLabels.take(3).map { aiDetector.translateToHindi(it) }.joinToString(", ")
                            "Samne $items dikh raha hai, rasta chalne yogya hai."
                        } else {
                            if (speechManager.currentLanguage == "hi") "Rasta bilkul saaf hai, aap aage badh sakte hain."
                            else "Path is clear ahead, continue walking."
                        }
                        speechManager.speakGuidance(feedback, flush = true)
                    },
                    onDoubleTap = {
                        // Double Tap: Open obstacle detail modal
                        hapticManager.vibrateClick()
                        val trackedList = detectedEntities.mapIndexed { idx, entity ->
                            TrackedObstacle(
                                trackId = (entity.englishName.hashCode().toLong() + idx),
                                obstacle = DetectedObstacle(
                                    assistiveClass = AssistiveClass.BARRICADE_FENCE,
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
        // 1. Physical Camera PreviewView
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
                                aiDetector.processImageProxy(imageProxy) { entities, labels ->
                                    detectedEntities = entities
                                    sceneLabels = labels

                                    val now = System.currentTimeMillis()
                                    if (entities.isNotEmpty()) {
                                        // Priority 1: Fast vehicle or impending collision
                                        // Priority 2: Obstacle within 2 feet requiring turn
                                        // Priority 3: Nearest entity
                                        val urgentEntity = entities.firstOrNull { it.suggestedAction == SuggestedAction.EMERGENCY_STOP }
                                            ?: entities.firstOrNull { it.suggestedAction == SuggestedAction.TURN_LEFT || it.suggestedAction == SuggestedAction.TURN_RIGHT || it.suggestedAction == SuggestedAction.AHEAD_BLOCKED }
                                            ?: entities.minByOrNull { it.distanceMeters }
                                            ?: entities.first()

                                        val distFt = (urgentEntity.distanceFeet * 10).toInt() / 10.0

                                        val (alertSpeech, actionType) = when (urgentEntity.suggestedAction) {
                                            SuggestedAction.EMERGENCY_STOP -> {
                                                if (urgentEntity.isVehicle) {
                                                    val speedKmh = (urgentEntity.approachSpeedMps * 3.6f).toInt()
                                                    val speedDesc = if (speedKmh > 5) " tez raftaar" else ""
                                                    "Rukiye! Wahi ruk jaiye! Samne se${speedDesc} gadi aa rahi hai!" to SuggestedAction.EMERGENCY_STOP
                                                } else {
                                                    "Rukiye! Wahi ruk jaiye! Samne se ${urgentEntity.hindiName} tez aa raha hai!" to SuggestedAction.EMERGENCY_STOP
                                                }
                                            }
                                            SuggestedAction.TURN_RIGHT -> {
                                                "Aage 2 feet par ${urgentEntity.hindiName} hai, thoda daayein mudiye" to SuggestedAction.TURN_RIGHT
                                            }
                                            SuggestedAction.TURN_LEFT -> {
                                                "Aage 2 feet par ${urgentEntity.hindiName} hai, thoda baayein mudiye" to SuggestedAction.TURN_LEFT
                                            }
                                            SuggestedAction.AHEAD_BLOCKED -> {
                                                "Aage 2 feet par ${urgentEntity.hindiName} rasta roke hue hai, thoda daayein mudiye" to SuggestedAction.TURN_RIGHT
                                            }
                                            SuggestedAction.CONTINUE_SAFE -> {
                                                "Aage $distFt feet par ${urgentEntity.hindiName} hai (Rasta khula hai)" to SuggestedAction.CONTINUE_SAFE
                                            }
                                        }

                                        lastAlertText = alertSpeech

                                        // Intelligent Speech Management:
                                        // 1. Only speak if immediate guidance (turn / stop) is required
                                        // 2. Emergency stop repeats every 2.2s if imminent threat persists
                                        // 3. Directional turn triggers immediately on direction change, or repeats every 3.8s
                                        if (actionType != SuggestedAction.CONTINUE_SAFE) {
                                            val shouldSpeak = when (actionType) {
                                                SuggestedAction.EMERGENCY_STOP -> {
                                                    lastSpokenAction != SuggestedAction.EMERGENCY_STOP || (now - lastSpokenTime > 2200)
                                                }
                                                SuggestedAction.TURN_LEFT, SuggestedAction.TURN_RIGHT, SuggestedAction.AHEAD_BLOCKED -> {
                                                    lastSpokenAction != actionType || (now - lastSpokenTime > 3800)
                                                }
                                                else -> false
                                            }

                                            if (shouldSpeak) {
                                                lastSpokenTime = now
                                                lastSpokenAction = actionType
                                                lastSpokenAlert = alertSpeech

                                                when (actionType) {
                                                    SuggestedAction.EMERGENCY_STOP, SuggestedAction.AHEAD_BLOCKED -> {
                                                        hapticManager.vibrateCriticalStop()
                                                        speechManager.speakCritical(alertSpeech)
                                                    }
                                                    SuggestedAction.TURN_LEFT -> {
                                                        hapticManager.vibrateTurnLeft()
                                                        speechManager.speakGuidance(alertSpeech)
                                                    }
                                                    SuggestedAction.TURN_RIGHT -> {
                                                        hapticManager.vibrateTurnRight()
                                                        speechManager.speakGuidance(alertSpeech)
                                                    }
                                                    else -> {}
                                                }
                                            }
                                        } else {
                                            if (now - lastSpokenTime > 3000) {
                                                lastSpokenAction = SuggestedAction.CONTINUE_SAFE
                                            }
                                        }
                                    } else {
                                        if (labels.isNotEmpty()) {
                                            val labelDesc = labels.take(2).map { aiDetector.translateToHindi(it) }.joinToString(", ")
                                            lastAlertText = "Dikh raha hai: $labelDesc"
                                        } else {
                                            lastAlertText = if (speechManager.currentLanguage == "hi") "Rasta saaf hai" else "Path clear"
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
                    } catch (_: Exception) {}
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        // 2. High-Contrast Assistive Overlay with Real AI Bounding Boxes & Distance in Feet
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Walking Corridor Guide Lines
            drawLine(
                color = SafetyCyan.copy(alpha = 0.45f),
                start = Offset(w * 0.22f, h),
                end = Offset(w * 0.38f, h * 0.45f),
                strokeWidth = 3.dp.toPx()
            )
            drawLine(
                color = SafetyCyan.copy(alpha = 0.45f),
                start = Offset(w * 0.78f, h),
                end = Offset(w * 0.62f, h * 0.45f),
                strokeWidth = 3.dp.toPx()
            )

            // Draw Real Bounding Boxes for every detected object
            if (!isPaused) {
                val textPaint = Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 42f
                    isFakeBoldText = true
                    setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
                }
                val bgPaint = Paint().apply {
                    color = android.graphics.Color.argb(220, 18, 20, 26)
                    style = Paint.Style.FILL
                }

                detectedEntities.forEach { entity ->
                    val box = entity.boundingBox
                    val boxColor = when (entity.suggestedAction) {
                        SuggestedAction.EMERGENCY_STOP, SuggestedAction.AHEAD_BLOCKED -> CriticalRed
                        SuggestedAction.TURN_LEFT, SuggestedAction.TURN_RIGHT -> WarningOrange
                        SuggestedAction.CONTINUE_SAFE -> HighContrastYellow
                    }

                    val left = box.left * w
                    val top = box.top * h
                    val right = box.right * w
                    val bottom = box.bottom * h

                    // Draw High-Contrast Outline
                    drawRect(
                        color = boxColor,
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        style = Stroke(width = 4.dp.toPx())
                    )

                    // Draw Label Tag above bounding box with Distance in Feet
                    val distFt = (entity.distanceFeet * 10).toInt() / 10.0
                    val actionSymbol = when (entity.suggestedAction) {
                        SuggestedAction.TURN_RIGHT -> " (Turn Right 👉)"
                        SuggestedAction.TURN_LEFT -> " (Turn Left 👈)"
                        SuggestedAction.EMERGENCY_STOP -> " (STOP 🛑)"
                        else -> ""
                    }
                    val labelText = "${entity.hindiName} • ${distFt}ft$actionSymbol"
                    val textWidth = textPaint.measureText(labelText)
                    val tagTop = (top - 55f).coerceAtLeast(180f)

                    drawContext.canvas.nativeCanvas.drawRoundRect(
                        left,
                        tagTop - 45f,
                        left + textWidth + 30f,
                        tagTop + 15f,
                        12f,
                        12f,
                        bgPaint
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        labelText,
                        left + 15f,
                        tagTop,
                        textPaint
                    )
                }
            }
        }

        // 3. Top Status Bar & Controls (Flashlight, Pause, End)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Flashlight Toggle Button
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isFlashlightOn) HighContrastYellow else CardBg)
                        .clickable {
                            isFlashlightOn = !isFlashlightOn
                            hapticManager.vibrateClick()
                            speechManager.speakGuidance(
                                if (isFlashlightOn) "Torch on kar di gayi hai" else "Torch off kar di gayi hai",
                                flush = true
                            )
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .semantics {
                            contentDescription = if (isFlashlightOn) "Torch ON. Double tap to turn off." else "Torch OFF. Double tap to turn on."
                            role = Role.Button
                        }
                ) {
                    Text(
                        text = if (isFlashlightOn) "Torch: ON" else "Torch: OFF",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isFlashlightOn) PureBlack else TextWhite
                    )
                }

                // Pause / Resume Button
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isPaused) WarningOrange else CardBg)
                        .clickable {
                            isPaused = !isPaused
                            hapticManager.vibrateClick()
                            speechManager.speakGuidance(
                                if (isPaused) "Scanning rok di gayi hai." else "Scanning shuru ho gayi hai.",
                                flush = true
                            )
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .semantics {
                            contentDescription = if (isPaused) "Scanning paused. Double tap to resume." else "Double tap to pause scanning."
                            role = Role.Button
                        }
                ) {
                    Text(
                        text = if (isPaused) "RESUME" else "PAUSE",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPaused) PureBlack else TextWhite
                    )
                }

                // Stop Navigation Button
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(CriticalRed)
                        .clickable {
                            hapticManager.vibrateClick()
                            speechManager.speakGuidance("Main screen par wapas ja rahe hain.", flush = true)
                            onStopNavigation()
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .semantics {
                            contentDescription = "Stop navigation and return to dashboard."
                            role = Role.Button
                        }
                ) {
                    Text(
                        text = "EXIT",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Active Route Banner
            if (currentTurnManeuver != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardBg)
                        .border(1.dp, SafetyCyan, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = currentTurnManeuver,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = SafetyCyan,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E88E5))
                            .clickable {
                                val target = activeRouteName ?: ""
                                val uri = Uri.parse("google.navigation:q=${Uri.encode(target)}&mode=w")
                                val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                                    setPackage("com.google.android.apps.maps")
                                }
                                try {
                                    context.startActivity(mapIntent)
                                } catch (_: Exception) {
                                    val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(target)}&travelmode=walking"))
                                    context.startActivity(fallback)
                                }
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🗺️ Open in Google Maps (Walking Turn-by-Turn)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                    }
                }
            }
        }

        // 4. Bottom Real-Time Alert Banner & Instruction Pill
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val isUrgent = detectedEntities.any {
                it.suggestedAction == SuggestedAction.EMERGENCY_STOP ||
                it.suggestedAction == SuggestedAction.AHEAD_BLOCKED ||
                it.suggestedAction == SuggestedAction.TURN_LEFT ||
                it.suggestedAction == SuggestedAction.TURN_RIGHT
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBg)
                    .border(
                        2.dp,
                        if (isUrgent) CriticalRed else HighContrastYellow,
                        RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = lastAlertText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Tap screen: Check status • 2ft Proximity: Turn alerts",
                        fontSize = 12.sp,
                        color = SafetyCyan,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
