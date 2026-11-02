package com.scrollscope.app.ui.morph

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseOutExpo
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollscope.app.ScrollScopeApp
import com.scrollscope.app.data.AppSettings
import com.scrollscope.app.data.Apps
import com.scrollscope.app.iron.IronRules
import com.scrollscope.app.ui.components.IronAvatar
import com.scrollscope.app.ui.components.IronOwl
import com.scrollscope.app.ui.components.Mood
import com.scrollscope.app.ui.components.Owl
import com.scrollscope.app.ui.theme.HootScheme
import com.scrollscope.app.ui.theme.IronColors
import com.scrollscope.app.ui.theme.ScrollScopeTheme
import com.scrollscope.app.util.Days
import com.scrollscope.app.util.Format
import com.scrollscope.app.util.rememberMotionEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

enum class OwlMode { Hoot, Iron }

/**
 * Switching between Hoot and Iron Hoot. Screens call [ask]; the host shows the
 * confirmation, then re-themes the whole app behind an expanding ring.
 */
class IronMorph internal constructor() {
    internal var asking by mutableStateOf<OwlMode?>(null)
    internal var askFrom = Offset.Zero
    internal var running by mutableStateOf(false)

    /** Bumped each time Iron Hoot arrives, so the top-bar avatar can flip in. */
    var arrivals by mutableIntStateOf(0)
        internal set

    /** [from] is where the ring starts when going back to Hoot, in root coordinates. */
    fun ask(to: OwlMode, from: Offset = Offset.Zero) {
        if (running || asking != null) return
        askFrom = from
        asking = to
    }
}

val LocalIronMorph = staticCompositionLocalOf { IronMorph() }
val LocalSnack = staticCompositionLocalOf { SnackbarHostState() }

private const val ARM_MS = 2500
private val MorphEasing = CubicBezierEasing(0.65f, 0f, 0.25f, 1f)

/**
 * Wraps the app in its theme and owns the switch. The current frame is kept in a
 * graphics layer; on a switch it's frozen to a bitmap, the theme flips underneath,
 * and the old frame is cut away by a circle growing from Iron Hoot (or Hoot).
 */
@Composable
fun IronMorphHost(iron: Boolean, content: @Composable () -> Unit) {
    val app = ScrollScopeApp.instance
    val morph = remember { IronMorph() }
    val snack = remember { SnackbarHostState() }
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val motion = rememberMotionEnabled()

    var old by remember { mutableStateOf<ImageBitmap?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var toIronTone by remember { mutableStateOf(true) }
    val reveal = remember { Animatable(0f) }
    val thud = remember { Animatable(1f) }
    val glint = remember { Animatable(1f) }
    var open by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf(OwlMode.Iron) }

    LaunchedEffect(morph.asking) {
        morph.asking?.let { kind = it; open = true }
    }

    fun close() {
        open = false
        morph.asking = null
    }

    fun say(text: String) = scope.launch {
        snack.currentSnackbarData?.dismiss()
        snack.showSnackbar(text)
    }

    suspend fun runSwitch(to: OwlMode, from: Offset) {
        val toIron = to == OwlMode.Iron
        try {
            if (motion) old = layer.toImageBitmap()
            origin = from
            toIronTone = toIron
            reveal.snapTo(0f)
            // The hold is checked inside the write itself, so nothing can sneak past the Settings tile.
            var refused: AppSettings? = null
            app.settings.update { cur ->
                val now = System.currentTimeMillis()
                if (!toIron && IronRules.holdsOn(Days.minuteOfDay(now), app.iron.current(), cur)) {
                    refused = cur
                    cur
                } else {
                    cur.copy(ironEnabled = toIron)
                }
            }
            refused?.let { cur ->
                val night = IronRules.isNight(Days.minuteOfDay(System.currentTimeMillis()), cur.openHour, cur.closeHour)
                say(if (night) "Iron Hoot holds on until ${Format.clock(cur.openHour)}." else "Iron Hoot holds on until tomorrow.")
                return
            }
            app.settingsState.first { it.ironEnabled == toIron }
            if (motion) {
                withFrameNanos { }
                reveal.animateTo(1f, tween(if (toIron) 900 else 650, easing = MorphEasing))
            }
            old = null
            if (toIron) {
                morph.arrivals++
                if (motion) {
                    scope.launch { thud.snapTo(0f); thud.animateTo(1f, tween(420, easing = LinearEasing)) }
                    scope.launch { glint.snapTo(0f); glint.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
                }
                val reels = app.iron.current().watched(Apps.INSTAGRAM) / 60_000
                say(if (reels > 0) "Iron Hoot is on. Today's $reels min of Reels already count." else "Iron Hoot is on. Your limits count from now.")
            } else {
                say("Back to Hoot. Nothing is locked any more.")
            }
        } finally {
            old = null
            morph.running = false
        }
    }

    fun switchTo(to: OwlMode, from: Offset) {
        if (morph.running) return
        morph.running = true
        scope.launch { runSwitch(to, from) }
    }

    ScrollScopeTheme(iron) {
        CompositionLocalProvider(LocalIronMorph provides morph, LocalSnack provides snack) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationY = thudOffset(thud.value) * 7.dp.toPx() }
                        .drawWithContent {
                            layer.record { this@drawWithContent.drawContent() }
                            drawLayer(layer)
                            old?.let { drawOldFrame(it, origin, reveal.value, toIronTone) }
                            if (glint.value < 1f) drawGlint(glint.value)
                        },
                ) { content() }
                MorphDialog(
                    open = open,
                    kind = kind,
                    onCancel = ::close,
                    onConfirm = { from ->
                        // Ignore a confirm that lands after Cancel/Back, or a second tap while closing.
                        if (!open) return@MorphDialog
                        close()
                        switchTo(kind, if (kind == OwlMode.Hoot) morph.askFrom else from)
                    },
                )
            }
        }
    }
}

