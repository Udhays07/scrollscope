package com.scrollscope.app.service

import com.scrollscope.app.data.ReelEvent
import com.scrollscope.app.data.SessionEntity
import com.scrollscope.app.data.StatsDao
import com.scrollscope.app.util.Days
import com.scrollscope.app.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Groups reels into sessions. A session ends 30 s after you leave the player
 * (so a quick peek at a DM doesn't split it), or immediately on screen-off.
 */
class SessionTracker(private val dao: StatsDao, private val scope: CoroutineScope) {
    private val mutex = Mutex()
    private var sessionId: Long? = null
    private var sessionApp: String? = null
    private var lastEventId: Long? = null
    private var lastEventTs = 0L
    private var leftAt = 0L
    private var closeJob: Job? = null

    var sessionCount = 0
        private set
    var nextPauseAt = Int.MAX_VALUE
        private set

    fun onEnterPlayer() {
        closeJob?.cancel()
        closeJob = null
    }

    fun onLeave(immediate: Boolean = false) {
        if (closeJob?.isActive == true && !immediate) return
        closeJob?.cancel()
        leftAt = System.currentTimeMillis()
        closeJob = scope.launch {
            if (!immediate) delay(GRACE_MS)
            close()
        }
    }

    /** Records one reel. Returns how many reels this session has so far. */
    suspend fun onReel(pkg: String, pauseAfter: Int): Int = mutex.withLock {
        closeJob?.cancel()
        closeJob = null
        val now = System.currentTimeMillis()
        val day = Days.keyOf(now)

        var sid = sessionId
        if (sid == null || sessionApp != pkg) {
            sid = dao.insertSession(
                SessionEntity(app = pkg, day = day, startTs = now, endTs = now, reelCount = 0)
            )
            sessionId = sid
            sessionApp = pkg
            sessionCount = 0
            lastEventId = null
            nextPauseAt = pauseAfter
            DebugLog.log("SESSION", pkg, "start")
        }

        lastEventId?.let { dao.setDwell(it, now - lastEventTs) }
        lastEventId = dao.insertEvent(
            ReelEvent(sessionId = sid, app = pkg, ts = now, day = day, hour = Days.hourOf(now))
        )
        lastEventTs = now
        dao.bumpSession(sid, now)
        sessionCount += 1
        sessionCount
    }

    fun pauseShown() {
        nextPauseAt = Int.MAX_VALUE
    }

    fun snoozePause(more: Int) {
        nextPauseAt = sessionCount + more
    }

    private suspend fun close() = mutex.withLock {
        val id = lastEventId
        if (id != null && leftAt > lastEventTs) {
            dao.setDwell(id, (leftAt - lastEventTs).coerceAtMost(MAX_LAST_DWELL_MS))
        }
        if (sessionId != null) DebugLog.log("SESSION", sessionApp, "end · $sessionCount reels")
        sessionId = null
        sessionApp = null
        lastEventId = null
        sessionCount = 0
        nextPauseAt = Int.MAX_VALUE
    }

    companion object {
        const val GRACE_MS = 30_000L
        const val MAX_LAST_DWELL_MS = 180_000L
    }
}
