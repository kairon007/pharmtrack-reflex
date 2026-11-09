package com.pharmtrack.reflex.app

import android.app.Application

/** Owns the one live session so an Activity recreation does not restart the Core. */
class PharmTrackApplication : Application() {

    lateinit var app: ReflexApp
        private set

    override fun onCreate() {
        super.onCreate()
        app = ReflexApp(this)
    }
}
