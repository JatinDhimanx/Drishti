package org.drishti.app

import android.app.Application

/**
 * Drishti Application Entry Point.
 * Initializes offline logging, local database, and LiteRT hardware delegates.
 */
class DrishtiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: DrishtiApplication
            private set
    }
}
