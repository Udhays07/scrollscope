package com.scrollscope.app.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollscope.app.iron.IronRules
import com.scrollscope.app.iron.LockReason
import com.scrollscope.app.ui.components.IronAvatar
import com.scrollscope.app.ui.components.IronOwl
import com.scrollscope.app.ui.components.IronPose
import com.scrollscope.app.ui.theme.IronColors
import com.scrollscope.app.util.Format
import com.scrollscope.app.util.rememberMotionEnabled
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LockInfo(
    val reason: LockReason,
    val pkg: String,
    val watchedMin: Int,
    val limitMin: Int,
    val passUsed: Boolean,
    val openHour: Int,
    val closeHour: Int,
    val minuteOfDay: Int,
)

private val TapLines = listOf("No.", "Still no.", "Go outside.", "I'm made of iron.", "Tapping won't work.")
private val NightLines = listOf("It's late.", "Go to sleep.", "Phone down.", "Bed. Now.", "I don't sleep. You should.")

/**
 * Iron Owl's lock screen. Shutters slam over the blocked app, Iron Owl drops
 * onto them, and his eyes follow the user's finger. Holding him for
 * [IronRules.HOLD_MS] earns the day's one 5-minute pass (daily limit only).
 */
@Composable
fun LockContent(state: StateFlow<LockInfo?>, onClose: () -> Unit, onPassEarned: () -> Unit) {
    val info = state.collectAsStateWithLifecycle().value ?: return
    val night = info.reason == LockReason.Night
    val canPass = !night && !info.passUsed
    val motion = rememberMotionEnabled()
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val passEarned by rememberUpdatedState(onPassEarned)

    // Entrance: shutters slam (0..1 closed), the screen thuds, Iron Owl drops, eyes flicker on, copy rises.
    val shutter = remember { Animatable(if (motion) 0f else 1f) }
    val thud = remember { Animatable(1f) }
    val drop = remember { Animatable(if (motion) 0f else 1f) }
    val lit = remember { Animatable(if (motion) 0f else 1f) }
    val copyIn = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!motion) return@LaunchedEffect
        shutter.snapTo(0f); drop.snapTo(0f); lit.snapTo(0f); copyIn.snapTo(0f)
        shutter.animateTo(1f, tween(380, easing = FastOutLinearInEasing))
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        launch { thud.snapTo(0f); thud.animateTo(1f, tween(320)) }
        launch { delay(120); drop.animateTo(1f, tween(620, easing = LinearEasing)) }
        launch { delay(670); lit.animateTo(1f, tween(700, easing = LinearEasing)) }
        launch { delay(670); copyIn.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
    }
    var pose by remember { mutableStateOf(if (night) IronPose.Night else IronPose.Guard) }
    LaunchedEffect(night) { if (pose != IronPose.Granted) pose = if (night) IronPose.Night else IronPose.Guard }
    var shakeKey by remember { mutableIntStateOf(0) }
    var fumeKey by remember { mutableIntStateOf(0) }
    var line by remember { mutableStateOf<String?>(null) }
    var lineKey by remember { mutableIntStateOf(0) }
    var sticky by remember { mutableStateOf(false) }
    fun say(text: String, stay: Boolean = false) { line = text; sticky = stay; lineKey++ }
    LaunchedEffect(lineKey) { if (!sticky && line != null) { delay(1400); line = null } }

    var taps by remember { mutableIntStateOf(0) }
    var tapJob by remember { mutableStateOf<Job?>(null) }
    fun onTap() {
        taps++
        tapJob?.cancel()
        tapJob = scope.launch { delay(1600); taps = 0 }
        val lines = if (night) NightLines else TapLines
        say(lines[(taps - 1).coerceAtMost(lines.lastIndex)])
        shakeKey++
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (taps >= 3) fumeKey++
    }

    val hold = remember { Animatable(0f) }
    var holding by remember { mutableStateOf(false) }
    LaunchedEffect(holding) {
        if (!holding) return@LaunchedEffect
        var said = 0
        snapshotFlow { hold.value }.collect { p ->
            if (p > 0.12f && said < 1) { said = 1; say("You sure?", stay = true) }
            if (p > 0.45f && said < 2) { said = 2; say("Really sure?", stay = true); fumeKey++ }
            if (p > 0.78f && said < 3) { said = 3; say("…ugh. Fine. Keep going.", stay = true) }
        }
    }

    var look by remember { mutableStateOf(Offset.Zero) }
    var owlCenter by remember { mutableStateOf(Offset.Zero) }

    val bars = WindowInsets.systemBars.asPaddingValues()
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        val c = e.changes.firstOrNull() ?: continue
                        look = if (c.pressed) {
                            val d = c.position - owlCenter
                            Offset((d.x / (size.width * 0.35f)).coerceIn(-1f, 1f), (d.y / (size.height * 0.25f)).coerceIn(-1f, 1f))
                        } else Offset.Zero
                    }
                }
            }
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Shutters(shutter.value, night, Modifier.fillMaxSize())
        if (night) Moon(Modifier.align(Alignment.TopEnd).padding(top = max(bars.calculateTopPadding(), 24.dp) + 36.dp, end = 32.dp).alpha(copyIn.value))

        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = thudOffset(thud.value) * density }
                .padding(
                    top = max(bars.calculateTopPadding(), 28.dp) + 8.dp,
                    bottom = max(bars.calculateBottomPadding(), 20.dp) + 12.dp,
                    start = 24.dp,
                    end = 24.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier
                    .alpha(copyIn.value)
                    .clip(RoundedCornerShape(16.dp))
                    .background(IronColors.I800.copy(alpha = 0.85f))
                    .border(1.dp, IronColors.I700, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    if (night) Icons.Rounded.Bedtime else Icons.Rounded.Lock, null,
                    tint = if (night) IronColors.Moon else IronColors.EmberText, modifier = Modifier.size(16.dp),
                )
                Text(
                    if (night) "Night lock" else "${IronRules.appLabel(info.pkg)} is locked",
                    color = IronColors.I200, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                )
            }

            Box(Modifier.padding(top = 20.dp).size(216.dp), contentAlignment = Alignment.Center) {
                HoldRing(hold.value, Modifier.fillMaxSize())
                IronOwl(
                    Modifier
                        .fillMaxSize()
                        .padding(22.dp)
                        .onGloballyPositioned { c ->
                            owlCenter = c.positionInRoot() + Offset(c.size.width / 2f, c.size.height / 2f)
                        }
                        .graphicsLayer {
                            val d = dropFrame(drop.value)
                            translationY = d.y * density
                            scaleX = d.sx; scaleY = d.sy; alpha = d.alpha
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                        }
                        .semantics { contentDescription = if (canPass) "Iron Owl. Press and hold for 10 seconds to earn a 5-minute pass." else "Iron Owl" }
                        .pointerInput(canPass, night) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                val job = scope.launch {
                                    delay(220)
                                    if (!canPass) {
                                        say(if (night) "No passes at night." else "No passes left.")
                                        shakeKey++
                                        return@launch
                                    }
                                    holding = true
                                    hold.animateTo(1f, tween((IronRules.HOLD_MS * (1f - hold.value)).toInt(), easing = LinearEasing))
                                    holding = false
                                    pose = IronPose.Granted
                                    say("Fine. 5 minutes.", stay = true)
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    // Outlives the finger lifting, so letting go after the ring fills still grants the pass.
                                    scope.launch { delay(if (motion) 1300L else 300L); passEarned() }
                                }
                                val start = System.currentTimeMillis()
                                var released = false
                                try {
                                    waitForUpOrCancellation()
                                    released = true
                                } finally {
                                    if (job.isActive) {
                                        job.cancel()
                                        if (released && System.currentTimeMillis() - start < 220) {
                                            onTap()
                                        } else if (holding) {
                                            holding = false
                                            if (released) say("Hmph.")
                                            scope.launch { hold.animateTo(0f, tween(380)) }
                                        }
                                    }
                                }
                            }
                        },
                    pose = pose,
                    eyeOffset = look,
                    straining = holding,
                    shakeKey = shakeKey,
                    fumeKey = fumeKey,
                    lit = lit.value,
                )
                Speech(line, Modifier.align(Alignment.TopCenter))
            }

            Column(
                Modifier.alpha(copyIn.value).graphicsLayer { translationY = (1f - copyIn.value) * 12.dp.toPx() },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val (title, body) = when {
                    night -> "Go to sleep." to "${IronRules.appLabel(info.pkg)} opens at ${Format.clock(info.openHour)}."
                    info.passUsed -> "That was your pass." to "${IronRules.appLabel(info.pkg)} stays locked for the rest of today."
                    else -> "See you tomorrow." to "You've watched ${info.limitMin} min of ${IronRules.feedLabel(info.pkg)} today."
                }
                Text(
                    title, color = Color(0xFFEDEDED), fontSize = 28.sp, lineHeight = 32.sp,
                    fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                )
                Text(body, color = IronColors.I300, fontSize = 15.sp, textAlign = TextAlign.Center)
                if (night) HoursStrip(info, Modifier.padding(top = 12.dp).widthIn(max = 400.dp))
                else LimitMeter(info, Modifier.padding(top = 12.dp).widthIn(max = 400.dp))
            }

            Spacer(Modifier.weight(1f))

            Column(
                Modifier.alpha(copyIn.value).widthIn(max = 400.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = IronColors.I200,
                    contentColor = IronColors.I950,
                ) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Close, null, Modifier.size(20.dp))
                        Text(
                            "  Close ${IronRules.appLabel(info.pkg)}",
                            fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
                Row(
                    Modifier.heightIn(min = 32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        if (canPass) Icons.Rounded.TouchApp else Icons.Rounded.Block, null,
                        tint = if (canPass) IronColors.EmberHot else IronColors.I500, modifier = Modifier.size(18.dp),
                    )
                    Text(
                        when {
                            holding -> "Keep holding…"
                            night -> "No passes at night"
                            info.passUsed -> "No passes left today"
                            else -> "Hold Hoot 10 s for a 5-min pass · 1 left"
                        },
                        color = IronColors.I300, fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

/** Screen-thud after the shutters land, in dp: down 5, up 2, settle. */
private fun thudOffset(q: Float): Float = when {
    q >= 1f -> 0f
    q < 0.3f -> 5f * (q / 0.3f)
    q < 0.6f -> 5f - 7f * ((q - 0.3f) / 0.3f)
    else -> -2f * (1f - (q - 0.6f) / 0.4f)
}

private data class Drop(val y: Float, val sx: Float, val sy: Float, val alpha: Float)

/** Falls from above (accelerating), squashes on landing, springs back. y in dp. */
private fun dropFrame(q: Float): Drop = when {
    q >= 1f -> Drop(0f, 1f, 1f, 1f)
    q < 0.58f -> { val k = q / 0.58f; Drop(-260f * (1f - k * k), 1f, 1f, (q / 0.06f).coerceAtMost(1f)) }
    q < 0.70f -> { val k = (q - 0.58f) / 0.12f; Drop(6f * k, 1f + 0.08f * k, 1f - 0.12f * k, 1f) }
    q < 0.85f -> { val k = (q - 0.70f) / 0.15f; Drop(6f - 10f * k, 1.08f - 0.11f * k, 0.88f + 0.16f * k, 1f) }
    else -> { val k = (q - 0.85f) / 0.15f; Drop(-4f * (1f - k), 0.97f + 0.03f * k, 1.04f - 0.04f * k, 1f) }
}

@Composable
private fun Shutters(closed: Float, night: Boolean, modifier: Modifier) {
    val slat = if (night) IronColors.NightSlat else Color(0xFF171A1E)
    val gap = if (night) IronColors.NightGap else Color(0xFF0E1013)
    val edge = if (night) IronColors.NightEdge else Color(0xFF1F2328)
    Canvas(modifier) {
        val half = size.height / 2f
        val travel = (1f - closed) * (half + 2f)
        drawShutter(top = -travel, height = half + 1f, slat, gap, edge)
        drawShutter(top = half + travel, height = half, slat, gap, edge)
        // The bolt line where the two panels meet.
        if (closed > 0.98f) {
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, IronColors.I600, IronColors.I400, IronColors.I600, Color.Transparent)),
                topLeft = Offset(0f, half - 1.dp.toPx()),
                size = Size(size.width, 3.dp.toPx()),
            )
        }
    }
}