/** A short drop-and-settle, like the phone took the weight of the armour. 0..1 → -1..1. */
private fun thudOffset(p: Float): Float =
    if (p >= 1f) 0f else sin(3f * PI.toFloat() * p) * (1f - p).pow(1.5f)

private fun DrawScope.drawOldFrame(old: ImageBitmap, c: Offset, p: Float, iron: Boolean) {
    val corners = listOf(Offset.Zero, Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height))
    val r = (corners.maxOf { (it - c).getDistance() } + 24.dp.toPx()) * p
    clipPath(Path().apply { addOval(Rect(c, r)) }, ClipOp.Difference) { drawImage(old) }
    if (p <= 0f || p >= 1f) return
    val fade = if (p > 0.85f) (1f - p) / 0.15f else 1f
    val tone = if (iron) IronColors.Ember else HootScheme.primary
    drawCircle(tone.copy(alpha = 0.3f * fade), r, c, style = Stroke(20.dp.toPx()))
    drawCircle(tone.copy(alpha = fade), r, c, style = Stroke(4.dp.toPx()))
    if (iron) drawCircle(IronColors.EmberHot.copy(alpha = 0.6f * fade), r - 6.dp.toPx(), c, style = Stroke(2.dp.toPx()))
}

/** One steel glint sweeping across the freshly armoured app. */
private fun DrawScope.drawGlint(p: Float) {
    val w = size.width
    val cx = -0.4f * w + p * 1.8f * w
    drawRect(
        Brush.linearGradient(
            0f to Color.Transparent,
            0.42f to IronColors.I200.copy(alpha = 0.08f),
            0.5f to Color.White.copy(alpha = 0.16f),
            0.58f to IronColors.I200.copy(alpha = 0.08f),
            1f to Color.Transparent,
            start = Offset(cx - 0.35f * w, 0f),
            end = Offset(cx + 0.35f * w, size.height * 0.35f),
        ),
    )
}

@Composable
private fun MorphDialog(open: Boolean, kind: OwlMode, onCancel: () -> Unit, onConfirm: (Offset) -> Unit) {
    BackHandler(open, onCancel)
    AnimatedVisibility(open, enter = fadeIn(tween(250)), exit = fadeOut(tween(380, delayMillis = 120))) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f))
                .pointerInput(Unit) { detectTapGestures { onCancel() } },
        )
    }
    AnimatedVisibility(
        open,
        enter = fadeIn(tween(300)) + scaleIn(tween(420, easing = EaseOutExpo), initialScale = 0.94f) +
            slideInVertically(tween(420, easing = EaseOutExpo)) { it / 14 },
        exit = fadeOut(tween(280)) + scaleOut(tween(320), targetScale = 0.9f),
    ) {
        Box(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            val card = Modifier.widthIn(max = 420.dp).fillMaxWidth().pointerInput(Unit) { detectTapGestures { } }
            if (kind == OwlMode.Iron) IronConfirm(card, onCancel, onConfirm) else HootConfirm(card, onCancel) { onConfirm(Offset.Zero) }
        }
    }
}

/**
 * "Switch to Iron Hoot?" He drops in, his eyes flicker on, and the rules are
 * stamped in white-hot and cool to steel. The confirm button charges for
 * [ARM_MS] so a quick tap can't skip reading them.
 */
