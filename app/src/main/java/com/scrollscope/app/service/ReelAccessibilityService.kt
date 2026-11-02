package com.scrollscope.app.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.scrollscope.app.MainActivity
import com.scrollscope.app.ScrollScopeApp
import com.scrollscope.app.data.Apps
import com.scrollscope.app.iron.IronRules
import com.scrollscope.app.overlay.IronBadge
import com.scrollscope.app.overlay.LockInfo
import com.scrollscope.app.overlay.OverlayActions
import com.scrollscope.app.overlay.OverlayManager
import com.scrollscope.app.util.Days
import com.scrollscope.app.util.DebugLog
import com.scrollscope.app.util.Format
import com.scrollscope.app.util.Sharing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReelAccessibilityService : AccessibilityService(), OverlayActions {

    private val app get() = ScrollScopeApp.instance
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var tracker: SessionTracker
    private lateinit var overlay: OverlayManager

    private var currentPkg: String? = null
    private var inPlayer = false
    private var enteredPlayerAt = 0L
    private var lastSwipeAt = 0L
    private var lastPlayerCheck = 0L
    private var receiverRegistered = false
    private var ironJob: Job? = null
    private var lastIronTick = 0L

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = leave("screen off", immediate = true)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _running.value = true
        tracker = SessionTracker(app.db.stats(), scope)
        overlay = OverlayManager(this, this)

        ContextCompat.registerReceiver(
            this, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true

        // Keep the bubble's number in sync with the database (and roll over at midnight).
        scope.launch {
            Days.todayFlow()
                .flatMapLatest { day -> app.db.stats().countForDay(day) }
                .combine(app.settingsState) { count, s -> count to s.dailyLimit }
                .collect { (count, limit) -> overlay.setCount(count, limit) }
        }
        DebugLog.log("SERVICE", null, "connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return
        val s = app.settingsState.value
        val tracked = (pkg == Apps.INSTAGRAM && s.trackInstagram) || (pkg == Apps.YOUTUBE && s.trackYoutube)
        val ironApp = pkg in IronRules.APPS

        if (!tracked && !ironApp) {
            if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && isRealAppSwitch(pkg, e.className)) {
                leave("switched to ${pkg.substringAfterLast('.')}")
            }
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (pkg != currentPkg) {
            currentPkg = pkg
            DebugLog.log("WINDOW", pkg, "app opened")
            startIronClock()
        }
        if (ironCheck(pkg)) return
        // Iron Owl still needs player time when reel counting is switched off for this app.
        if (!tracked && !(ironApp && s.ironEnabled)) return
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> refreshPlayer(pkg, now, tracked)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ->
                if (now - lastPlayerCheck > PLAYER_CHECK_MS) refreshPlayer(pkg, now, tracked)
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (!inPlayer) refreshPlayer(pkg, now, tracked)
                if (tracked) onScroll(pkg, now, e)
            }
        }
    }

    /** Keyboards, system UI and our own overlays don't count as leaving; opening ScrollScope itself does. */
    private fun isRealAppSwitch(pkg: String, cls: CharSequence?) =
        if (pkg == packageName) cls?.toString() == MainActivity::class.java.name
        else pkg != "com.android.systemui" && !pkg.contains("inputmethod") && !pkg.contains("keyboard")

    // ---- Iron Owl ----

    /** Ticks once a second while Instagram/YouTube is in front: counts player time and re-checks the rules. */
    private fun startIronClock() {
        if (ironJob?.isActive == true) return
        lastIronTick = SystemClock.elapsedRealtime()
        ironJob = scope.launch {
            while (true) {
                delay(1000)
                val pkg = currentPkg ?: break
                val now = SystemClock.elapsedRealtime()
                val dt = (now - lastIronTick).coerceIn(0L, 5_000L)
                lastIronTick = now
                if (inPlayer && pkg in IronRules.APPS) app.iron.addWatch(pkg, dt)
                ironCheck(pkg)
            }
            overlay.setIron(null)
        }
    }

    /** Applies Iron Owl to the app in front. Returns true while it's locked. */
    private fun ironCheck(pkg: String): Boolean {
        val s = app.settingsState.value
        if (!s.ironEnabled || pkg !in IronRules.APPS) {
            overlay.hideLock()
            overlay.setIron(null)
            return false
        }
        val wall = System.currentTimeMillis()
        val minute = Days.minuteOfDay(wall)
        val snap = app.iron.current()
        val watched = snap.watched(pkg)
        val limit = IronRules.limitMs(pkg, s)
        val passActive = snap.passActive(wall)
        val reason = IronRules.lockReason(pkg, minute, watched, passActive, s)
        if (reason != null) {
            if (inPlayer) {
                inPlayer = false
                overlay.hideBubble()
                tracker.onLeave(immediate = true)
            }
            if (!overlay.lockShowing) DebugLog.log("IRON", pkg, "locked: $reason")
            overlay.showLock(
                LockInfo(
                    reason = reason,
                    pkg = pkg,
                    watchedMin = (watched / 60_000).toInt(),
                    limitMin = (limit / 60_000).toInt(),
                    passUsed = snap.passUsedToday,
                    openHour = s.openHour,
                    closeHour = s.closeHour,
                    minuteOfDay = minute,
                )
            )
            return true
        }
        overlay.hideLock()

        val left = limit - watched
        val leftMin = ((left + 59_999) / 60_000).coerceAtLeast(1)
        overlay.setIron(
            when {
                passActive -> IronBadge(Format.mmss(snap.passUntil - wall), hot = true, granted = true)
                left <= IronRules.IRON_BUBBLE_MS -> IronBadge("${leftMin}m", hot = left <= IronRules.WARN_MS)
                else -> null
            }
        )
        if (inPlayer && !passActive && left <= IronRules.WARN_MS && app.iron.markWarned("limit_$pkg")) {
            overlay.showBanner(
                "$leftMin min of ${IronRules.feedLabel(pkg)} left",
                "Then I lock ${IronRules.appLabel(pkg)} until tomorrow.",
            )
        }
        val toClose = IronRules.minutesToClose(minute, s)
        if (toClose != null && toClose <= IronRules.NIGHT_WARN_MIN && app.iron.markWarned("night")) {
            overlay.showBanner(
                "Lights out at ${Format.clock(s.closeHour)}",
                "$toClose min left. Instagram and YouTube open again at ${Format.clock(s.openHour)}.",
            )
        }
        return false
    }

    /** [track] false: only note player time for Iron Owl; no reel counting, sessions or bubble. */
    private fun refreshPlayer(pkg: String, now: Long, track: Boolean = true) {
        lastPlayerCheck = now
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        val found = ReelDetector.isReelPlayer(root, pkg)
        if (found == inPlayer) return
        inPlayer = found
        if (found) {
            enteredPlayerAt = now
            DebugLog.log("WINDOW", pkg, "player ✓ ${ReelDetector.lastMatch}")
            if (track) {
                tracker.onEnterPlayer()
                overlay.showBubble()
            }
        } else {
            DebugLog.log("WINDOW", pkg, "left player")
            overlay.hideBubble()
            if (track) tracker.onLeave()
        }
    }

    private fun onScroll(pkg: String, now: Long, e: AccessibilityEvent) {
        if (!inPlayer) return
        if (now - enteredPlayerAt < ENTER_GRACE_MS) return
        if (Build.VERSION.SDK_INT >= 28 && e.scrollDeltaX != 0 && e.scrollDeltaX != -1 && e.scrollDeltaY == 0) {
            DebugLog.log("IGNORED", pkg, "horizontal scroll")
            return
        }
        val gap = now - lastSwipeAt
        if (gap < DEBOUNCE_MS) return
        lastSwipeAt = now

        scope.launch {
            val s = app.settingsState.value
            val n = tracker.onReel(pkg, s.pauseAfter)
            DebugLog.log("SCROLL", pkg, "reel #$n · gap ${gap / 1000}s")
            if (s.pauseEnabled && n >= tracker.nextPauseAt) {
                tracker.pauseShown()
                overlay.showPause(n)
            }
        }
    }

    private fun leave(reason: String, immediate: Boolean = false) {
        overlay.hideLock()
        app.iron.flush()
        if (currentPkg == null && !inPlayer) return
        DebugLog.log("WINDOW", currentPkg, "left: $reason")
        currentPkg = null
        inPlayer = false
        overlay.hideBubble()
        tracker.onLeave(immediate)
    }

    // ---- Overlay actions ----

    override fun openApp() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun dumpTree() {
        val text = TreeDumper.dump(rootInActiveWindow)
        val stamp = SimpleDateFormat("MMdd-HHmmss", Locale.US).format(Date())
        val pkg = rootInActiveWindow?.packageName?.toString()?.substringAfterLast('.') ?: "unknown"
        val file = File(Sharing.dumpsDir(this), "dump-$pkg-$stamp.txt")
        file.writeText(text)
        DebugLog.log("DUMP", null, file.name)
        Toast.makeText(this, "Screen tree saved. See Debug log.", Toast.LENGTH_SHORT).show()
    }

    fun requestDelayedDump(delayMs: Long) {
        scope.launch {
            delay(delayMs)
            dumpTree()
        }
    }

    override fun takeBreak() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        leave("took a break", immediate = true)
    }

    override fun keepWatching() = tracker.snoozePause(5)

    override fun closeLocked() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        leave("closed by Iron Hoot", immediate = true)
    }

    override fun passEarned() {
        val s = app.settingsState.value
        if (IronRules.isNight(Days.minuteOfDay(System.currentTimeMillis()), s.openHour, s.closeHour)) return
        if (!app.iron.usePass(System.currentTimeMillis())) return
        DebugLog.log("IRON", currentPkg, "pass granted")
        overlay.hideLock()
        currentPkg?.let { refreshPlayer(it, SystemClock.elapsedRealtime()) }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        _running.value = false
        if (receiverRegistered) unregisterReceiver(screenOffReceiver)
        if (::overlay.isInitialized) overlay.destroy()
        app.iron.flush()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val DEBOUNCE_MS = 700L
        const val ENTER_GRACE_MS = 800L
        const val PLAYER_CHECK_MS = 400L

        @Volatile
        var instance: ReelAccessibilityService? = null
            private set

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running
    }
}
