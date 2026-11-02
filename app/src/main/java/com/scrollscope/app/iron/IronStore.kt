package com.scrollscope.app.iron

import android.content.Context
import com.scrollscope.app.util.Days
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class IronSnapshot(
    val day: String = "",
    val watchedMs: Map<String, Long> = emptyMap(),
    val passDay: String? = null,
    val passUntil: Long = 0,
) {
    fun watched(pkg: String) = watchedMs[pkg] ?: 0L
    fun passActive(now: Long) = passUntil > now
    val passUsedToday get() = passDay == day
}

/**
 * Today's Reels/Shorts watch time, the daily pass and one-shot warnings.
 * Written every second by the accessibility service, so it lives in plain
 * SharedPreferences (synchronous reads) rather than DataStore.
 */
class IronStore(context: Context) {
    private val prefs = context.getSharedPreferences("iron", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<IronSnapshot> = _state.asStateFlow()

    private var lastFlush = 0L

    private fun watchKey(day: String, pkg: String) = "watch_${day}_$pkg"

    private fun read(): IronSnapshot {
        val day = Days.todayKey()
        return IronSnapshot(
            day = day,
            watchedMs = IronRules.APPS.associateWith { prefs.getLong(watchKey(day, it), 0L) },
            passDay = prefs.getString("pass_day", null),
            passUntil = prefs.getLong("pass_until", 0L),
        )
    }

    /** Re-reads after midnight so the new day starts from zero. */
    fun current(): IronSnapshot {
        if (_state.value.day != Days.todayKey()) {
            flush()
            clearOldDays()
            _state.value = read()
        }
        return _state.value
    }

    /** Counts in memory every tick; writes to disk about every 10 s and on [flush]. */
    fun addWatch(pkg: String, ms: Long) {
        if (ms <= 0) return
        val snap = current()
        _state.value = snap.copy(watchedMs = snap.watchedMs + (pkg to snap.watched(pkg) + ms))
        val now = System.currentTimeMillis()
        if (now - lastFlush >= FLUSH_MS) flush()
    }

    fun flush() {
        val snap = _state.value
        lastFlush = System.currentTimeMillis()
        prefs.edit().apply { snap.watchedMs.forEach { (pkg, ms) -> putLong(watchKey(snap.day, pkg), ms) } }.apply()
    }

    /** Spends today's pass. Returns false if it was already used. */
    fun usePass(now: Long): Boolean {
        val snap = current()
        if (snap.passUsedToday) return false
        val until = now + IronRules.PASS_MS
        prefs.edit().putString("pass_day", snap.day).putLong("pass_until", until).apply()
        _state.value = snap.copy(passDay = snap.day, passUntil = until)
        return true
    }

    /** Marks a one-shot warning for today. Returns true the first time only. */
    fun markWarned(name: String): Boolean {
        val key = "warned_${current().day}_$name"
        if (prefs.getBoolean(key, false)) return false
        prefs.edit().putBoolean(key, true).apply()
        return true
    }

    private fun clearOldDays() {
        val today = Days.todayKey()
        val stale = prefs.all.keys.filter {
            (it.startsWith("watch_") || it.startsWith("warned_")) && !it.contains(today)
        }
        if (stale.isNotEmpty()) prefs.edit().apply { stale.forEach { remove(it) } }.apply()
    }

    private companion object {
        const val FLUSH_MS = 10_000L
    }
}