@Composable
private fun IronConfirm(modifier: Modifier, onCancel: () -> Unit, onConfirm: (Offset) -> Unit) {
    val s by ScrollScopeApp.instance.settingsState.collectAsStateWithLifecycle()
    val motion = rememberMotionEnabled()
    val scope = rememberCoroutineScope()
    var owlCenter by remember { mutableStateOf(Offset.Zero) }
    val drop = remember { Animatable(if (motion) -1f else 0f) }
    val lit = remember { Animatable(if (motion) 0f else 1f) }
    val title = remember { Animatable(if (motion) 0f else 1f) }
    val charge = remember { Animatable(0f) }
    var fume by remember { mutableIntStateOf(0) }
    var poke by remember { mutableIntStateOf(0) }
    var arming by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (motion) {
            launch { delay(180); drop.animateTo(0f, spring(dampingRatio = 0.42f, stiffness = 380f)) }
            launch {
                delay(720)
                lit.animateTo(1f, keyframes { durationMillis = 700; 1f at 140; 0.1f at 210; 1f at 315; 0.35f at 385 })
            }
            launch { delay(400); title.animateTo(1f, tween(450, easing = EaseOutExpo)) }
        }
        delay(500)
        charge.animateTo(1f, tween(ARM_MS, easing = LinearEasing))
    }

    Surface(modifier, shape = RoundedCornerShape(28.dp), color = Color.Transparent, border = BorderStroke(1.dp, IronColors.I700)) {
        Column(
            Modifier
                .background(Brush.verticalGradient(listOf(Color(0xFF1C2025), Color(0xFF101215))))
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(124.dp), contentAlignment = Alignment.BottomCenter) {
                Canvas(Modifier.width(132.dp).height(18.dp)) {
                    drawOval(IronColors.Ember.copy(alpha = 0.22f * lit.value))
                }
                IronOwl(
                    Modifier
                        .padding(bottom = 4.dp)
                        .size(112.dp)
                        .graphicsLayer {
                            translationY = drop.value * 150.dp.toPx()
                            alpha = if (drop.value < -0.95f) 0f else 1f
                        }
                        .onGloballyPositioned { owlCenter = it.boundsInRoot().center }
                        .pointerInput(Unit) { detectTapGestures { poke++ } },
                    straining = arming,
                    shakeKey = poke,
                    fumeKey = fume,
                    lit = lit.value,
                )
            }
            Text(
                "Switch to Iron Hoot?",
                Modifier.padding(top = 10.dp).graphicsLayer {
                    alpha = title.value
                    translationY = (1f - title.value) * 10.dp.toPx()
                },
                fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
            )
            Forged(950, Modifier.padding(top = 6.dp)) { color, shadow ->
                Text(
                    buildAnnotatedString {
                        append("This is the ")
                        withStyle(SpanStyle(color = IronColors.EmberText, fontWeight = FontWeight.Bold)) { append("strict") }
                        append(" mode. He doesn't nudge. He locks.")
                    },
                    color = color,
                    style = MaterialTheme.typography.bodyLarge.copy(shadow = shadow),
                    textAlign = TextAlign.Center,
                )
            }
            Column(
                Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .border(1.dp, IronColors.I800, RoundedCornerShape(18.dp)),
            ) {
                rules(s).forEachIndexed { i, (icon, text) ->
                    if (i > 0) HorizontalDivider(color = IronColors.I800)
                    Forged(1350 + i * 320) { color, shadow ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(icon, null, tint = IronColors.I300, modifier = Modifier.size(20.dp))
                            Text(text, color = color, style = MaterialTheme.typography.bodyMedium.copy(shadow = shadow))
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) { Text("Stay with Hoot", color = IronColors.I200, fontWeight = FontWeight.SemiBold) }
                ArmButton(charge.value) {
                    if (arming) return@ArmButton
                    arming = true
                    fume++
                    scope.launch {
                        delay(if (motion) 520 else 0)
                        onConfirm(owlCenter)
                    }
                }
            }
        }
    }
}

private fun rules(s: AppSettings): List<Pair<ImageVector, AnnotatedString>> {
    fun bold(t: String) = SpanStyle(fontWeight = FontWeight.Bold).let { st -> buildAnnotatedString { withStyle(st) { append(t) } } }
    val limit = buildAnnotatedString {
        if (s.reelsLimitMin == s.shortsLimitMin) {
            append(bold("Reels and Shorts")); append(" lock after "); append(bold("${s.reelsLimitMin} min")); append(" a day")
        } else {
            append(bold("Reels")); append(" locks after "); append(bold("${s.reelsLimitMin} min")); append(", ")
            append(bold("Shorts")); append(" after "); append(bold("${s.shortsLimitMin} min"))
        }
    }
    val night = buildAnnotatedString {
        append("Both apps are closed from "); append(bold(Format.clock(s.closeHour)))
        append(" to "); append(bold(Format.clock(s.openHour)))
    }
    val pass = buildAnnotatedString {
        append("One "); append(bold("5-minute pass")); append(" a day, earned by holding him for 10 s")
    }
    val stuck = AnnotatedString("No switching back to Hoot while an app is over its limit or it's night")
    return listOf(
        Icons.Rounded.HourglassBottom to limit,
        Icons.Rounded.Bedtime to night,
        Icons.Rounded.Key to pass,
        Icons.Rounded.Lock to stuck,
    )
}