private fun DrawScope.drawShutter(top: Float, height: Float, slat: Color, gap: Color, edge: Color) {
    drawRect(slat, Offset(0f, top), Size(size.width, height))
    val pitch = 30.dp.toPx()
    var y = top + pitch
    while (y < top + height) {
        drawRect(gap, Offset(0f, y - 1.4.dp.toPx()), Size(size.width, 1.4.dp.toPx()))
        drawRect(edge, Offset(0f, y), Size(size.width, 1.dp.toPx()))
        y += pitch
    }
    drawRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.03f), Color.Transparent), startY = top, endY = top + height * 0.3f),
        Offset(0f, top), Size(size.width, height),
    )
}

@Composable
private fun Moon(modifier: Modifier) {
    Canvas(modifier.size(64.dp)) {
        val r = 14.dp.toPx()
        val c = Offset(size.width - r - 4.dp.toPx(), r + 4.dp.toPx())
        // Crescent: a lit disc with a second disc of shutter colour bitten out of it.
        drawCircle(IronColors.Moon.copy(alpha = 0.9f), r, c)
        drawCircle(IronColors.NightSlat, r, c + Offset(-r * 0.55f, r * 0.3f))
        drawCircle(IronColors.Moon.copy(alpha = 0.55f), 1.6.dp.toPx(), Offset(8.dp.toPx(), 30.dp.toPx()))
        drawCircle(IronColors.Moon.copy(alpha = 0.4f), 1.4.dp.toPx(), Offset(26.dp.toPx(), 56.dp.toPx()))
    }
}

