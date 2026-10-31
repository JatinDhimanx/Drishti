package org.drishti.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import org.drishti.app.splash.DrishtiLogo
import org.drishti.core.feedback.SpeechManager

private val PureBlack = Color(0xFF000000)
private val HighContrastYellow = Color(0xFFFFEB3B)
private val SafetyCyan = Color(0xFF00E5FF)
private val CardBg = Color(0xFF14161C)
private val BorderColor = Color(0xFF2C303E)
private val TextWhite = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFB8B8BE)

private data class OnboardingStep(
    val title: String,
    val subtitle: String,
    val description: String,
    val hindiAnnouncement: String,
    val englishAnnouncement: String
)

@Composable
fun OnboardingScreen(
    speechManager: SpeechManager,
    onComplete: () -> Unit
) {
    val steps = listOf(
        OnboardingStep(
            title = "Welcome to Drishti",
            subtitle = "Offline AI Mobility Assistant",
            description = "Drishti is designed to complement your white cane. It alerts you to head-level hazards, potholes, open drains, and approaching vehicles completely offline.",
            hindiAnnouncement = "Drishti me aapka swagat hai. Yeh offline AI mobility assistant aapki safar ko surakshit banata hai.",
            englishAnnouncement = "Welcome to Drishti. Offline AI mobility assistant designed to complement your white cane."
        ),
        OnboardingStep(
            title = "How to Hold Your Phone",
            subtitle = "Chest Mount or Hand Grip",
            description = "For best coverage, use a chest harness, lanyard, or hold the phone upright pointing slightly forward. Keep the rear camera uncovered.",
            hindiAnnouncement = "Phone ko seene ke paas ya lanyard par lagayein, taaki camera saamne ka rasta dekh sake.",
            englishAnnouncement = "Mount phone at chest height or hold upright pointing forward so the camera can scan your walking path."
        ),
        OnboardingStep(
            title = "Voice & Vibration Language",
            subtitle = "3 Levels of Safety Alerts",
            description = "Gentle pulse for distant objects. Warning voice alert for objects in your path. Urgent vibration bursts and Emergency Stop command for immediate collision hazards.",
            hindiAnnouncement = "Teen tarah ke alerts: Dheemi vibration, Warning aawaz, aur tatkaal rukne ke liye Emergency Stop.",
            englishAnnouncement = "Three levels of alerts: soft pulse for caution, spoken alert for warning, and urgent stop command for critical collision."
        )
    )

    var currentStep by remember { mutableIntStateOf(0) }

    LaunchedEffect(currentStep) {
        val step = steps[currentStep]
        val speech = if (speechManager.currentLanguage == "hi") step.hindiAnnouncement else step.englishAnnouncement
        speechManager.speakGuidance(speech, flush = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Bar: Step Indicator & Skip
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DrishtiLogo(modifier = Modifier.size(36.dp, 24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "DRISHTI",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardBg)
                    .clickable(onClick = onComplete)
                    .semantics {
                        contentDescription = "Skip onboarding tutorial and go to permission setup"
                        role = Role.Button
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Skip Tutorial",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
            }
        }

        // Animated Content Card
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "OnboardingContent"
        ) { stepIndex ->
            val step = steps[stepIndex]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardBg)
                    .border(2.dp, BorderColor, RoundedCornerShape(24.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Step ${stepIndex + 1} of ${steps.size}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = SafetyCyan,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = step.title,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = HighContrastYellow,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = step.subtitle,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextWhite,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = step.description,
                    fontSize = 15.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }
        }

        // Bottom Controls: Page Dots & Next Button
        Column(modifier = Modifier.fillMaxWidth()) {
            // Page Dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                steps.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (index == currentStep) 12.dp else 8.dp)
                            .clip(CircleShape)
                            .background(if (index == currentStep) HighContrastYellow else BorderColor)
                    )
                }
            }

            // Big Action Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(HighContrastYellow)
                    .clickable {
                        if (currentStep < steps.size - 1) {
                            currentStep++
                        } else {
                            onComplete()
                        }
                    }
                    .semantics {
                        val label = if (currentStep < steps.size - 1) "Next tutorial step" else "Finish tutorial and continue"
                        contentDescription = "$label. Double tap to activate."
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (currentStep < steps.size - 1) "NEXT STEP" else "GET STARTED",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PureBlack
                )
            }
        }
    }
}

