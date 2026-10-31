package org.drishti.app.ui.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.drishti.core.feedback.SpeechManager

private val PureBlack = Color(0xFF000000)
private val HighContrastYellow = Color(0xFFFFEB3B)
private val SafetyCyan = Color(0xFF00E5FF)
private val CardBg = Color(0xFF14161C)
private val BorderColor = Color(0xFF2C303E)
private val SuccessGreen = Color(0xFF00E676)
private val TextWhite = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFB8B8BE)

@Composable
fun PermissionSetupScreen(
    speechManager: SpeechManager,
    onAllPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var hasLocationPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] ?: hasCameraPermission
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: hasLocationPermission

        if (hasCameraPermission && hasLocationPermission) {
            speechManager.speakGuidance("All essential permissions granted. Entering home dashboard.", flush = true)
            onAllPermissionsGranted()
        }
    }

    LaunchedEffect(Unit) {
        val announcement = if (speechManager.currentLanguage == "hi") {
            "Anumati setup screen. Kripya camera aur location ki anumati dein taaki Drishti rasta aur rukavatein dekh sake."
        } else {
            "Permissions setup. Please grant camera and location permissions so Drishti can detect obstacles and navigate."
        }
        speechManager.speakGuidance(announcement, flush = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Permissions Setup",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = HighContrastYellow,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Drishti processes all data 100% on-device. Your video and location never leave your phone.",
                fontSize = 15.sp,
                color = TextSecondary,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Camera Permission Card
            PermissionCard(
                title = "1. Camera Access (Mandatory)",
                purpose = "Allows AI to scan your walking path for obstacles, potholes, low tree branches, and vehicles in real time.",
                isGranted = hasCameraPermission,
                onRequest = {
                    permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Location Permission Card
            PermissionCard(
                title = "2. GPS Location (Navigation & Safety)",
                purpose = "Required for offline pedestrian turn-by-turn guidance, route deviation alerts, and family safety SOS links.",
                isGranted = hasLocationPermission,
                onRequest = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Big Continue Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (hasCameraPermission) HighContrastYellow else CardBg)
                .border(2.dp, if (hasCameraPermission) HighContrastYellow else BorderColor, RoundedCornerShape(16.dp))
                .clickable {
                    if (hasCameraPermission && hasLocationPermission) {
                        onAllPermissionsGranted()
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                }
                .semantics {
                    val label = if (hasCameraPermission && hasLocationPermission) "Continue to Home Dashboard" else "Grant remaining permissions"
                    contentDescription = "$label. Double tap to activate."
                    role = Role.Button
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (hasCameraPermission && hasLocationPermission) "CONTINUE TO DASHBOARD" else "GRANT PERMISSIONS",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = if (hasCameraPermission) PureBlack else TextWhite
            )
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    purpose: String,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .border(2.dp, if (isGranted) SuccessGreen else BorderColor, RoundedCornerShape(18.dp))
            .clickable(onClick = onRequest)
            .semantics {
                contentDescription = "$title. Status: ${if (isGranted) "Granted" else "Not granted"}. $purpose. Double tap to grant."
                role = Role.Button
            }
            .padding(18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )

                // Status Badge
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isGranted) SuccessGreen.copy(alpha = 0.2f) else HighContrastYellow.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isGranted) "GRANTED" else "REQUIRED",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isGranted) SuccessGreen else HighContrastYellow
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = purpose,
                fontSize = 14.sp,
                color = TextSecondary,
                lineHeight = 20.sp
            )
        }
    }
}

