package com.scrollscope.app.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** In-memory ring buffer shown on the Debug screen (no Logcat needed). */
object DebugLog {
    data class Entry(val time: String, val type: String, val app: String?, val detail: String)

    private const val MAX = 250
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries

    fun log(type: String, app: String?, detail: String) {
        val e = Entry(fmt.format(Date()), type, app, detail)
        _entries.update { (listOf(e) + it).take(MAX) }
    }

    fun clear() = _entries.update { emptyList() }
}
