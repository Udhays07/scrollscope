package com.scrollscope.app.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object Days {
    private fun zoned(ts: Long) = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault())
    fun keyOf(ts: Long): String = zoned(ts).toLocalDate().toString()
    fun hourOf(ts: Long): Int = zoned(ts).hour
    fun minuteOfDay(ts: Long): Int = zoned(ts).let { it.hour * 60 + it.minute }
    fun today(): LocalDate = LocalDate.now()
    fun todayKey(): String = today().toString()

    /** Emits today's key, and again after midnight. */
    fun todayFlow(): Flow<String> = flow {
        while (true) {
            emit(todayKey())
            delay(60_000)
        }
    }.distinctUntilChanged()
}

object Format {
    fun duration(ms: Long): String {
        val totalMin = ms / 60_000
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    fun hour12(hour: Int): String = when {
        hour == 0 -> "12 AM"
        hour < 12 -> "$hour AM"
        hour == 12 -> "12 PM"
        else -> "${hour - 12} PM"
    }
}
