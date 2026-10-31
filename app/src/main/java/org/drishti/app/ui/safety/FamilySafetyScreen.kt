package org.drishti.app.ui.safety

import android.content.Intent
import android.net.Uri
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
import org.drishti.core.feedback.SpeechManager
import org.drishti.core.model.FamilyContact
import org.drishti.core.model.LocationSharingInterval

private val PureBlack = Color(0xFF000000)
private val HighContrastYellow = Color(0xFFFFEB3B)
private val SafetyCyan = Color(0xFF00E5FF)
private val FamilyGreen = Color(0xFF00E676)
private val EmergencyRed = Color(0xFFFF3B30)
private val CardBg = Color(0xFF14161C)
private val BorderColor = Color(0xFF2C303E)
private val TextWhite = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFB8B8BE)

@Composable
fun FamilySafetyScreen(
    safetyRepo: FamilySafetyRepository,
    speechManager: SpeechManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val contacts by safetyRepo.contacts.collectAsState()
    val sharingStatus by safetyRepo.sharingStatus.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var newContactName by remember { mutableStateOf("") }
    var newContactPhone by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val announcement = if (speechManager.currentLanguage == "hi") {
            "Family Safety screen. Yahan aap parivar ke sampark jod sakte hain aur live location share kar sakte hain."
        } else {
            "Family Safety screen. Manage trusted contacts and live location sharing intervals."
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
                text = "Family Safety",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = FamilyGreen,
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

        // Live Location Sharing Toggle Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(CardBg)
                .border(2.dp, if (sharingStatus.isLiveSharingActive) FamilyGreen else BorderColor, RoundedCornerShape(18.dp))
                .clickable {
                    val newState = !sharingStatus.isLiveSharingActive
                    safetyRepo.setLiveSharingActive(newState)
                    val msg = if (newState) "Live location sharing started" else "Live location sharing stopped"
                    speechManager.speakGuidance(msg, flush = true)
                }
                .semantics {
                    contentDescription = "Live Location Sharing. Status: ${if (sharingStatus.isLiveSharingActive) "Active" else "Inactive"}. Interval: ${sharingStatus.interval.label}. Double tap to toggle."
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
                        text = "Live Location Sharing",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (sharingStatus.isLiveSharingActive)
                            "Sharing active • Every ${sharingStatus.interval.minutes} min"
                        else
                            "Sharing stopped • Tap to activate",
                        fontSize = 13.sp,
                        color = if (sharingStatus.isLiveSharingActive) FamilyGreen else TextSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (sharingStatus.isLiveSharingActive) FamilyGreen else CardBg)
                        .border(1.dp, FamilyGreen, CircleShape)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (sharingStatus.isLiveSharingActive) "ACTIVE" else "START",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (sharingStatus.isLiveSharingActive) PureBlack else FamilyGreen
                    )
                }
            }
        }

        // Configurable Sharing Interval Options
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(CardBg)
                .border(1.dp, BorderColor, RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "Sharing Interval",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = HighContrastYellow
            )
            Spacer(modifier = Modifier.height(10.dp))

            LocationSharingInterval.entries.forEach { intervalOption ->
                val isSelected = sharingStatus.interval == intervalOption
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) SafetyCyan.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable {
                            safetyRepo.updateSharingInterval(intervalOption)
                            speechManager.speakGuidance("Interval set to ${intervalOption.label}", flush = true)
                        }
                        .semantics {
                            contentDescription = "${intervalOption.label}. ${if (isSelected) "Selected" else "Not selected"}. Double tap to choose."
                            role = Role.Button
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = intervalOption.label,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) SafetyCyan else TextWhite
                    )
                    if (isSelected) {
                        Text(
                            text = "✓",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = SafetyCyan
                        )
                    }
                }
            }
        }

        // Instant Send SMS Check-In Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SafetyCyan)
                .clickable {
                    val primaryContact = contacts.firstOrNull { it.isPrimaryEmergencyContact } ?: contacts.firstOrNull()
                    val phoneNumber = primaryContact?.phoneNumber ?: ""
                    val smsText = safetyRepo.formatSmsAlert(28.6139, 77.2090, isEmergency = false)

                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("smsto:$phoneNumber")
                        putExtra("sms_body", smsText)
                    }
                    context.startActivity(intent)
                    speechManager.speakGuidance("Opening SMS app with current location link.", flush = true)
                }
                .semantics {
                    contentDescription = "Send instant SMS location check-in to primary contact. Double tap to send."
                    role = Role.Button
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "SEND INSTANT SMS CHECK-IN",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = PureBlack
            )
        }

        // Family Contacts Header & Add Contact Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Trusted Contacts (${contacts.size})",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(HighContrastYellow)
                    .clickable { showAddDialog = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .semantics {
                        contentDescription = "Add new family emergency contact. Double tap to open dialog."
                        role = Role.Button
                    }
            ) {
                Text(
                    text = "+ Add Contact",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PureBlack
                )
            }
        }

        // Contacts List
        contacts.forEach { contact ->
            ContactItemCard(
                contact = contact,
                onSetPrimary = {
                    safetyRepo.setPrimaryContact(contact.id)
                    speechManager.speakGuidance("${contact.name} set as primary emergency contact.", flush = true)
                },
                onDelete = {
                    safetyRepo.removeContact(contact.id)
                    speechManager.speakGuidance("${contact.name} removed.", flush = true)
                }
            )
        }
    }

    // Add Contact Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = CardBg,
            title = {
                Text(
                    text = "Add Emergency Contact",
                    color = HighContrastYellow,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newContactName,
                        onValueChange = { newContactName = it },
                        label = { Text("Contact Name", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = HighContrastYellow,
                            unfocusedBorderColor = BorderColor
                        ),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newContactPhone,
                        onValueChange = { newContactPhone = it },
                        label = { Text("Phone Number (+91...)", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = HighContrastYellow,
                            unfocusedBorderColor = BorderColor
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(HighContrastYellow)
                        .clickable {
                            if (newContactPhone.isNotBlank()) {
                                safetyRepo.addContact(newContactName, newContactPhone)
                                speechManager.speakGuidance("Contact added: $newContactName", flush = true)
                                newContactName = ""
                                newContactPhone = ""
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
private fun ContactItemCard(
    contact: FamilyContact,
    onSetPrimary: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(1.5.dp, if (contact.isPrimaryEmergencyContact) HighContrastYellow else BorderColor, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = contact.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                        if (contact.isPrimaryEmergencyContact) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(HighContrastYellow.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "PRIMARY",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HighContrastYellow
                                )
                            }
                        }
                    }
                    Text(
                        text = contact.phoneNumber,
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!contact.isPrimaryEmergencyContact) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(BorderColor)
                                .clickable(onClick = onSetPrimary)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .semantics {
                                    contentDescription = "Set ${contact.name} as primary contact"
                                    role = Role.Button
                                }
                        ) {
                            Text(
                                text = "Set Primary",
                                fontSize = 11.sp,
                                color = TextWhite
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(EmergencyRed.copy(alpha = 0.2f))
                            .clickable(onClick = onDelete)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .semantics {
                                contentDescription = "Delete contact ${contact.name}"
                                role = Role.Button
                            }
                    ) {
                        Text(
                            text = "Delete",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmergencyRed
                        )
                    }
                }
            }
        }
    }
}

