package org.drishti.app.ui.destination

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.drishti.core.database.DestinationRepository
import org.drishti.core.feedback.HapticManager
import org.drishti.core.feedback.SpeechManager
import org.drishti.core.model.Waypoint

private val PureBlack = Color(0xFF000000)
private val HighContrastYellow = Color(0xFFFFEB3B)
private val SafetyCyan = Color(0xFF00E5FF)
private val CardBg = Color(0xFF14161C)
private val BorderColor = Color(0xFF2C303E)
private val TextWhite = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFB8B8BE)

@Composable
fun DestinationHubScreen(
    repository: DestinationRepository,
    speechManager: SpeechManager,
    hapticManager: HapticManager,
    onOpenLiveMapSelector: () -> Unit,
    onStartRouteNavigation: (Waypoint) -> Unit,
    onBackToDashboard: () -> Unit,
    onSpeakIntro: () -> Unit
) {
    val destinations by repository.destinations.collectAsState()

    LaunchedEffect(Unit) {
        onSpeakIntro()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Saved Destinations",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CardBg)
                        .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                        .clickable(onClick = onBackToDashboard)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .semantics {
                            contentDescription = "Back to home dashboard"
                            role = Role.Button
                        }
                ) {
                    Text(text = "Back", color = HighContrastYellow, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Giant Primary Button: SELECT ON LIVE MAP (with companion / self)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SafetyCyan)
                    .clickable {
                        hapticManager.vibrateClick()
                        onOpenLiveMapSelector()
                    }
                    .padding(18.dp)
                    .semantics {
                        contentDescription = "Select destination on live map. Drop a pin or search a place with a companion to add a new route. Double tap to open map."
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "+ SELECT DESTINATION ON LIVE MAP",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = PureBlack
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Drop a pin with a companion or search address",
                        fontSize = 12.sp,
                        color = PureBlack.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Your Saved Places & Routes",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Saved Destinations List
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(destinations) { waypoint ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(CardBg)
                            .border(1.5.dp, BorderColor, RoundedCornerShape(14.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = waypoint.label,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextWhite
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "GPS: ${(waypoint.latitude * 1000).toInt() / 1000.0}, ${(waypoint.longitude * 1000).toInt() / 1000.0}",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }

                            // Start Live Route Navigation Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(HighContrastYellow)
                                    .clickable {
                                        hapticManager.vibrateClick()
                                        speechManager.speakGuidance("${waypoint.label} ke liye live rasta shuru ho raha hai", flush = true)
                                        onStartRouteNavigation(waypoint)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                                    .semantics {
                                        contentDescription = "Start live turn by turn navigation to ${waypoint.label}. Gives voice directions and vibration cues."
                                        role = Role.Button
                                    }
                            ) {
                                Text(
                                    text = "GO HERE",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = PureBlack
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
