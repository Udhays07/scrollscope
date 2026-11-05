package com.scrollscope.app.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import com.scrollscope.app.iron.TimerStage
import com.scrollscope.app.util.rememberMotionEnabled
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.LocalTextStyle
import kotlin.math.min
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollscope.app.ui.components.Mood
import com.scrollscope.app.ui.components.IronOwl
import com.scrollscope.app.ui.components.IronPose
import com.scrollscope.app.ui.components.Owl
import com.scrollscope.app.ui.theme.IronColors
import com.scrollscope.app.ui.theme.HootColors
import kotlinx.coroutines.flow.StateFlow

/** Badge (and ring arc) colors per Iron timer stage: fill to text. */
private fun stageColors(stage: TimerStage): Pair<Color, Color> = when (stage) {
    TimerStage.Calm -> IronColors.I300 to IronColors.I950
    TimerStage.Low -> HootColors.Amber to HootColors.OnAmber
    TimerStage.Critical -> IronColors.Ember to IronColors.I950
    TimerStage.Pass -> IronColors.EmberHot to IronColors.I950
}

private fun stagePose(stage: TimerStage): IronPose = when (stage) {
    TimerStage.Calm -> IronPose.Watch
    TimerStage.Low, TimerStage.Critical -> IronPose.Guard
    TimerStage.Pass -> IronPose.Granted
}

