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
    val ironEnabled: Boolean = true,
    val reelsLimitMin: Int = 60,
    val shortsLimitMin: Int = 60,
    val openHour: Int = 10,
    val closeHour: Int = 23,
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
        val iron = booleanPreferencesKey("iron_enabled")
        val reelsMin = intPreferencesKey("iron_reels_min")
        val shortsMin = intPreferencesKey("iron_shorts_min")
        val open = intPreferencesKey("iron_open_hour")
        val close = intPreferencesKey("iron_close_hour")
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
        ironEnabled = p[K.iron] ?: d.ironEnabled,
        reelsLimitMin = p[K.reelsMin] ?: d.reelsLimitMin,
        shortsLimitMin = p[K.shortsMin] ?: d.shortsLimitMin,
        openHour = p[K.open] ?: d.openHour,
        closeHour = p[K.close] ?: d.closeHour,
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
        p[K.iron] = s.ironEnabled
        p[K.reelsMin] = s.reelsLimitMin
        p[K.shortsMin] = s.shortsLimitMin
        p[K.open] = s.openHour
        p[K.close] = s.closeHour
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { read(it) }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p -> write(p, transform(read(p))) }
    }
}
