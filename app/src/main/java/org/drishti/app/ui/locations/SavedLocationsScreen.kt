package org.drishti.app.ui.locations

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.drishti.core.database.DestinationRepository
import org.drishti.core.feedback.SpeechManager
import org.drishti.core.model.Waypoint

private val PureBlack = Color(0xFF000000)
private val HighContrastYellow = Color(0xFFFFEB3B)
private val SafetyCyan = Color(0xFF00E5FF)
private val CardBg = Color(0xFF14161C)
private val BorderColor = Color(0xFF2C303E)
private val EmergencyRed = Color(0xFFFF3B30)
private val TextWhite = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFB8B8BE)

@Composable
fun SavedLocationsScreen(
    repository: DestinationRepository,
    speechManager: SpeechManager,
    onNavigateToWaypoint: (Waypoint) -> Unit,
    onBack: () -> Unit
) {
    val destinations by repository.destinations.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var newPlaceName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val announcement = if (speechManager.currentLanguage == "hi") {
            "Saved Locations screen. Aapke paas ${destinations.size} surakshit sthan hain. Rasta shuru karne ke liye kisi bhi jagah par tap karein."
        } else {
            "Saved Locations screen. You have ${destinations.size} saved places. Tap any place to start walking navigation."
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Saved Locations",
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
                        contentDescription = "Back to previous screen"
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

        // Add Current Location Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SafetyCyan)
                .clickable { showAddDialog = true }
                .semantics {
                    contentDescription = "Save current GPS location. Double tap to name and save."
                    role = Role.Button
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+ SAVE CURRENT LOCATION",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = PureBlack
            )
        }

        Text(
            text = "Your Saved Places (${destinations.size})",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextWhite
        )

        // List of Waypoints
        destinations.forEach { waypoint ->
            SavedPlaceItem(
                waypoint = waypoint,
                onNavigate = { onNavigateToWaypoint(waypoint) },
                onDelete = {
                    repository.deleteDestination(waypoint.id)
                    speechManager.speakGuidance("${waypoint.label} deleted.", flush = true)
                }
            )
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = CardBg,
            title = {
                Text(
                    text = "Save Location",
                    color = HighContrastYellow,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = newPlaceName,
                    onValueChange = { newPlaceName = it },
                    label = { Text("Location Name (e.g. My Pharmacy)", color = TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = HighContrastYellow,
                        unfocusedBorderColor = BorderColor
                    ),
                    singleLine = true
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(HighContrastYellow)
                        .clickable {
                            if (newPlaceName.isNotBlank()) {
                                repository.addDestination(newPlaceName, 28.6139, 77.2090)
                                speechManager.speakGuidance("Location saved: $newPlaceName", flush = true)
                                newPlaceName = ""
                                showAddDialog = false
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("Save", color = PureBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clickable { showAddDialog = false }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun SavedPlaceItem(
    waypoint: Waypoint,
    onNavigate: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(1.5.dp, BorderColor, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = waypoint.label,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "GPS: ${String.format("%.4f", waypoint.latitude)}, ${String.format("%.4f", waypoint.longitude)}",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(EmergencyRed.copy(alpha = 0.2f))
                        .clickable(onClick = onDelete)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .semantics {
                            contentDescription = "Delete ${waypoint.label}"
                            role = Role.Button
                        }
                ) {
                    Text(
                        text = "Delete",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmergencyRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Navigate Here Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(HighContrastYellow)
                    .clickable(onClick = onNavigate)
                    .semantics {
                        contentDescription = "Start walking navigation to ${waypoint.label}. Double tap to start route."
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "NAVIGATE TO ${waypoint.label.uppercase()}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PureBlack
                )
            }
        }
    }
}