@Composable
fun BubbleContent(
    state: StateFlow<BubbleState>,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onTap: () -> Unit,
) {
    val s by state.collectAsStateWithLifecycle()
    val motion = rememberMotionEnabled()
    val pct = if (s.limit > 0) s.count.toFloat() / s.limit else 0f
    val cs = MaterialTheme.colorScheme
    val iron = s.iron
    val (ring, onRing) = when {
        iron != null -> stageColors(iron.stage)
        pct >= 1f -> cs.error to cs.onError
        pct >= 0.8f -> HootColors.Amber to HootColors.OnAmber
        else -> cs.primary to cs.onPrimary
    }
    val mood = if (pct >= 0.8f) Mood.Wide else Mood.Awake

    // Badge pops and a "+1" floats up whenever the count goes up (not in Iron mode).
    val pop = remember { Animatable(1f) }
    val plus = remember { Animatable(1f) }
    var lastCount by remember { mutableIntStateOf(s.count) }
    LaunchedEffect(s.count) {
        if (s.count > lastCount && motion && s.iron == null) {
            launch { pop.snapTo(1.35f); pop.animateTo(1f, spring(dampingRatio = 0.4f)) }
            launch { plus.snapTo(0f); plus.animateTo(1f, tween(1400, easing = LinearEasing)) }
        }
        lastCount = s.count
    }

    // Iron: one steam puff on a transition into Critical (not when the bubble appears already Critical).
    val stage = iron?.stage
    var fumeKey by remember { mutableIntStateOf(0) }
    var prevStage by remember { mutableStateOf(stage) }
    LaunchedEffect(stage) {
        if (stage == null) fumeKey = 0
        else if (stage == TimerStage.Critical && prevStage != null && prevStage != TimerStage.Critical) fumeKey++
        prevStage = stage
    }

    // Ring glows near the limit and pulses once over it. In Iron mode it pulses only when Critical.
    val hot = if (iron != null) iron.stage == TimerStage.Critical else pct >= 1f
    val showPulse = motion && (if (iron != null) iron.stage == TimerStage.Critical else pct >= 0.8f)
    val pulseT = rememberInfiniteTransition(label = "pulse")
    val pulse by pulseT.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(if (hot) 1600 else 2400, easing = LinearEasing)),
        label = "pulse",
    )

    val fontScale = LocalDensity.current.fontScale
    val badgeSize = if (iron != null) (13f * min(fontScale, 1.3f) / fontScale).sp else 13.sp
    // Iron mode is 8 dp taller and its badge uses a tight line height, so the badge sits below
    // the ring (overlapping at most ~1 dp at the 1.3x font cap) and never hides the ring's end.
    // Outside Iron mode the bubble keeps its original 80x112 dp size (Decision 6).
    val innerH = if (iron != null) 96.dp else 88.dp

    Box(
        Modifier
            .widthIn(min = 80.dp)
            .height(innerH + 24.dp)
            .clearAndSetSemantics {
                contentDescription = iron?.spoken ?: if (s.count == 1) "1 reel today" else "${s.count} reels today"
                onClick(label = "Open Hoot menu") { onTap(); true }
            }
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = onDragEnd) { change, drag ->
                    change.consume()
                    onDrag(drag.x, drag.y)
                }
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }, onLongPress = { onTap() }) },
    ) {
        if (iron == null && plus.value < 1f) {
            val q = plus.value
            val alpha = if (q < 0.15f) q / 0.15f else if (q < 0.7f) 1f else 1f - (q - 0.7f) / 0.3f
            Text(
                "+1",
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (8 - 10 * q).dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(cs.surfaceContainerHigh.copy(alpha = alpha))
                    .padding(horizontal = 8.dp, vertical = 1.dp),
                color = cs.primary.copy(alpha = alpha),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(Modifier.align(Alignment.BottomCenter).widthIn(min = 80.dp).height(innerH)) {
            if (showPulse) {
                Canvas(Modifier.align(Alignment.TopCenter).size(80.dp)) {
                    val base = 32.dp.toPx()
                    val r = base + pulse * (if (hot) 8.dp.toPx() else 5.dp.toPx())
                    drawCircle(ring.copy(alpha = (1f - pulse) * 0.55f), r, center, style = Stroke(3.dp.toPx()))
                }
            }
            if (iron != null) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(IronColors.I900),
                    contentAlignment = Alignment.Center,
                ) {
                    IronOwl(Modifier.size(48.dp), pose = stagePose(iron.stage), fumeKey = fumeKey)
                }
                TimerRing(
                    fraction = iron.fractionLeft,
                    color = ring,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(64.dp),
                )
            } else {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(HootColors.OwlBackdrop)
                        .border(3.dp, ring, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Owl(mood, Modifier.size(46.dp))
                }
            }
            if (iron != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(min = 56.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(ring)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        iron.text,
                        color = onRing,
                        fontSize = badgeSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        // Tabular digits so the countdown doesn't jiggle; a tight line height
                        // (16/13 of the font, capped with it) keeps the badge below the ring.
                        style = LocalTextStyle.current.copy(
                            fontFeatureSettings = "tnum",
                            lineHeight = (badgeSize.value * 16f / 13f).sp,
                        ),
                    )
                }
            } else {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .scale(pop.value)
                        .clip(RoundedCornerShape(11.dp))
                        .background(ring)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text("${s.count}", color = onRing, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun MenuContent(
    anchorXpx: Int,
    anchorYpx: Int,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
    onOpenApp: () -> Unit,
    onHideToday: () -> Unit,
) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val menuW = 232.dp
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        val screenWpx = with(density) { config.screenWidthDp.dp.roundToPx() }
        val menuWpx = with(density) { menuW.roundToPx() }
        val x = remember(anchorXpx) { (anchorXpx + with(density) { 80.dp.roundToPx() } - menuWpx).coerceIn(0, (screenWpx - menuWpx).coerceAtLeast(0)) }
        Surface(
            modifier = Modifier.offset { IntOffset(x, anchorYpx) }.width(menuW),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 6.dp,
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                MenuRow(Icons.Rounded.Snooze, "Snooze 10 min", onSnooze)
                MenuRow(Icons.Rounded.Apps, "Open ScrollScope", onOpenApp)
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                MenuRow(Icons.Rounded.VisibilityOff, "Hide for today", onHideToday)
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
fun PauseContent(count: Int, onKeepWatching: () -> Unit, onTakeBreak: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        val shown = remember { MutableTransitionState(false).apply { targetState = true } }
        AnimatedVisibility(
            visibleState = shown,
            enter = fadeIn(tween(200)) + scaleIn(tween(320, easing = FastOutSlowInEasing), initialScale = 0.9f),
        ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(112.dp).clip(CircleShape).background(HootColors.OwlBackdrop),
                    contentAlignment = Alignment.Center,
                ) { Owl(Mood.Sleepy, Modifier.size(90.dp)) }
                Text(
                    "$count in a row",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text("Stretch break?", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                // Stacked full-width buttons: never wrap or get clipped, even with large font sizes.
                Button(
                    onClick = onTakeBreak,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text("Take a break", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                TextButton(
                    onClick = onKeepWatching,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("5 more reels", maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        }
    }
}
