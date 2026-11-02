package com.scrollscope.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutExpo
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.scrollscope.app.ui.morph.LocalIronMorph
import com.scrollscope.app.ui.morph.LocalSnack
import com.scrollscope.app.ui.morph.OwlMode
import com.scrollscope.app.ui.theme.HootScheme
import com.scrollscope.app.ui.theme.IronColors
import com.scrollscope.app.util.Days
import com.scrollscope.app.util.Format
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/** Wall-clock time, refreshed every 15 s; also rolls Iron Hoot's numbers over at midnight. */
@Composable
private fun rememberNow() = produceState(System.currentTimeMillis()) {
    while (true) {
        ScrollScopeApp.instance.iron.current()
        value = System.currentTimeMillis()
        delay(15_000)
    }
}

private fun step(v: Int, by: Int, range: IntRange) = (v + by).coerceIn(range)
private val LimitRange = IronRules.LIMIT_MIN..IronRules.LIMIT_MAX

/**
 * Hoot or Iron Hoot, as two tiles. Picking either asks first (see IronMorphHost).
 * Iron Hoot's limits and hours only unfold once it's on. While Iron Hoot holds on
 * (night hours, or an app over its limit) there's no way back to Hoot, limits can
 * only go down and open hours can only get shorter.
 */
@Composable
fun OwlModeSection() {
    val app = ScrollScopeApp.instance
    val s by app.settingsState.collectAsStateWithLifecycle()
    val snap by app.iron.state.collectAsStateWithLifecycle()
    val now by rememberNow()
    val scope = rememberCoroutineScope()
    val morph = LocalIronMorph.current
    val snack = LocalSnack.current
    fun update(t: (AppSettings) -> AppSettings) = scope.launch { app.settings.update(t) }

    val minute = Days.minuteOfDay(now)
    val night = IronRules.isNight(minute, s.openHour, s.closeHour)
    val holds = IronRules.holdsOn(minute, snap, s)
    var hootCenter by remember { mutableStateOf(Offset.Zero) }
    var denied by remember { mutableIntStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeTile(
                Modifier.weight(1f).fillMaxHeight().onGloballyPositioned { hootCenter = it.boundsInRoot().center },
                title = "Hoot",
                line = "Counts and nudges. Never blocks.",
                selected = !s.ironEnabled,
                iron = false,
                locked = holds,
                denyKey = denied,
                onClick = {
                    when {
                        !s.ironEnabled -> Unit
                        holds -> {
                            denied++
                            val text = if (night) "Night lock until ${Format.clock(s.openHour)}. You can switch back after that."
                            else IronRules.APPS.filter { snap.watched(it) >= IronRules.limitMs(it, s) }
                                .joinToString(" and ") { IronRules.appLabel(it) } + " used up today's time. You can switch back tomorrow."
                            scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(text) }
                        }
                        else -> morph.ask(OwlMode.Hoot, hootCenter)
                    }
                },
            ) { Owl(Mood.Awake, Modifier.size(56.dp)) }
            ModeTile(
                Modifier.weight(1f).fillMaxHeight(),
                title = "Iron Hoot",
                line = "Locks the app when your time is up.",
                selected = s.ironEnabled,
                iron = true,
                onClick = { if (!s.ironEnabled) morph.ask(OwlMode.Iron) },
            ) { IronOwl(Modifier.size(56.dp), shakeKey = denied) }
        }
        AnimatedVisibility(
            s.ironEnabled,
            enter = expandVertically(tween(550, easing = EaseOutExpo), expandFrom = Alignment.Top) + fadeIn(tween(300)),
            exit = shrinkVertically(tween(350)) + fadeOut(tween(200)),
        ) {
            IronOptions(s, snap.passUsedToday, holds, night) { t -> update(t) }
        }
        AnimatedVisibility(!s.ironEnabled, enter = fadeIn(tween(300, delayMillis = 200)), exit = fadeOut(tween(150))) {
            Text(
                "Hoot never locks anything. Pick Iron Hoot to set hard limits and night hours.",
                Modifier.padding(horizontal = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ModeTile(
    modifier: Modifier,
    title: String,
    line: String,
    selected: Boolean,
    iron: Boolean,
    onClick: () -> Unit,
    locked: Boolean = false,
    denyKey: Int = 0,
    owl: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    val shake = remember { Animatable(1f) }
    LaunchedEffect(denyKey) {
        if (denyKey > 0) { shake.snapTo(0f); shake.animateTo(1f, tween(420, easing = LinearEasing)) }
    }
    val edge by animateColorAsState(
        when {
            selected && iron -> IronColors.I200
            selected -> HootScheme.primary
            iron -> IronColors.I700
            else -> Color.Transparent
        },
        tween(300), label = "tileEdge",
    )
    val fill = when {
        iron -> Brush.verticalGradient(listOf(Color(0xFF1D2126), Color(0xFF111316)))
        selected -> SolidColor(Color(0xFF24180F))
        else -> SolidColor(cs.surfaceContainer)
    }
    Box(
        modifier
            .graphicsLayer {
                val p = shake.value
                translationX = if (p >= 1f) 0f else sin(3f * PI.toFloat() * p) * (1f - p) * 8.dp.toPx()
            }
            .clip(shape)
            .background(fill)
            .border(if (selected) 2.dp else 1.dp, edge, shape)
            .selectable(selected, onClick = onClick, role = Role.RadioButton)
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            owl()
            Text(title, Modifier.padding(top = 4.dp), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(line, color = if (iron) IronColors.I300 else cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        if (locked) {
            Icon(Icons.Rounded.Lock, "Locked", tint = IronColors.I400, modifier = Modifier.align(Alignment.TopEnd).size(20.dp))
        } else {
            AnimatedVisibility(
                selected,
                Modifier.align(Alignment.TopEnd),
                enter = scaleIn(spring(dampingRatio = 0.5f)) + fadeIn(),
                exit = fadeOut(tween(150)),
            ) { Icon(Icons.Rounded.CheckCircle, null, tint = edge, modifier = Modifier.size(22.dp)) }
        }
    }
}

/** The limit rows; each one slides in a beat after the last when Iron Hoot arrives. */
@Composable
private fun AnimatedVisibilityScope.IronOptions(
    s: AppSettings,
    passUsed: Boolean,
    locked: Boolean,
    night: Boolean,
    update: ((AppSettings) -> AppSettings) -> Unit,
) {
    fun Modifier.stagger(i: Int) = animateEnterExit(
        enter = fadeIn(tween(450, delayMillis = 120 + i * 70)) +
            slideInVertically(tween(450, delayMillis = 120 + i * 70, easing = EaseOutExpo)) { -it / 2 },
        exit = ExitTransition.None,
    )
    Surface(shape = RoundedCornerShape(24.dp), color = IronColors.I900, border = BorderStroke(1.dp, IronColors.I700)) {
        Column {
            StepRow(
                Icons.Rounded.Movie, "Reels", "${s.reelsLimitMin} min",
                canDown = s.reelsLimitMin > IronRules.LIMIT_MIN,
                canUp = !locked && s.reelsLimitMin < IronRules.LIMIT_MAX,
                onDown = { update { it.copy(reelsLimitMin = step(it.reelsLimitMin, -IronRules.LIMIT_STEP, LimitRange)) } },
                onUp = { update { it.copy(reelsLimitMin = step(it.reelsLimitMin, IronRules.LIMIT_STEP, LimitRange)) } },
                modifier = Modifier.stagger(0),
            )
            Line()
            StepRow(
                Icons.Rounded.SmartDisplay, "Shorts", "${s.shortsLimitMin} min",
                canDown = s.shortsLimitMin > IronRules.LIMIT_MIN,
                canUp = !locked && s.shortsLimitMin < IronRules.LIMIT_MAX,
                onDown = { update { it.copy(shortsLimitMin = step(it.shortsLimitMin, -IronRules.LIMIT_STEP, LimitRange)) } },
                onUp = { update { it.copy(shortsLimitMin = step(it.shortsLimitMin, IronRules.LIMIT_STEP, LimitRange)) } },
                modifier = Modifier.stagger(1),
            )
            Line()
            // Opening earlier or locking later widens the window, so both are blocked while locked.
            StepRow(
                Icons.Rounded.WbSunny, "Opens at", Format.clock(s.openHour),
                canDown = !locked && s.openHour > IronRules.OPEN_RANGE.first,
                canUp = s.openHour < IronRules.OPEN_RANGE.last,
                onDown = { update { it.copy(openHour = step(it.openHour, -1, IronRules.OPEN_RANGE)) } },
                onUp = { update { it.copy(openHour = step(it.openHour, 1, IronRules.OPEN_RANGE)) } },
                modifier = Modifier.stagger(2),
            )
            Line()
            StepRow(
                Icons.Rounded.Bedtime, "Locks at", Format.clock(s.closeHour),
                canDown = s.closeHour > IronRules.CLOSE_RANGE.first,
                canUp = !locked && s.closeHour < IronRules.CLOSE_RANGE.last,
                onDown = { update { it.copy(closeHour = step(it.closeHour, -1, IronRules.CLOSE_RANGE)) } },
                onUp = { update { it.copy(closeHour = step(it.closeHour, 1, IronRules.CLOSE_RANGE)) } },
                modifier = Modifier.stagger(3),
            )
            Line()
            Row(
                Modifier.fillMaxWidth().stagger(4).padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(Icons.Rounded.Key, null, tint = IronColors.I300)
                Text("Emergency pass", Modifier.weight(1f))
                Text(
                    if (passUsed) "Used today" else "1 a day · 5 min",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (locked) {
                Line()
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Rounded.Lock, null, tint = IronColors.EmberText, modifier = Modifier.size(18.dp))
                    Text(
                        if (night) "Night lock until ${Format.clock(s.openHour)}. Until then you can't switch back to Hoot, raise a limit or open earlier."
                        else "An app has used up today's time. Until tomorrow you can't switch back to Hoot, raise a limit or widen the open hours.",
                        color = IronColors.I300, style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun Line() = HorizontalDivider(color = IronColors.I800)

@Composable
private fun StepRow(
    icon: ImageVector,
    label: String,
    value: String,
    canDown: Boolean,
    canUp: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = IronColors.I300)
        Text(label, Modifier.weight(1f))
        Row(
            Modifier.clip(RoundedCornerShape(20.dp)).background(IronColors.I800),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDown, enabled = canDown) {
                Text("−", fontSize = 20.sp, color = if (canDown) IronColors.I200 else IronColors.I600)
            }
            Text(value, Modifier.widthIn(min = 64.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onUp, enabled = canUp) {
                Text("+", fontSize = 20.sp, color = if (canUp) IronColors.I200 else IronColors.I600)
            }
        }
    }
}

/** Today's Reels/Shorts minutes against each app's Iron Hoot limit. */
@Composable
fun IronBudgetCard() {
    val app = ScrollScopeApp.instance
    val s by app.settingsState.collectAsStateWithLifecycle()
    val snap by app.iron.state.collectAsStateWithLifecycle()
    rememberNow()
    if (!s.ironEnabled) return
    val cs = MaterialTheme.colorScheme

    Surface(shape = RoundedCornerShape(24.dp), color = IronColors.I900, border = BorderStroke(1.dp, IronColors.I700)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IronAvatar(32.dp)
                Text("Iron Hoot", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(
                    "${Format.hour12(s.openHour)} – ${Format.hour12(s.closeHour % 24)}",
                    color = IronColors.I300, style = MaterialTheme.typography.bodySmall,
                )
            }
            BudgetLine(Apps.INSTAGRAM, Icons.Rounded.Movie, cs.primary, snap.watched(Apps.INSTAGRAM), IronRules.limitMs(Apps.INSTAGRAM, s))
            BudgetLine(Apps.YOUTUBE, Icons.Rounded.SmartDisplay, cs.tertiary, snap.watched(Apps.YOUTUBE), IronRules.limitMs(Apps.YOUTUBE, s))
        }
    }
}

@Composable
private fun BudgetLine(pkg: String, icon: ImageVector, tint: Color, watchedMs: Long, limitMs: Long) {
    val frac = if (limitMs > 0) (watchedMs.toFloat() / limitMs).coerceIn(0f, 1f) else 0f
    val shown by animateFloatAsState(frac, tween(600), label = "budget")
    val leftMin = ((limitMs - watchedMs + 59_999) / 60_000).coerceAtLeast(0)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Text(IronRules.feedLabel(pkg), Modifier.weight(1f))
            Text(
                "${watchedMs / 60_000} / ${limitMs / 60_000} min",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
            )
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(IronColors.I800)) {
            Box(
                Modifier.fillMaxWidth(shown).fillMaxHeight().clip(CircleShape).background(
                    if (frac >= 0.8f) Brush.horizontalGradient(listOf(IronColors.I300, IronColors.Ember))
                    else Brush.horizontalGradient(listOf(IronColors.I300, IronColors.I300)),
                ),
            )
        }
        when {
            frac >= 1f -> Text("${IronRules.appLabel(pkg)} is locked until tomorrow", color = IronColors.EmberText, style = MaterialTheme.typography.bodySmall)
            limitMs - watchedMs <= IronRules.IRON_BUBBLE_MS ->
                Text("${IronRules.appLabel(pkg)} locks in $leftMin min", color = IronColors.EmberText, style = MaterialTheme.typography.bodySmall)
        }
    }
}
