package org.drishti.app

import android.app.Application
import org.drishti.app.notification.DrishtiNotificationManager

/**
 * Drishti Application Entry Point.
 * Initializes offline logging, local database, notification channels, and LiteRT hardware delegates.
 */
class DrishtiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Initialize Android notification channels
        DrishtiNotificationManager.initChannels(this)
    }

    companion object {
        lateinit var instance: DrishtiApplication
            private set
    }
}
