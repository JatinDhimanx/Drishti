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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.drishti.app.safety.AutoLocationSharingManager
import org.drishti.core.database.FamilySafetyRepository
import org.drishti.core.feedback.SpeechManager
import org.drishti.core.model.FamilyContact
import org.drishti.core.model.LocationSharingInterval
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PureBlack = Color(0xFF0A0C12)
private val HighContrastYellow = Color(0xFFFFEB3B)
private val SafetyCyan = Color(0xFF00E5FF)
private val FamilyGreen = Color(0xFF00E676)
private val EmergencyRed = Color(0xFFFF3B30)
private val CardBg = Color(0xFF141722)
private val BorderColor = Color(0xFF262E40)
private val TextWhite = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF9096A2)

@Composable
fun FamilySafetyScreen(
    safetyRepo: FamilySafetyRepository,
    speechManager: SpeechManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val contacts by safetyRepo.contacts.collectAsState()
    val sharingStatus by safetyRepo.sharingStatus.collectAsState()

    val autoSharingManager = remember { AutoLocationSharingManager(context, safetyRepo) }

    var showAddDialog by remember { mutableStateOf(false) }
    var newContactName by remember { mutableStateOf("") }
    var newContactPhone by remember { mutableStateOf("") }
    var isNewContactPrimary by remember { mutableStateOf(false) }
    var isSendingInstantSms by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val announcement = if (speechManager.currentLanguage == "hi") {
            "Family Safety screen. Yahan aap automatically har interval ke baad live location SMS bhej sakte hain."
        } else {
            "Family Safety. Manage automatic periodic location SMS to trusted contacts."
        }
        speechManager.speakGuidance(announcement, flush = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Top Bar Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(CardBg)
                        .border(1.dp, BorderColor, CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to dashboard",
                        tint = TextWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = "Family Safety",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = FamilyGreen,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "Auto SMS Live Location Sharing",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        // 2. Automatic Live Location Sharing Main Toggle Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CardBg)
                .border(2.dp, if (sharingStatus.isLiveSharingActive) FamilyGreen else BorderColor, RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Auto Location Sharing",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            if (sharingStatus.isLiveSharingActive) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(FamilyGreen.copy(alpha = 0.2f))
                                        .border(1.dp, FamilyGreen, CircleShape)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("RUNNING", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FamilyGreen)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (sharingStatus.isLiveSharingActive)
                                "Har ${sharingStatus.interval.minutes} min me SMS apne aap send ho raha hai"
                            else
                                "Auto-SMS band hai • Switch dabakar shuru karein",
                            fontSize = 12.sp,
                            color = if (sharingStatus.isLiveSharingActive) FamilyGreen else TextSecondary
                        )
                    }

                    // Toggle Switch
                    Switch(
                        checked = sharingStatus.isLiveSharingActive,
                        onCheckedChange = { active ->
                            if (active) {
                                autoSharingManager.startAutoSharing()
                                val msg = "Live location auto-sharing chalu ho gayi hai. Har ${sharingStatus.interval.minutes} minute me SMS bhej diya jayega."
                                speechManager.speakGuidance(msg, flush = true)
                            } else {
                                autoSharingManager.stopAutoSharing()
                                speechManager.speakGuidance("Auto location sharing band kar di gayi hai.", flush = true)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PureBlack,
                            checkedTrackColor = FamilyGreen,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = BorderColor
                        )
                    )
                }

                // Status info & total count
                if (sharingStatus.isLiveSharingActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(PureBlack.copy(alpha = 0.6f))
                            .border(1.dp, Color(0xFF1E2838), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Kul SMS Bheje Gaye:", fontSize = 12.sp, color = TextSecondary)
                                Text("${sharingStatus.totalDispatchedCount} SMS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SafetyCyan)
                            }
                            if (sharingStatus.lastStatusMessage.isNotBlank()) {
                                Text(
                                    text = sharingStatus.lastStatusMessage,
                                    fontSize = 11.sp,
                                    color = FamilyGreen
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Sharing Interval Selection (1 min, 5 min, 10 min, 15 min, 30 min)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CardBg)
                .border(1.dp, BorderColor, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Auto-Send Interval",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = HighContrastYellow
                )
                Text(
                    text = "Current: ${sharingStatus.interval.minutes} min",
                    fontSize = 12.sp,
                    color = SafetyCyan,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            LocationSharingInterval.entries.forEach { intervalOption ->
                val isSelected = sharingStatus.interval == intervalOption
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) SafetyCyan.copy(alpha = 0.15f) else Color(0xFF0E1118))
                        .border(1.dp, if (isSelected) SafetyCyan else BorderColor, RoundedCornerShape(12.dp))
                        .clickable {
                            safetyRepo.updateSharingInterval(intervalOption)
                            if (sharingStatus.isLiveSharingActive) {
                                autoSharingManager.stopAutoSharing()
                                autoSharingManager.startAutoSharing()
                            }
                            speechManager.speakGuidance("Interval set to ${intervalOption.label}", flush = true)
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = intervalOption.label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) SafetyCyan else TextWhite
                        )
                        Text(
                            text = "Automatic SMS dispatched every ${intervalOption.minutes} minutes",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = SafetyCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 4. Force Send Location SMS Now Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SafetyCyan)
                .clickable {
                    if (!isSendingInstantSms) {
                        isSendingInstantSms = true
                        coroutineScope.launch {
                            val success = autoSharingManager.dispatchLocationNow()
                            isSendingInstantSms = false
                            if (success) {
                                speechManager.speakGuidance("Live location SMS sabhi parivar walo ko bhej diya gaya hai.", flush = true)
                            } else {
                                speechManager.speakGuidance("SMS nahi bheja ja saka. Kripya check karein.", flush = true)
                            }
                        }
                    }
                }
                .semantics {
                    contentDescription = "Send location SMS immediately to all contacts"
                    role = Role.Button
                },
            contentAlignment = Alignment.Center
        ) {
            if (isSendingInstantSms) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = PureBlack, strokeWidth = 2.dp)
                    Text("SMS Bhej Rahe Hain...", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PureBlack)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = null, tint = PureBlack, modifier = Modifier.size(18.dp))
                    Text("SEND LOCATION SMS NOW", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PureBlack)
                }
            }
        }

        // 5. Trusted Family Contacts Header & Add Contact Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Trusted Contacts (${contacts.size})",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
                Text(
                    text = "Select who receives automatic location SMS",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(HighContrastYellow)
                    .clickable { showAddDialog = true }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .semantics {
                        contentDescription = "Add new contact"
                        role = Role.Button
                    }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = PureBlack, modifier = Modifier.size(16.dp))
                    Text(
                        text = "Add Contact",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PureBlack
                    )
                }
            }
        }

        // 6. Contacts List
        contacts.forEach { contact ->
            ContactCardItem(
                contact = contact,
                onToggleSharing = { enabled ->
                    safetyRepo.setContactSharingEnabled(contact.id, enabled)
                    speechManager.speakGuidance(
                        if (enabled) "${contact.name} ke liye auto SMS chalu kiya gaya." else "${contact.name} ke liye auto SMS band kiya gaya."
                    )
                },
                onSetPrimary = {
                    safetyRepo.setPrimaryContact(contact.id)
                    speechManager.speakGuidance("${contact.name} ko primary contact banaya gaya.")
                },
                onDelete = {
                    safetyRepo.removeContact(contact.id)
                    speechManager.speakGuidance("${contact.name} contact hata diya gaya.")
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
                    text = "Add Family Emergency Contact",
                    color = HighContrastYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newContactName,
                        onValueChange = { newContactName = it },
                        label = { Text("Contact Name (e.g. Mammi, Bhai)", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = HighContrastYellow,
                            unfocusedBorderColor = BorderColor
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newContactPhone,
                        onValueChange = { newContactPhone = it },
                        label = { Text("Mobile Phone Number", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = HighContrastYellow,
                            unfocusedBorderColor = BorderColor
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isNewContactPrimary = !isNewContactPrimary }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Switch(
                            checked = isNewContactPrimary,
                            onCheckedChange = { isNewContactPrimary = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PureBlack,
                                checkedTrackColor = HighContrastYellow
                            )
                        )
                        Text("Set as Primary Emergency Contact", fontSize = 12.sp, color = TextWhite)
                    }
                }
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(HighContrastYellow)
                        .clickable {
                            if (newContactPhone.isNotBlank()) {
                                safetyRepo.addContact(newContactName, newContactPhone, isNewContactPrimary)
                                speechManager.speakGuidance("$newContactName successfully add ho gaya.")
                                newContactName = ""
                                newContactPhone = ""
                                isNewContactPrimary = false
                                showAddDialog = false
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("Save Contact", color = PureBlack, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clickable { showAddDialog = false }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("Cancel", color = TextSecondary, fontSize = 13.sp)
                }
            }
        )
    }
}

@Composable
private fun ContactCardItem(
    contact: FamilyContact,
    onToggleSharing: (Boolean) -> Unit,
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
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Name and Phone
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = contact.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                        if (contact.isPrimaryEmergencyContact) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(HighContrastYellow.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "PRIMARY",
                                    fontSize = 9.sp,
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

                // Delete Button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(EmergencyRed.copy(alpha = 0.15f))
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete contact",
                        tint = EmergencyRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Bottom Action Row: Auto-SMS toggle and Set Primary
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Auto-SMS Checkbox / Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable { onToggleSharing(!contact.isLocationSharingEnabled) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (contact.isLocationSharingEnabled) FamilyGreen else BorderColor)
                            .border(1.dp, if (contact.isLocationSharingEnabled) FamilyGreen else TextSecondary, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (contact.isLocationSharingEnabled) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = PureBlack, modifier = Modifier.size(14.dp))
                        }
                    }
                    Text(
                        text = if (contact.isLocationSharingEnabled) "Auto-SMS: ON" else "Auto-SMS: OFF",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (contact.isLocationSharingEnabled) FamilyGreen else TextSecondary
                    )
                }

                if (!contact.isPrimaryEmergencyContact) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(BorderColor)
                            .clickable(onClick = onSetPrimary)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Set Primary",
                            fontSize = 11.sp,
                            color = TextWhite
                        )
                    }
                }
            }
        }
    }
}
