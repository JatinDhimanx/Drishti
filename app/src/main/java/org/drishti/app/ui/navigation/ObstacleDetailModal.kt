package org.drishti.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import org.drishti.core.model.ThreatLevel
import org.drishti.core.model.TrackedObstacle

private val PureBlack = Color(0xFF000000)
private val CriticalRed = Color(0xFFFF1744)
private val WarningOrange = Color(0xFFFF9100)
private val SafetyCyan = Color(0xFF00E5FF)
private val CardBg = Color(0xFF14161C)
private val TextWhite = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFB8B8BE)

@Composable
fun ObstacleDetailModal(
    obstacles: List<TrackedObstacle>,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack.copy(alpha = 0.94f))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Nearby Obstacles Detected",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                if (obstacles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No immediate obstacles in walking corridor.",
                            fontSize = 16.sp,
                            color = TextSecondary
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(obstacles) { tracked ->
                            val levelColor = when (tracked.threatLevel) {
                                ThreatLevel.CRITICAL -> CriticalRed
                                ThreatLevel.CAUTION -> WarningOrange
                                else -> SafetyCyan
                            }

                            val desc = "${tracked.obstacle.assistiveClass.englishLabel} at ${tracked.clockPosition} o'clock, ${(tracked.estimatedDistanceMeters * 10).toInt() / 10f} meters. Severity: ${tracked.threatLevel.name}."

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CardBg)
                                    .border(1.5.dp, levelColor, RoundedCornerShape(12.dp))
                                    .padding(16.dp)
                                    .semantics {
                                        contentDescription = desc
                                        role = Role.Button
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "${tracked.obstacle.assistiveClass.englishLabel} (${tracked.obstacle.assistiveClass.hindiLabel})",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextWhite
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Direction: ${tracked.clockPosition} o'clock • Distance: ${(tracked.estimatedDistanceMeters * 10).toInt() / 10f}m",
                                            fontSize = 14.sp,
                                            color = TextSecondary
                                        )
                                    }

                                    Text(
                                        text = tracked.threatLevel.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = levelColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Close and Resume Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SafetyCyan)
                    .clickable(onClick = onClose)
                    .padding(18.dp)
                    .semantics {
                        contentDescription = "Close obstacle details and resume camera scanning."
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "RESUME SCANNING",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PureBlack
                )
            }
        }
    }
}
