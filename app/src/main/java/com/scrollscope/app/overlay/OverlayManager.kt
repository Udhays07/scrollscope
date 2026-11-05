package com.scrollscope.app.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import com.scrollscope.app.ScrollScopeApp
import com.scrollscope.app.iron.TimerReading
import com.scrollscope.app.ui.theme.ScrollScopeTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

interface OverlayActions {
    fun openApp()
    fun takeBreak()
    fun keepWatching()
    fun closeLocked()
    fun passEarned()
}

data class BubbleState(val count: Int = 0, val limit: Int = 200, val iron: TimerReading? = null)

/**
 * Draws Hoot's bubble, its menu, the mindful-pause dialog, and Iron Owl's
 * lock screen and heads-up banner.
 * Uses TYPE_ACCESSIBILITY_OVERLAY, which an accessibility service may add
 * without the separate "Display over other apps" permission.
 */
class OverlayManager(
    private val service: AccessibilityService,
    private val actions: OverlayActions,
) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val owner = OverlayOwner().also { it.start() }
    private val density = service.resources.displayMetrics.density
    private val screenW get() = service.resources.displayMetrics.widthPixels
    private val screenH get() = service.resources.displayMetrics.heightPixels

    val state = MutableStateFlow(BubbleState())

    private var bubble: View? = null
    private var menu: View? = null
    private var pause: View? = null
    private var lock: View? = null
    private var banner: View? = null
    private val main = Handler(Looper.getMainLooper())
    private val hideBannerRunnable = Runnable { hideBanner() }
    val lockInfo = MutableStateFlow<LockInfo?>(null)
    val lockShowing get() = lock != null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var snoozedUntil = 0L

    private fun dp(v: Int) = (v * density).toInt()

    private fun params(w: Int, h: Int, focusable: Boolean = false) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun compose(content: @Composable () -> Unit): ComposeView =
        ComposeView(service).apply {
            owner.attach(this)
            setContent { ScrollScopeTheme(content = content) }
        }

    fun setCount(count: Int, limit: Int) {
        state.value = state.value.copy(count = count, limit = limit)
    }

    fun setIron(reading: TimerReading?) {
        if (state.value.iron != reading) state.value = state.value.copy(iron = reading)
    }

    // ---- Bubble ----

    fun showBubble() {
        val s = ScrollScopeApp.instance.settingsState.value
        if (!s.bubbleEnabled || System.currentTimeMillis() < snoozedUntil || bubble != null || lock != null) return
        val p = params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            x = if (s.bubbleX >= 0) s.bubbleX else screenW - dp(92)
            y = s.bubbleY
        }
        val v = compose {
            BubbleContent(
                state = state,
                onDrag = { dx, dy -> dragBy(dx, dy) },
                onDragEnd = ::savePosition,
                onTap = ::showMenu,
            )
        }
        runCatching { wm.addView(v, p) }.onSuccess {
            bubble = v
            bubbleParams = p
        }
    }

    fun hideBubble() {
        hideMenu()
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
    }

    private fun dragBy(dx: Float, dy: Float) {
        val v = bubble ?: return
        val p = bubbleParams ?: return
        // The Iron timer badge can make the bubble wider than 80 dp; clamp by its measured width.
        val w = if (v.width > 0) v.width else dp(80)
        p.x = (p.x + dx.toInt()).coerceIn(0, (screenW - w).coerceAtLeast(0))
        // Iron mode makes the bubble taller (120 dp); clamp by its measured height.
        val h = if (v.height > 0) v.height else dp(112)
        p.y = (p.y + dy.toInt()).coerceIn(0, (screenH - h).coerceAtLeast(0))
        runCatching { wm.updateViewLayout(v, p) }
    }

    private fun savePosition() {
        val p = bubbleParams ?: return
        val app = ScrollScopeApp.instance
        app.appScope.launch { app.settings.update { it.copy(bubbleX = p.x, bubbleY = p.y) } }
    }

    // ---- Menu ----

    private fun showMenu() {
        if (menu != null) return
        val p = bubbleParams ?: return
        val anchorX = p.x
        val anchorY = p.y + (bubble?.height?.takeIf { it > 0 } ?: dp(112))
        val v = compose {
            MenuContent(
                anchorXpx = anchorX,
                anchorYpx = anchorY,
                onDismiss = ::hideMenu,
                onSnooze = {
                    snoozedUntil = System.currentTimeMillis() + 10 * 60_000
                    hideBubble()
                },
                onOpenApp = { hideMenu(); actions.openApp() },
                onHideToday = {
                    snoozedUntil = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault())
                        .toInstant().toEpochMilli()
                    hideBubble()
                },
            )
        }
        val mp = params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        runCatching { wm.addView(v, mp) }.onSuccess { menu = v }
    }

    private fun hideMenu() {
        menu?.let { runCatching { wm.removeView(it) } }
        menu = null
    }

    // ---- Mindful pause ----

    fun showPause(count: Int) {
        if (pause != null || lock != null) return
        val v = compose {
            PauseContent(
                count = count,
                onKeepWatching = { hidePause(); actions.keepWatching() },
                onTakeBreak = { hidePause(); actions.takeBreak() },
            )
        }
        val pp = params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        runCatching { wm.addView(v, pp) }.onSuccess { pause = v }
    }

    fun hidePause() {
        pause?.let { runCatching { wm.removeView(it) } }
        pause = null
    }

    // ---- Iron Owl ----

    fun showLock(info: LockInfo) {
        lockInfo.value = info
        if (lock != null) return
        hideBubble()
        hidePause()
        hideBanner()
        val v = compose {
            LockContent(lockInfo, onClose = { actions.closeLocked() }, onPassEarned = { actions.passEarned() })
        }
        val lp = params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT).apply {
            // Cover the cutout band too, so the blocked app can't peek through above the shutters.
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        runCatching { wm.addView(v, lp) }.onSuccess { lock = v }
    }

    fun hideLock() {
        lock?.let { runCatching { wm.removeView(it) } }
        lock = null
        lockInfo.value = null
    }

    fun showBanner(title: String, body: String) {
        if (lock != null) return
        hideBanner()
        val v = compose { BannerContent(title, body, onDismiss = ::hideBanner) }
        val bp = params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        runCatching { wm.addView(v, bp) }.onSuccess {
            banner = v
            main.postDelayed(hideBannerRunnable, BANNER_MS)
        }
    }

    fun hideBanner() {
        main.removeCallbacks(hideBannerRunnable)
        banner?.let { runCatching { wm.removeView(it) } }
        banner = null
    }

    fun destroy() {
        hideBubble()
        hidePause()
        hideLock()
        hideBanner()
        owner.destroy()
    }

    private companion object {
        const val BANNER_MS = 6500L
    }
}
