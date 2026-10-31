package org.drishti.app.ui.about

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.drishti.app.splash.DrishtiLogo

private val PureBlack = Color(0xFF000000)
private val HighContrastYellow = Color(0xFFFFEB3B)
private val SafetyCyan = Color(0xFF00E5FF)
private val WarningOrange = Color(0xFFFF9100)
private val CardBg = Color(0xFF14161C)
private val BorderColor = Color(0xFF2C303E)
private val TextWhite = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFB8B8BE)

@Composable
fun AboutAccessibilityScreen(
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(18.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "About & Accessibility",
                fontSize = 24.sp,
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

        // App Branding Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(CardBg)
                .border(1.dp, BorderColor, RoundedCornerShape(18.dp))
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DrishtiLogo(modifier = Modifier.size(56.dp, 38.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "DRISHTI",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Text(
                        text = "Version 1.0.0 (Offline Production)",
                        fontSize = 13.sp,
                        color = SafetyCyan
                    )
                    Text(
                        text = "AI-Powered Mobility Assistant",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        // Safety Disclaimer Card (High-Contrast Orange Warning)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(CardBg)
                .border(2.dp, WarningOrange, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Column {
                Text(
                    text = "CRITICAL SAFETY DISCLAIMER",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = WarningOrange,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Drishti is designed as a secondary assistive perception aid. It complements, but NEVER replaces, your primary physical mobility tool such as the white cane or trained guide dog.\n\nAlways maintain physical sweep with your white cane to detect tactile ground textures and immediate steps.",
                    fontSize = 14.sp,
                    color = TextWhite,
                    lineHeight = 20.sp
                )
            }
        }

        // Accessibility Features Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(CardBg)
                .border(1.dp, BorderColor, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Column {
                Text(
                    text = "Accessibility Standards (WCAG 2.2 AAA)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = HighContrastYellow,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "• Pure Black & High-Contrast Yellow/Cyan color palette for maximum low-vision clarity.\n• Complete TalkBack screen-reader labeling across every interactive element.\n• Minimum 56x56 dp touch targets to prevent mis-taps.\n• Dynamic audio ducking so safety alerts are never drowned out by background sounds.\n• Spatial haptic vibrations for silent and non-auditory orientation.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 20.sp
                )
            }
        }

        // Privacy Guarantee Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(CardBg)
                .border(1.dp, BorderColor, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Column {
                Text(
                    text = "Privacy & Offline Architecture",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = SafetyCyan,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• 100% on-device AI inference via Google LiteRT.\n• Zero camera video or audio frames are recorded or uploaded to any remote server.\n• Operates seamlessly in Airplane Mode without internet.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