/** Stamps its content in left to right, white-hot, then lets it cool to the normal text colour. */
@Composable
private fun Forged(delayMs: Int, modifier: Modifier = Modifier, content: @Composable (Color, Shadow?) -> Unit) {
    val motion = rememberMotionEnabled()
    val stamp = remember { Animatable(if (motion) 0f else 1f) }
    val cool = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!motion) return@LaunchedEffect
        delay(delayMs.toLong())
        launch { stamp.animateTo(1f, tween(440, easing = EaseOutExpo)) }
        delay(200)
        cool.animateTo(1f, tween(1300, easing = LinearOutSlowInEasing))
    }
    val heat = 1f - cool.value
    val color = lerp(MaterialTheme.colorScheme.onSurface, IronColors.EmberHot, heat)
    val shadow = if (heat > 0.01f) Shadow(IronColors.Ember.copy(alpha = 0.9f * heat), blurRadius = 24f * heat) else null
    Box(
        modifier.drawWithContent {
            val pad = 24.dp.toPx()
            clipRect(left = -pad * stamp.value, top = -pad, right = size.width * stamp.value + pad * stamp.value, bottom = size.height + pad) {
                this@drawWithContent.drawContent()
            }
        },
    ) { content(color, shadow) }
}

@Composable
private fun ArmButton(charge: Float, onClick: () -> Unit) {
    val ready = charge >= 1f
    val bg by animateColorAsState(if (ready) IronColors.Ember else IronColors.I800, tween(250), label = "armBg")
    val fg by animateColorAsState(if (ready) Color(0xFF1D0700) else IronColors.I300, tween(250), label = "armFg")
    val clank = remember { Animatable(0f) }
    LaunchedEffect(ready) {
        if (ready) { clank.snapTo(-25f); clank.animateTo(0f, tween(400, easing = EaseOutExpo)) }
    }
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .clip(shape)
            .background(bg)
            .then(if (ready) Modifier else Modifier.border(1.dp, IronColors.I600, shape))
            .drawBehind {
                if (!ready) {
                    drawRect(
                        Brush.horizontalGradient(listOf(IronColors.Ember.copy(alpha = 0.18f), IronColors.Ember.copy(alpha = 0.42f))),
                        size = Size(size.width * charge, size.height),
                    )
                }
            }
            .clickable(enabled = ready, onClick = onClick)
            .semantics { if (!ready) stateDescription = "Charging" }
            .heightIn(min = 48.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.Shield, null, tint = fg, modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = clank.value })
        Text("Put on the armour", color = fg, fontWeight = FontWeight.Bold)
    }
}

/** "Back to Hoot?" Short and amber, whatever the current theme. */
@Composable
private fun HootConfirm(modifier: Modifier, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(28.dp), color = Color(0xFF1C1C1C)) {
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Owl(Mood.Happy, Modifier.size(88.dp))
            Spacer(Modifier.height(8.dp))
            Text("Back to Hoot?", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = HootScheme.onSurface)
            Text(
                "Your limits and the night lock stop. Hoot goes back to counting and nudging.",
                Modifier.padding(top = 6.dp),
                color = HootScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onCancel) { Text("Keep Iron Hoot", color = HootScheme.primary) }
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = HootScheme.primary, contentColor = HootScheme.onPrimary),
                ) { Text("Switch to Hoot", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** Iron Hoot's avatar for the top bar; flips in when Iron Hoot has just arrived. */
@Composable
fun ArrivingIronAvatar(size: Dp) {
    val morph = LocalIronMorph.current
    val seen = remember { morph.arrivals }
    val flip = remember { Animatable(0f) }
    LaunchedEffect(morph.arrivals) {
        if (morph.arrivals != seen) { flip.snapTo(1f); flip.animateTo(0f, tween(700, easing = EaseOutExpo)) }
    }
    IronAvatar(
        size,
        Modifier.graphicsLayer {
            rotationY = 180f * flip.value
            val sc = 1f - 0.4f * flip.value
            scaleX = sc
            scaleY = sc
            cameraDistance = 12f * density
        },
    )
}
