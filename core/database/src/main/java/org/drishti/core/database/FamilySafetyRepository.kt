package org.drishti.core.database

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drishti.core.model.FamilyContact
import org.drishti.core.model.LocationSharingInterval
import org.drishti.core.model.SafetySharingStatus

/**
 * Local repository for Family Contacts and Live Location Sharing configuration.
 */
class FamilySafetyRepository {

    private val _contacts = MutableStateFlow<List<FamilyContact>>(
        listOf(
            FamilyContact(
                id = 1L,
                name = "Primary Caregiver",
                phoneNumber = "+919876543210",
                isPrimaryEmergencyContact = true,
                isLocationSharingEnabled = true
            ),
            FamilyContact(
                id = 2L,
                name = "Emergency Contact 2",
                phoneNumber = "+919811223344",
                isPrimaryEmergencyContact = false,
                isLocationSharingEnabled = true
            )
        )
    )
    val contacts: StateFlow<List<FamilyContact>> = _contacts.asStateFlow()

    private val _sharingStatus = MutableStateFlow(SafetySharingStatus())
    val sharingStatus: StateFlow<SafetySharingStatus> = _sharingStatus.asStateFlow()

    fun addContact(name: String, phoneNumber: String, isPrimary: Boolean = false): FamilyContact {
        val newContact = FamilyContact(
            id = System.currentTimeMillis(),
            name = name.ifBlank { "Contact ${_contacts.value.size + 1}" },
            phoneNumber = phoneNumber,
            isPrimaryEmergencyContact = isPrimary,
            isLocationSharingEnabled = true
        )
        _contacts.value = _contacts.value + newContact
        return newContact
    }

    fun removeContact(contactId: Long) {
        _contacts.value = _contacts.value.filterNot { it.id == contactId }
    }

    fun setPrimaryContact(contactId: Long) {
        _contacts.value = _contacts.value.map { contact ->
            contact.copy(isPrimaryEmergencyContact = contact.id == contactId)
        }
    }

    fun updateSharingInterval(interval: LocationSharingInterval) {
        _sharingStatus.value = _sharingStatus.value.copy(interval = interval)
    }

    fun setLiveSharingActive(active: Boolean) {
        _sharingStatus.value = _sharingStatus.value.copy(
            isLiveSharingActive = active,
            lastDispatchedTimestampMs = if (active) System.currentTimeMillis() else _sharingStatus.value.lastDispatchedTimestampMs
        )
    }

    fun recordLocationDispatch(lat: Double, lng: Double) {
        _sharingStatus.value = _sharingStatus.value.copy(
            lastDispatchedTimestampMs = System.currentTimeMillis(),
            lastLatitude = lat,
            lastLongitude = lng
        )
    }

    fun getGoogleMapsLink(latitude: Double, longitude: Double): String {
        return "https://maps.google.com/?q=$latitude,$longitude"
    }

    fun formatSmsAlert(latitude: Double, longitude: Double, isEmergency: Boolean = false): String {
        val mapsLink = getGoogleMapsLink(latitude, longitude)
        return if (isEmergency) {
            "EMERGENCY SOS: Drishti user needs immediate assistance! Current location: $mapsLink"
        } else {
            "Drishti Safety Update: Family live location link: $mapsLink"
        }
    }
}

