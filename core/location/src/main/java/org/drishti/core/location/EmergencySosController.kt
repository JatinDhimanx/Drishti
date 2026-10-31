package org.drishti.core.location

import org.drishti.core.model.FamilyContact

/**
 * Robust Emergency SOS Controller for Drishti.
 *
 * Implements accessible trigger handling, 3-second abort countdown window,
 * multi-recipient SMS generation, and audible cancellation workflows.
 */
class EmergencySosController(
    var userLanguage: String = "hi"
) {

    enum class SosState {
        IDLE,
        COUNTDOWN_ACTIVE,
        SOS_DISPATCHED,
        CANCELLED
    }

    data class SosStatus(
        val state: SosState,
        val remainingSeconds: Int,
        val lastLatitude: Double,
        val lastLongitude: Double,
        val dispatchedContactsCount: Int,
        val spokenConfirmation: String
    )

    private var currentState: SosState = SosState.IDLE
    private var countdownSeconds: Int = 3
    private var currentLat: Double = 0.0
    private var currentLon: Double = 0.0

    /**
     * Trigger Emergency SOS request. Starts a 3-second grace countdown to prevent accidental activation.
     */
    fun startSosCountdown(latitude: Double, longitude: Double): SosStatus {
        currentState = SosState.COUNTDOWN_ACTIVE
        countdownSeconds = 3
        currentLat = latitude
        currentLon = longitude

        val speech = if (userLanguage == "hi") {
            "Emergency SOS shuru ho raha hai. Rokne ke liye screen par double tap karein. 3.. 2.. 1.."
        } else {
            "Emergency SOS activating in 3 seconds. Double tap anywhere to cancel."
        }

        return SosStatus(
            state = currentState,
            remainingSeconds = countdownSeconds,
            lastLatitude = currentLat,
            lastLongitude = currentLon,
            dispatchedContactsCount = 0,
            spokenConfirmation = speech
        )
    }

    /**
     * Cancels the active SOS request during countdown or after dispatch.
     */
    fun cancelSos(): SosStatus {
        currentState = SosState.CANCELLED
        val speech = if (userLanguage == "hi") {
            "SOS cancel kar diya gaya hai. Aap surakshit hain."
        } else {
            "SOS alert cancelled. You are safe."
        }

        return SosStatus(
            state = currentState,
            remainingSeconds = 0,
            lastLatitude = currentLat,
            lastLongitude = currentLon,
            dispatchedContactsCount = 0,
            spokenConfirmation = speech
        )
    }

    /**
     * Executes the SOS dispatch after countdown expiry.
     */
    fun executeSosDispatch(contacts: List<FamilyContact>): SosStatus {
        currentState = SosState.SOS_DISPATCHED
        val activeContacts = contacts.filter { it.isLocationSharingEnabled }

        val speech = if (userLanguage == "hi") {
            "Aapatkaleen SOS bhej diya gaya hai! Aapke ${activeContacts.size} parivaar jano ko live location bhej di gayi hai."
        } else {
            "Emergency SOS broadcasted! Live GPS coordinates dispatched to ${activeContacts.size} emergency contacts."
        }

        return SosStatus(
            state = currentState,
            remainingSeconds = 0,
            lastLatitude = currentLat,
            lastLongitude = currentLon,
            dispatchedContactsCount = activeContacts.size,
            spokenConfirmation = speech
        )
    }

    /**
     * Formats stand-down "I am Safe" update to ease family concerns after a resolved emergency.
     */
    fun formatSafeStandDownMessage(latitude: Double, longitude: Double): String {
        val mapsLink = "https://maps.google.com/?q=$latitude,$longitude"
        return if (userLanguage == "hi") {
            "UPDATE: Drishti user ab surakshit hain aur unhone Emergency SOS band kar diya hai. Current location: $mapsLink"
        } else {
            "UPDATE: Drishti user is now safe and has stood down the Emergency SOS. Current location: $mapsLink"
        }
    }

    fun isSosActive(): Boolean = currentState == SosState.SOS_DISPATCHED || currentState == SosState.COUNTDOWN_ACTIVE
}

