package com.scrollscope.app.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import com.scrollscope.app.ScrollScopeApp
import com.scrollscope.app.ui.theme.ScrollScopeTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

interface OverlayActions {
    fun openApp()
    fun takeBreak()
    fun keepWatching()
}

data class BubbleState(val count: Int = 0, val limit: Int = 200)

/**
 * Draws Hoot's bubble, its menu and the mindful-pause dialog.
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
            setContent { ScrollScopeTheme(content) }
        }

    fun setCount(count: Int, limit: Int) {
        state.value = BubbleState(count, limit)
    }

    // ---- Bubble ----

    fun showBubble() {
        val s = ScrollScopeApp.instance.settingsState.value
        if (!s.bubbleEnabled || System.currentTimeMillis() < snoozedUntil || bubble != null) return
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
        p.x = (p.x + dx.toInt()).coerceIn(0, screenW - dp(80))
        p.y = (p.y + dy.toInt()).coerceIn(0, screenH - dp(112))
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
        val anchorY = p.y + dp(112)
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
        if (pause != null) return
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

    fun destroy() {
        hideBubble()
        hidePause()
        owner.destroy()
    }
}
