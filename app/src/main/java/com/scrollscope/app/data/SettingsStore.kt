package com.scrollscope.app.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppSettings(
    val dailyLimit: Int = 200,
    val pauseEnabled: Boolean = true,
    val pauseAfter: Int = 50,
    val bubbleEnabled: Boolean = true,
    val trackInstagram: Boolean = true,
    val trackYoutube: Boolean = true,
    val summaryHour: Int = 22,
    val retentionDays: Int = 90,
    val bubbleX: Int = -1,
    val bubbleY: Int = 320,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private object K {
        val limit = intPreferencesKey("daily_limit")
        val pauseOn = booleanPreferencesKey("pause_enabled")
        val pauseAfter = intPreferencesKey("pause_after")
        val bubble = booleanPreferencesKey("bubble_enabled")
        val ig = booleanPreferencesKey("track_instagram")
        val yt = booleanPreferencesKey("track_youtube")
        val hour = intPreferencesKey("summary_hour")
        val retention = intPreferencesKey("retention_days")
        val bx = intPreferencesKey("bubble_x")
        val by = intPreferencesKey("bubble_y")
    }

    private val d = AppSettings()

    private fun read(p: Preferences) = AppSettings(
        dailyLimit = p[K.limit] ?: d.dailyLimit,
        pauseEnabled = p[K.pauseOn] ?: d.pauseEnabled,
        pauseAfter = p[K.pauseAfter] ?: d.pauseAfter,
        bubbleEnabled = p[K.bubble] ?: d.bubbleEnabled,
        trackInstagram = p[K.ig] ?: d.trackInstagram,
        trackYoutube = p[K.yt] ?: d.trackYoutube,
        summaryHour = p[K.hour] ?: d.summaryHour,
        retentionDays = p[K.retention] ?: d.retentionDays,
        bubbleX = p[K.bx] ?: d.bubbleX,
        bubbleY = p[K.by] ?: d.bubbleY,
    )

    private fun write(p: MutablePreferences, s: AppSettings) {
        p[K.limit] = s.dailyLimit
        p[K.pauseOn] = s.pauseEnabled
        p[K.pauseAfter] = s.pauseAfter
        p[K.bubble] = s.bubbleEnabled
        p[K.ig] = s.trackInstagram
        p[K.yt] = s.trackYoutube
        p[K.hour] = s.summaryHour
        p[K.retention] = s.retentionDays
        p[K.bx] = s.bubbleX
        p[K.by] = s.bubbleY
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { read(it) }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p -> write(p, transform(read(p))) }
    }
}
