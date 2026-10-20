package com.scrollscope.app.data

import com.scrollscope.app.ScrollScopeApp
import com.scrollscope.app.util.Days
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalDate

data class TodayUi(
    val count: Int = 0,
    val limit: Int = 200,
    val byApp: Map<String, Int> = emptyMap(),
    val sessions: List<SessionEntity> = emptyList(),
    val watchMs: Long = 0,
)

data class DayValue(val date: LocalDate, val count: Int)

data class TrendsUi(
    val days: List<DayValue> = emptyList(),
    val total: Int = 0,
    val prevTotal: Int = 0,
    val watchMs: Long = 0,
    val limit: Int = 200,
)

data class InsightsUi(
    /** 7 rows (Mon..Sun) × 24 hours of reel counts. */
    val grid: List<List<Int>> = List(7) { List(24) { 0 } },
    val total: Int = 0,
    val peakHour: Int? = null,
    val latePercent: Int = 0,
    val skipPercent: Int? = null,
    val longest: Int = 0,
    val avgSecondsPerReel: Int? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
object Stats {
    private val app get() = ScrollScopeApp.instance
    private val dao get() = app.db.stats()

    fun today(): Flow<TodayUi> = Days.todayFlow().flatMapLatest { day ->
        combine(
            dao.countForDay(day),
            dao.appCounts(day),
            dao.sessionsForDay(day),
            dao.dailyStats(day),
            app.settings.flow,
        ) { count, apps, sessions, daily, s ->
            TodayUi(
                count = count,
                limit = s.dailyLimit,
                byApp = apps.associate { it.app to it.count },
                sessions = sessions,
                watchMs = daily.firstOrNull { it.day == day }?.dwellMs ?: 0,
            )
        }
    }

    fun trends(range: Int): Flow<TrendsUi> = Days.todayFlow().flatMapLatest { key ->
        val today = LocalDate.parse(key)
        val from = today.minusDays((2L * range) - 1)
        combine(dao.dailyStats(from.toString()), app.settings.flow) { stats, s ->
            val byDay = stats.associateBy { it.day }
            val days = (range - 1 downTo 0).map { back ->
                val d = today.minusDays(back.toLong())
                DayValue(d, byDay[d.toString()]?.count ?: 0)
            }
            val prev = (2 * range - 1 downTo range).sumOf { back ->
                byDay[today.minusDays(back.toLong()).toString()]?.count ?: 0
            }
            val firstDay = days.first().date.toString()
            TrendsUi(
                days = days,
                total = days.sumOf { it.count },
                prevTotal = prev,
                watchMs = stats.filter { it.day >= firstDay }.sumOf { it.dwellMs },
                limit = s.dailyLimit,
            )
        }
    }

    fun insights(): Flow<InsightsUi> = Days.todayFlow().flatMapLatest { key ->
        val from = LocalDate.parse(key).minusDays(6).toString()
        combine(
            dao.hourStats(from),
            dao.dailyStats(from),
            dao.quickSkips(from),
            dao.timedCount(from),
            dao.longestSession(from),
        ) { hours, daily, skips, timed, longest ->
            val grid = List(7) { MutableList(24) { 0 } }
            val perHour = IntArray(24)
            hours.forEach { h ->
                val dow = LocalDate.parse(h.day).dayOfWeek.value - 1
                grid[dow][h.hour] += h.count
                perHour[h.hour] += h.count
            }
            val total = perHour.sum()
            val dwell = daily.sumOf { it.dwellMs }
            InsightsUi(
                grid = grid,
                total = total,
                peakHour = if (total > 0) perHour.indices.maxByOrNull { perHour[it] } else null,
                latePercent = if (total > 0) (perHour[22] + perHour[23]) * 100 / total else 0,
                skipPercent = if (timed > 0) skips * 100 / timed else null,
                longest = longest ?: 0,
                avgSecondsPerReel = if (timed > 0) (dwell / timed / 1000).toInt() else null,
            )
        }
    }
}
