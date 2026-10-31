package org.drishti.core.location

import org.drishti.core.model.FamilyContact
import org.drishti.core.model.LocationSharingInterval

/**
 * Family Safety & Live Location Dispatcher for Drishti.
 *
 * Implements interval-based tracking, offline ping queues, activity status monitoring,
 * and SMS broadcast formatting.
 */
class FamilySafetyDispatcher(
    var userLanguage: String = "hi"
) {

    enum class UserSafetyState(val englishLabel: String, val hindiLabel: String) {
        WALKING("Walking safely", "Surakshit chal rahe hain"),
        STOPPED("Paused / Stopped", "Ruke hue hain"),
        ROUTE_DEVIATED("Route deviated", "Raste se bhatak gaye hain"),
        SOS_TRIGGERED("Emergency SOS Active", "Aapatkaleen SOS sakriya hai")
    }

    data class QueuedLocationPing(
        val latitude: Double,
        val longitude: Double,
        val state: UserSafetyState,
        val timestampMs: Long = System.currentTimeMillis()
    )

    private val offlineQueue = mutableListOf<QueuedLocationPing>()
    var lastDispatchTimestampMs: Long = 0L

    /**
     * Determines if a location ping should be dispatched based on the selected interval.
     */
    fun shouldDispatch(
        interval: LocationSharingInterval,
        currentTimestampMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (lastDispatchTimestampMs == 0L) return true
        val intervalMs = interval.minutes * 60 * 1000L
        return (currentTimestampMs - lastDispatchTimestampMs) >= intervalMs
    }

    /**
     * Creates a structured SMS broadcast message for trusted family contacts.
     */
    fun formatBroadcastMessage(
        latitude: Double,
        longitude: Double,
        state: UserSafetyState
    ): String {
        val mapsLink = "https://maps.google.com/?q=$latitude,$longitude"

        return if (userLanguage == "hi") {
            when (state) {
                UserSafetyState.SOS_TRIGGERED -> "AAPATKALEEN SOS: Drishti user ko turant madad chahiye! Live Location: $mapsLink"
                UserSafetyState.ROUTE_DEVIATED -> "Drishti Alert: User raste se bhatak gaye hain. Live Location: $mapsLink"
                UserSafetyState.WALKING -> "Drishti Safety Update: User surakshit chal rahe hain. Live Location: $mapsLink"
                UserSafetyState.STOPPED -> "Drishti Safety Update: User ruke hue hain. Live Location: $mapsLink"
            }
        } else {
            when (state) {
                UserSafetyState.SOS_TRIGGERED -> "EMERGENCY SOS: Drishti user needs immediate assistance! Live Location: $mapsLink"
                UserSafetyState.ROUTE_DEVIATED -> "Drishti Alert: User deviated from planned route. Live Location: $mapsLink"
                UserSafetyState.WALKING -> "Drishti Safety Update: User is currently walking safely. Live Location: $mapsLink"
                UserSafetyState.STOPPED -> "Drishti Safety Update: User has paused/stopped. Live Location: $mapsLink"
            }
        }
    }

    /**
     * Enqueue a ping when offline or out of cell coverage.
     */
    fun enqueueOfflinePing(ping: QueuedLocationPing) {
        offlineQueue.add(ping)
        // Keep queue bounded to last 20 pings
        if (offlineQueue.size > 20) {
            offlineQueue.removeAt(0)
        }
    }

    /**
     * Retrieve and clear all pending offline pings once connection is restored.
     */
    fun drainOfflineQueue(): List<QueuedLocationPing> {
        val drained = offlineQueue.toList()
        offlineQueue.clear()
        return drained
    }

    fun getPendingQueueSize(): Int = offlineQueue.size

    fun recordSuccessfulDispatch(timestampMs: Long = System.currentTimeMillis()) {
        lastDispatchTimestampMs = timestampMs
    }
}

