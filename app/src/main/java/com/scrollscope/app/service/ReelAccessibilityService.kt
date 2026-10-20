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
import com.scrollscope.app.overlay.OverlayActions
import com.scrollscope.app.overlay.OverlayManager
import com.scrollscope.app.util.Days
import com.scrollscope.app.util.DebugLog
import com.scrollscope.app.util.Sharing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

        if (!tracked) {
            if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && isRealAppSwitch(pkg)) {
                leave("switched to ${pkg.substringAfterLast('.')}")
            }
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (pkg != currentPkg) {
            currentPkg = pkg
            DebugLog.log("WINDOW", pkg, "app opened")
        }
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> refreshPlayer(pkg, now)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ->
                if (now - lastPlayerCheck > PLAYER_CHECK_MS) refreshPlayer(pkg, now)
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (!inPlayer) refreshPlayer(pkg, now)
                onScroll(pkg, now, e)
            }
        }
    }

    /** Keyboards, system UI and our own overlay don't count as leaving. */
    private fun isRealAppSwitch(pkg: String) =
        pkg != packageName && pkg != "com.android.systemui" &&
            !pkg.contains("inputmethod") && !pkg.contains("keyboard")

    private fun refreshPlayer(pkg: String, now: Long) {
        lastPlayerCheck = now
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        val found = ReelDetector.isReelPlayer(root, pkg)
        if (found == inPlayer) return
        inPlayer = found
        if (found) {
            enteredPlayerAt = now
            DebugLog.log("WINDOW", pkg, "player ✓ ${ReelDetector.lastMatch}")
            tracker.onEnterPlayer()
            overlay.showBubble()
        } else {
            DebugLog.log("WINDOW", pkg, "left player")
            overlay.hideBubble()
            tracker.onLeave()
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

    override fun dumpTree() {
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

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        _running.value = false
        if (receiverRegistered) unregisterReceiver(screenOffReceiver)
        if (::overlay.isInitialized) overlay.destroy()
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