@Composable
private fun HoldRing(progress: Float, modifier: Modifier) {
    Canvas(modifier) {
        val w = 3.dp.toPx()
        val inset = w
        val arc = Size(size.width - 2 * inset, size.height - 2 * inset)
        drawArc(IronColors.I400.copy(alpha = 0.14f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(2.dp.toPx()))
        if (progress > 0f) {
            drawArc(IronColors.Ember.copy(alpha = 0.35f), -90f, 360f * progress, false, Offset(inset, inset), arc, style = Stroke(w * 2.6f, cap = StrokeCap.Round))
            drawArc(IronColors.EmberHot, -90f, 360f * progress, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun Speech(text: String?, modifier: Modifier) {
    val shown = remember { MutableTransitionState(false) }
    var last by remember { mutableStateOf("") }
    if (text != null) last = text
    shown.targetState = text != null
    AnimatedVisibility(
        visibleState = shown,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        enter = fadeIn(tween(140)) + scaleIn(tween(180, easing = FastOutSlowInEasing), initialScale = 0.6f),
        exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.8f),
    ) {
        Text(
            last,
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(IronColors.I200)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            color = IronColors.I950, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LimitMeter(info: LockInfo, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(IronColors.I400, IronColors.Ember))),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${info.watchedMin.coerceAtMost(info.limitMin)} / ${info.limitMin} min", color = IronColors.I300, fontSize = 12.sp)
            Text("Opens ${Format.clock(info.openHour)} tomorrow", color = IronColors.I300, fontSize = 12.sp)
        }
    }
}

/** 24-hour strip: the open window in steel, "now" as a moonlit tick. */
@Composable
private fun HoursStrip(info: LockInfo, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val w = maxWidth
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val y = size.height / 2f
            val h = 6.dp.toPx()
            drawRoundRect(IronColors.NightEdge, Offset(0f, y - h / 2), Size(size.width, h), androidx.compose.ui.geometry.CornerRadius(h))
            val x0 = size.width * info.openHour / 24f
            val x1 = size.width * info.closeHour / 24f
            drawRoundRect(IronColors.I400, Offset(x0, y - h / 2), Size(x1 - x0, h), androidx.compose.ui.geometry.CornerRadius(h))
            val nx = size.width * info.minuteOfDay / 1440f
            drawRoundRect(
                Color(0xFFA6C8FF), Offset(nx - 1.5.dp.toPx(), 0f), Size(3.dp.toPx(), size.height),
                androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            )
        }
        Box(Modifier.fillMaxWidth().padding(top = 18.dp)) {
            Text("12 AM", color = IronColors.I300, fontSize = 12.sp)
            Text(
                Format.hour12(info.openHour), color = IronColors.I300, fontSize = 12.sp,
                modifier = Modifier.offset(x = w * (info.openHour / 24f) - 14.dp),
            )
            Text(Format.hour12(info.closeHour % 24), color = IronColors.I300, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

/** Heads-up card that drops in from the top: 5 min left, or lights out soon. */
@Composable
fun BannerContent(title: String, body: String, onDismiss: () -> Unit) {
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = shown,
        enter = slideInVertically(tween(360, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(200)),
    ) {
        Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Surface(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = IronColors.I900,
                border = androidx.compose.foundation.BorderStroke(1.dp, IronColors.I700),
                shadowElevation = 8.dp,
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IronAvatar(44.dp)
                    Column {
                        Text(title, color = Color(0xFFEDEDED), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(body, color = IronColors.I300, fontSize = 13.sp, lineHeight = 17.sp)
                    }
                }
            }
        }
    }
}
