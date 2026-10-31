package org.drishti.app.ui.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.drishti.core.database.UserPreferencesRepository
import org.drishti.core.feedback.HapticManager
import org.drishti.core.feedback.SpeechManager

private val PureBlack = Color(0xFF000000)
private val HighContrastYellow = Color(0xFFFFEB3B)
private val SafetyCyan = Color(0xFF00E5FF)
private val CardBg = Color(0xFF14161C)
private val BorderColor = Color(0xFF2C303E)
private val TextWhite = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFB8B8BE)

@Composable
fun SettingsScreen(
    preferencesRepo: UserPreferencesRepository,
    speechManager: SpeechManager,
    hapticManager: HapticManager,
    onOpenHelp: () -> Unit,
    onBack: () -> Unit
) {
    val prefs by preferencesRepo.preferences.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Settings & Audio",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardBg)
                    .clickable(onClick = onBack)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(text = "Back", color = HighContrastYellow, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 1. Spoken Language Selection
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
                    text = "Spoken Language / Bhasha",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Hindi Toggle
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (prefs.language == "hi") HighContrastYellow else PureBlack)
                            .border(1.5.dp, HighContrastYellow, RoundedCornerShape(10.dp))
                            .clickable {
                                preferencesRepo.updateLanguage("hi")
                                speechManager.setLanguage("hi")
                                hapticManager.vibrateClick()
                                speechManager.speakGuidance("Bhasha Hindi select ho gayi hai.", flush = true)
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "हिन्दी (Hindi)",
                            fontWeight = FontWeight.Bold,
                            color = if (prefs.language == "hi") PureBlack else TextWhite
                        )
                    }

                    // English Toggle
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (prefs.language == "en") HighContrastYellow else PureBlack)
                            .border(1.5.dp, HighContrastYellow, RoundedCornerShape(10.dp))
                            .clickable {
                                preferencesRepo.updateLanguage("en")
                                speechManager.setLanguage("en")
                                hapticManager.vibrateClick()
                                speechManager.speakGuidance("Language set to English.", flush = true)
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "English",
                            fontWeight = FontWeight.Bold,
                            color = if (prefs.language == "en") PureBlack else TextWhite
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Speech Speed Slider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardBg)
                .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Speech Rate (Aawaz ki Speed)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Text(
                        text = "${(prefs.speechRate * 10).toInt() / 10f}x",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = SafetyCyan
                    )
                }

                Slider(
                    value = prefs.speechRate,
                    onValueChange = {
                        preferencesRepo.updateSpeechRate(it)
                        speechManager.speechRate = it
                    },
                    valueRange = 0.8f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = HighContrastYellow,
                        activeTrackColor = HighContrastYellow,
                        inactiveTrackColor = BorderColor
                    )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(PureBlack)
                        .clickable {
                            hapticManager.vibrateClick()
                            speechManager.speakGuidance(
                                if (prefs.language == "hi") "Yeh speech speed ka sample hai."
                                else "This is a sample of the speech speed.",
                                flush = true
                            )
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Test Speech Speed", color = SafetyCyan, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Vibration & Haptics
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardBg)
                .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Haptic Vibration Alerts",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                        Text(
                            text = "Directional pulses for hazards and turns",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    Switch(
                        checked = prefs.isHapticEnabled,
                        onCheckedChange = {
                            preferencesRepo.updateHapticEnabled(it)
                            hapticManager.isHapticEnabled = it
                            if (it) hapticManager.vibrateCriticalStop()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PureBlack,
                            checkedTrackColor = HighContrastYellow
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(PureBlack)
                        .clickable {
                            hapticManager.vibrateCriticalStop()
                            speechManager.speakGuidance("Critical stop vibration tested.", flush = true)
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Test Vibration Pulse", color = HighContrastYellow, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Auto-Torch in Low Light
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardBg)
                .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Auto-Flashlight in Dark",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Text(
                        text = "Automatically enables torch when lighting is low",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                Switch(
                    checked = prefs.autoTorchEnabled,
                    onCheckedChange = { preferencesRepo.updateAutoTorch(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PureBlack,
                        checkedTrackColor = HighContrastYellow
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // White Cane Safety Guide Link
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SafetyCyan)
                .clickable(onClick = onOpenHelp)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "WHITE CANE SAFETY & GESTURE GUIDE",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = PureBlack
            )
        }
    }
}
