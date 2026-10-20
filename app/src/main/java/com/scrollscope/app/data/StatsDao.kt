package com.scrollscope.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StatsDao {
    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Query("UPDATE sessions SET endTs = :endTs, reelCount = reelCount + 1 WHERE id = :id")
    suspend fun bumpSession(id: Long, endTs: Long)

    @Insert
    suspend fun insertEvent(event: ReelEvent): Long

    @Query("UPDATE reel_events SET dwellMs = :dwellMs WHERE id = :id")
    suspend fun setDwell(id: Long, dwellMs: Long)

    @Query("SELECT COUNT(*) FROM reel_events WHERE day = :day")
    fun countForDay(day: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM reel_events WHERE day = :day")
    suspend fun countForDayNow(day: String): Int

    @Query("SELECT app, COUNT(*) AS count FROM reel_events WHERE day = :day GROUP BY app")
    fun appCounts(day: String): Flow<List<AppCount>>

    @Query("SELECT * FROM sessions WHERE day = :day ORDER BY startTs DESC")
    fun sessionsForDay(day: String): Flow<List<SessionEntity>>

    @Query("SELECT day, COUNT(*) AS count, COALESCE(SUM(dwellMs), 0) AS dwellMs FROM reel_events WHERE day >= :from GROUP BY day ORDER BY day")
    fun dailyStats(from: String): Flow<List<DayStat>>

    @Query("SELECT day, hour, COUNT(*) AS count FROM reel_events WHERE day >= :from GROUP BY day, hour")
    fun hourStats(from: String): Flow<List<HourStat>>

    @Query("SELECT COUNT(*) FROM reel_events WHERE day >= :from AND dwellMs IS NOT NULL AND dwellMs < 3000")
    fun quickSkips(from: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM reel_events WHERE day >= :from AND dwellMs IS NOT NULL")
    fun timedCount(from: String): Flow<Int>

    @Query("SELECT MAX(reelCount) FROM sessions WHERE day >= :from")
    fun longestSession(from: String): Flow<Int?>

    @Query("SELECT * FROM reel_events ORDER BY ts")
    suspend fun allEvents(): List<ReelEvent>

    @Query("DELETE FROM reel_events WHERE day < :day")
    suspend fun deleteEventsBefore(day: String)

    @Query("DELETE FROM sessions WHERE day < :day")
    suspend fun deleteSessionsBefore(day: String)

    @Query("DELETE FROM reel_events")
    suspend fun clearEvents()

    @Query("DELETE FROM sessions")
    suspend fun clearSessions()
}
