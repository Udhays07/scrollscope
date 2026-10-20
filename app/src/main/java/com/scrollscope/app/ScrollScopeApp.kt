package com.scrollscope.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.scrollscope.app.data.AppDatabase
import com.scrollscope.app.data.AppSettings
import com.scrollscope.app.data.SettingsStore
import com.scrollscope.app.work.DailySummaryWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ScrollScopeApp : Application() {
    lateinit var db: AppDatabase
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var settingsState: StateFlow<AppSettings>
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this
        db = AppDatabase.build(this)
        settings = SettingsStore(this)
        settingsState = settings.flow.stateIn(appScope, SharingStarted.Eagerly, AppSettings())

        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_SUMMARY, "Daily summary", NotificationManager.IMPORTANCE_DEFAULT)
        )
        DailySummaryWorker.schedule(this, hour = 22, replace = false)
    }

    companion object {
        const val CHANNEL_SUMMARY = "daily_summary"
        lateinit var instance: ScrollScopeApp
            private set
    }
}
