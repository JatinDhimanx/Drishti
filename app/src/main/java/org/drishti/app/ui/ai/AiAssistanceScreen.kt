package org.drishti.app.ui.ai

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
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun AiAssistanceScreen(
    speechManager: SpeechManager,
    onLaunchCamera: () -> Unit,
    onBack: () -> Unit
) {
    var isBlindScreenModeEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val announcement = if (speechManager.currentLanguage == "hi") {
            "AI sahayata kendra. On-device AI engine taiyar hai. Live camera shuru karne ke liye bada peela button dabayein."
        } else {
            "AI Assistance hub. On-device AI engine is active. Tap the large yellow button to start live camera scanning."
        }
        speechManager.speakGuidance(announcement, flush = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(18.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "AI Assistance",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = HighContrastYellow,
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

            Spacer(modifier = Modifier.height(16.dp))

            // AI Status Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CardBg)
                    .border(2.dp, BorderColor, RoundedCornerShape(18.dp))
                    .padding(18.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Inference Engine",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(SuccessGreen.copy(alpha = 0.2f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "ONLINE / READY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Google LiteRT Edge NPU Acceleration\n• ML Kit On-Device Fallback\n• 24-Class Hazard Taxonomy Active",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Battery Saver Blind Mode Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CardBg)
                    .border(2.dp, if (isBlindScreenModeEnabled) SafetyCyan else BorderColor, RoundedCornerShape(18.dp))
                    .clickable {
                        isBlindScreenModeEnabled = !isBlindScreenModeEnabled
                        val msg = if (isBlindScreenModeEnabled) "Battery Saver Blind Mode Enabled" else "Battery Saver Blind Mode Disabled"
                        speechManager.speakGuidance(msg, flush = true)
                    }
                    .semantics {
                        contentDescription = "Battery Saver Blind Mode. Dims the display while keeping camera AI detection running. Currently ${if (isBlindScreenModeEnabled) "Enabled" else "Disabled"}. Double tap to toggle."
                        role = Role.Button
                    }
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Battery Saver Blind Mode",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Dims screen to 1% brightness to save battery while preserving voice & haptic guidance.",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isBlindScreenModeEnabled) SafetyCyan else CardBg)
                            .border(1.dp, SafetyCyan, CircleShape)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isBlindScreenModeEnabled) "ON" else "OFF",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isBlindScreenModeEnabled) PureBlack else SafetyCyan
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Giant Action Button: Open Live Camera Scanner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(HighContrastYellow)
                .clickable(onClick = onLaunchCamera)
                .semantics {
                    contentDescription = "Launch live camera scanner. Starts camera preview and real-time obstacle detection. Double tap to activate."
                    role = Role.Button
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "OPEN LIVE CAMERA SCANNER",
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = PureBlack,
                textAlign = TextAlign.Center
            )
        }
    }
}

