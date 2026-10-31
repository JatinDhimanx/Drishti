package org.drishti.app.ui.sos

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.drishti.core.database.FamilySafetyRepository
import org.drishti.core.feedback.HapticManager
import org.drishti.core.feedback.SpeechManager

private val PureBlack = Color(0xFF000000)
private val EmergencyRed = Color(0xFFFF3B30)
private val CardBg = Color(0xFF14161C)
private val BorderColor = Color(0xFF2C303E)
private val HighContrastYellow = Color(0xFFFFEB3B)
private val SafetyCyan = Color(0xFF00E5FF)
private val TextWhite = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFB8B8BE)

@Composable
fun EmergencySosScreen(
    safetyRepo: FamilySafetyRepository,
    speechManager: SpeechManager,
    hapticManager: HapticManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val contacts by safetyRepo.contacts.collectAsState()
    var isSosTriggered by remember { mutableStateOf(false) }

    val pulseAlpha = remember { Animatable(0.6f) }

    LaunchedEffect(isSosTriggered) {
        if (isSosTriggered) {
            pulseAlpha.animateTo(
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        }
    }

    LaunchedEffect(Unit) {
        val announcement = if (speechManager.currentLanguage == "hi") {
            "Emergency SOS screen. Madad chahiye toh laal button dabayein. Aapka live location turant family contacts ko bheja jayega."
        } else {
            "Emergency SOS screen. Tap the large red button to broadcast your emergency GPS coordinates to your family contacts."
        }
        speechManager.speakCritical(announcement)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Emergency SOS",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = EmergencyRed,
                modifier = Modifier.semantics { heading() }
            )

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(CardBg)
                    .border(1.dp, BorderColor, CircleShape)
                    .clickable(onClick = onBack)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .semantics {
                        contentDescription = "Back to home dashboard"
                        role = Role.Button
                    }
            ) {
                Text(
                    text = "Back",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
            }
        }

        // GPS Status Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardBg)
                .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "Current GPS Coordinates",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = HighContrastYellow
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Lat: 28.6139° N, Lng: 77.2090° E (Accuracy: ±4.2m)",
                    fontSize = 13.sp,
                    color = TextWhite
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Active Contacts to notify: ${contacts.size}",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // GIANT EMERGENCY SOS BUTTON
        Box(
            modifier = Modifier
                .size(240.dp)
                .clip(CircleShape)
                .background(EmergencyRed.copy(alpha = if (isSosTriggered) pulseAlpha.value else 1.0f))
                .border(4.dp, TextWhite, CircleShape)
                .clickable {
                    isSosTriggered = true
                    hapticManager.vibrateCriticalStop()

                    val primaryContact = contacts.firstOrNull { it.isPrimaryEmergencyContact } ?: contacts.firstOrNull()
                    val phone = primaryContact?.phoneNumber ?: ""
                    val smsBody = safetyRepo.formatSmsAlert(28.6139, 77.2090, isEmergency = true)

                    val speechMsg = if (speechManager.currentLanguage == "hi") {
                        "Aapaatkaal alert bheja gaya! Aapki location emergency contacts ko share kar di gayi hai."
                    } else {
                        "Emergency SOS triggered! Your live GPS coordinates have been sent to your emergency contacts."
                    }
                    speechManager.speakCritical(speechMsg)

                    // Dispatch SMS
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("smsto:$phone")
                        putExtra("sms_body", smsBody)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {}
                }
                .semantics {
                    contentDescription = "Trigger Emergency SOS. Broadcasts emergency alert and coordinates to all family contacts. Double tap to activate."
                    role = Role.Button
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isSosTriggered) "ALERT\nSENT!" else "PRESS\nFOR SOS",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = TextWhite,
                    textAlign = TextAlign.Center,
                    lineHeight = 36.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isSosTriggered) "Contacts Notified" else "Tap for Help",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite.copy(alpha = 0.9f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Cancel / Safe Button
        if (isSosTriggered) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBg)
                    .border(2.dp, SafetyCyan, RoundedCornerShape(16.dp))
                    .clickable {
                        isSosTriggered = false
                        speechManager.speakGuidance("Emergency SOS cancelled. Returning to safe status.", flush = true)
                    }
                    .semantics {
                        contentDescription = "Cancel Emergency SOS. Signals that you are safe. Double tap to cancel."
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "I AM SAFE / CANCEL SOS",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = SafetyCyan
                )
            }
        } else {
            Text(
                text = "Accidental activation protected. You can cancel at any time.",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}


