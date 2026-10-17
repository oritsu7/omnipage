package com.ansh.omnipage

import android.app.Application

class OmniPageApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Re-scan every registered folder on launch (no-op when none are registered).
        container.scanScheduler.scanAll()
    }
}
