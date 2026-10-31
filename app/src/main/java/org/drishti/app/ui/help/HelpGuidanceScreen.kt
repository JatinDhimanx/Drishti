package org.drishti.app.ui.help

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PureBlack = Color(0xFF000000)
private val HighContrastYellow = Color(0xFFFFEB3B)
private val SafetyCyan = Color(0xFF00E5FF)
private val WarningOrange = Color(0xFFFF9100)
private val CardBg = Color(0xFF14161C)
private val BorderColor = Color(0xFF2C303E)
private val TextWhite = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFB8B8BE)

@Composable
fun HelpGuidanceScreen(
    onBack: () -> Unit
) {
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
                text = "Help & Safety Guide",
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

        Spacer(modifier = Modifier.height(18.dp))

        // Important Safety Rule Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(WarningOrange.copy(alpha = 0.15f))
                .border(2.dp, WarningOrange, RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "CRITICAL SAFETY NOTICE",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = WarningOrange
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Drishti is an assistive aid designed to complement, NEVER replace, your long white cane or guide dog. Always maintain physical cane contact with the ground while walking.",
                    fontSize = 13.sp,
                    color = TextWhite,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // How to Hold Phone
        HelpCard(
            title = "1. How to Hold Your Phone",
            subtitle = "Phone ko kaise pakdein",
            content = "Hold your smartphone at chest height pointing forward with the camera unobstructed. You can also place the phone in a chest lanyard harness for hands-free scanning."
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Screen Gestures
        HelpCard(
            title = "2. Quick Touch Gestures",
            subtitle = "Screen par touch karne ke niyam",
            content = "• Single Tap: Repeats the latest obstacle or direction alert.\n• Double Tap: Opens the full list of nearby detected objects.\n• Torch Button: Toggles the flashlight in low light."
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Vibration Code Guide
        HelpCard(
            title = "3. Vibration Patterns Meaning",
            subtitle = "Vibration signals ka matlab",
            content = "• 3 Sharp Pulses: Critical Hazard ahead! Stop immediately.\n• Double Pulse: Turn Left ahead on route.\n• Triple Pulse: Turn Right ahead on route.\n• Continuous Buzz: Off-route / Wrong direction.\n• Double Long Wave: You have arrived at your destination!"
        )

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(HighContrastYellow)
                .clickable(onClick = onBack)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "RETURN TO DASHBOARD",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = PureBlack
            )
        }
    }
}

@Composable
private fun HelpCard(
    title: String,
    subtitle: String,
    content: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SafetyCyan)
            Text(text = subtitle, fontSize = 12.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = content, fontSize = 13.sp, color = TextWhite, lineHeight = 20.sp)
        }
    }
}
