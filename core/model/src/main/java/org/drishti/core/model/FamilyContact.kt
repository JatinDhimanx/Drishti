package org.drishti.core.model

/**
 * Family Contact data model for location sharing and Emergency SOS.
 */
data class FamilyContact(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val phoneNumber: String,
    val isPrimaryEmergencyContact: Boolean = false,
    val isLocationSharingEnabled: Boolean = true
)

enum class LocationSharingInterval(val minutes: Int, val label: String) {
    MINUTES_1(1, "1 Minute (High Frequency)"),
    MINUTES_5(5, "5 Minutes (Standard Walk)"),
    MINUTES_10(10, "10 Minutes (Balanced)"),
    MINUTES_15(15, "15 Minutes (Battery Saver)"),
    MINUTES_30(30, "30 Minutes (Long Transit)")
}

data class SafetySharingStatus(
    val isLiveSharingActive: Boolean = false,
    val interval: LocationSharingInterval = LocationSharingInterval.MINUTES_5,
    val lastDispatchedTimestampMs: Long = 0L,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val totalDispatchedCount: Int = 0,
    val lastStatusMessage: String = ""
)

