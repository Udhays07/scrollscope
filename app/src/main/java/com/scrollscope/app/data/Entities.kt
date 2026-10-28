package com.scrollscope.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object Apps {
    const val INSTAGRAM = "com.instagram.android"
    const val YOUTUBE = "com.google.android.youtube"
}

@Entity(tableName = "sessions", indices = [Index("day")])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val app: String,
    val day: String,
    val startTs: Long,
    val endTs: Long,
    val reelCount: Int,
)

@Entity(tableName = "reel_events", indices = [Index("day"), Index("sessionId")])
data class ReelEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val app: String,
    val ts: Long,
    val day: String,
    val hour: Int,
    val dwellMs: Long? = null,
)

data class AppCount(val app: String, val count: Int)
data class DayStat(val day: String, val count: Int, val dwellMs: Long)
data class DayCount(val day: String, val count: Int)
data class HourStat(val day: String, val hour: Int, val count: Int)
