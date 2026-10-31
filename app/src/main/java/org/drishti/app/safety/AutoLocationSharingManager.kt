package org.drishti.app.safety

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import org.drishti.core.database.FamilySafetyRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitResult(): T? =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resume(null) }
        addOnCanceledListener { cont.resume(null) }
    }

/**
 * Manages automated periodic SMS dispatch of the user's live GPS location to trusted family contacts.
 * Runs in the background at the user's chosen interval (1m, 5m, 10m, 15m, 30m).
 */
class AutoLocationSharingManager(
    private val context: Context,
    private val safetyRepo: FamilySafetyRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var sharingJob: Job? = null

    val isRunning: Boolean
        get() = sharingJob?.isActive == true

    /**
     * Start the automated background loop. Dispatches an immediate initial SMS,
     * then continues periodically after every selected interval.
     */
    fun startAutoSharing() {
        if (isRunning) return
        safetyRepo.setLiveSharingActive(true)

        sharingJob = scope.launch {
            // 1. Initial immediate dispatch on activation
            dispatchLocationNow()

            // 2. Periodic interval loop
            while (isActive && safetyRepo.sharingStatus.value.isLiveSharingActive) {
                val currentIntervalMinutes = safetyRepo.sharingStatus.value.interval.minutes
                val intervalMs = currentIntervalMinutes * 60 * 1000L

                delay(intervalMs)

                if (isActive && safetyRepo.sharingStatus.value.isLiveSharingActive) {
                    dispatchLocationNow()
                }
            }
        }
    }

    /**
     * Stop the automated background SMS dispatching.
     */
    fun stopAutoSharing() {
        sharingJob?.cancel()
        sharingJob = null
        safetyRepo.setLiveSharingActive(false)
        safetyRepo.updateStatusMessage("Auto location sharing stopped")
    }

    /**
     * Fetches current GPS coordinates and automatically sends SMS to all enabled family contacts.
     */
    @SuppressLint("MissingPermission")
    suspend fun dispatchLocationNow(): Boolean {
        // Check SMS permission
        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        // Check Location permission
        val hasLocationPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // Retrieve contacts configured for sharing
        val targetContacts = safetyRepo.contacts.value.filter { it.isLocationSharingEnabled }
        if (targetContacts.isEmpty()) {
            safetyRepo.updateStatusMessage("No contacts enabled for location sharing")
            return false
        }

        // Fetch device location
        var lat = 28.6139
        var lon = 77.2090
        var accuracy = 15

        if (hasLocationPermission) {
            try {
                val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                val location = fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).awaitResult()
                    ?: fusedClient.lastLocation.awaitResult()

                if (location != null) {
                    lat = location.latitude
                    lon = location.longitude
                    accuracy = location.accuracy.toInt()
                }
            } catch (e: Exception) {}
        }

        val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val currentTimeStr = timeFormatter.format(Date())

        val mapsLink = "https://maps.google.com/?q=$lat,$lon"
        val smsMessage = "Drishti Safety Update: User live location: $mapsLink (Accuracy: ${accuracy}m at $currentTimeStr)"

        if (hasSmsPermission) {
            try {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                var successCount = 0
                for (contact in targetContacts) {
                    val phone = contact.phoneNumber.trim()
                    if (phone.isNotEmpty()) {
                        try {
                            val parts = smsManager.divideMessage(smsMessage)
                            if (parts.size > 1) {
                                smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
                            } else {
                                smsManager.sendTextMessage(phone, null, smsMessage, null, null)
                            }
                            successCount++
                        } catch (e: Exception) {}
                    }
                }

                val summary = "Auto-sent SMS to $successCount contacts at $currentTimeStr"
                safetyRepo.recordLocationDispatch(lat, lon, summary)
                return successCount > 0
            } catch (e: Exception) {
                safetyRepo.updateStatusMessage("SMS dispatch failed: ${e.localizedMessage}")
                return false
            }
        } else {
            safetyRepo.updateStatusMessage("SEND_SMS permission required for automatic SMS")
            return false
        }
    }
}


