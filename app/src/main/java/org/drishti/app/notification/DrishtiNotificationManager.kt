package org.drishti.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.drishti.app.MainActivity
import org.drishti.app.R

/**
 * Drishti Central Notification Manager.
 * Manages notification channels, foreground walking assistance banners,
 * obstacle safety alerts, and system notifications for visually impaired users.
 */
object DrishtiNotificationManager {

    const val CHANNEL_NAVIGATION_ID = "drishti_navigation_channel"
    const val CHANNEL_ALERTS_ID = "drishti_safety_alerts_channel"
    const val CHANNEL_STATUS_ID = "drishti_system_status_channel"

    const val NOTIFICATION_ID_NAV_ONGOING = 1001
    const val NOTIFICATION_ID_HAZARD_ALERT = 1002
    const val NOTIFICATION_ID_STATUS = 1003

    /**
     * Initializes all required Android notification channels.
     * Safe to call on application start and whenever needed.
     */
    fun initChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    ?: return

            // 1. Navigation Channel (Ongoing Walking Assistance)
            val navChannel = NotificationChannel(
                CHANNEL_NAVIGATION_ID,
                "Walking Assistance & Safe Corridor",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows active AI navigation status and real-time safe walking corridor"
                setShowBadge(true)
                enableVibration(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            // 2. Obstacle & Hazard Channel
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS_ID,
                "Obstacle & Collision Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent 2-meter proximity and collision warnings"
                setShowBadge(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 300)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            // 3. Status Updates Channel
            val statusChannel = NotificationChannel(
                CHANNEL_STATUS_ID,
                "Drishti Status & Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General system notices, safety checkups, and offline map alerts"
                setShowBadge(true)
            }

            notificationManager.createNotificationChannels(
                listOf(navChannel, alertsChannel, statusChannel)
            )
        }
    }

    /**
     * Checks if notification permission is granted (API 33+).
     */
    fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    /**
     * Displays an ongoing notification while live AI camera navigation is running.
     */
    fun showOngoingNavigationNotification(
        context: Context,
        title: String = "Drishti AI Assistance Active",
        message: String = "Monitoring 2m corridor ahead for obstacles & drop-offs"
    ) {
        if (!canPostNotifications(context)) return

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.drishti_splash_logo)
        } catch (_: Exception) {
            null
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_NAVIGATION_ID)
            .setSmallIcon(R.drawable.ic_notification_drishti)
            .apply {
                if (largeIcon != null) setLargeIcon(largeIcon)
            }
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setColor(0xFFFF7028.toInt()) // Drishti Brand Orange
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_NAV_ONGOING, notification)
        } catch (_: SecurityException) {
            // Permission denied or revoked at runtime
        }
    }

    /**
     * Cancels the ongoing navigation notification when user exits camera navigation.
     */
    fun cancelOngoingNavigationNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_NAV_ONGOING)
        } catch (_: Exception) {
        }
    }

    /**
     * Posts an obstacle hazard alert notification.
     */
    fun showHazardAlertNotification(
        context: Context,
        obstacleLabel: String,
        distanceMeters: Float,
        guidance: String
    ) {
        if (!canPostNotifications(context)) return

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Caution: $obstacleLabel ahead (${String.format("%.1fm", distanceMeters)})"
        val message = "$guidance. Path corridor compromised."

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
            .setSmallIcon(R.drawable.ic_notification_drishti)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(0xFFF02D2D.toInt()) // Alert Red
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_HAZARD_ALERT, notification)
        } catch (_: SecurityException) {
        }
    }

    /**
     * Posts a general system status notification (used on dashboard or status check).
     */
    fun showStatusNotification(
        context: Context,
        title: String = "Drishti Safety Active",
        message: String = "Offline AI obstacle detection and 2-meter collision guard are ready."
    ) {
        if (!canPostNotifications(context)) return

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            2,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.drishti_splash_logo)
        } catch (_: Exception) {
            null
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_STATUS_ID)
            .setSmallIcon(R.drawable.ic_notification_drishti)
            .apply {
                if (largeIcon != null) setLargeIcon(largeIcon)
            }
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(0xFFFF7028.toInt()) // Brand Orange
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_STATUS, notification)
        } catch (_: SecurityException) {
        }
    }
}

