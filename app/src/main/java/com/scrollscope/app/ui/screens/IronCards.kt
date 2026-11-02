package com.scrollscope.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollscope.app.ScrollScopeApp
import com.scrollscope.app.data.AppSettings
import com.scrollscope.app.data.Apps
import com.scrollscope.app.iron.IronRules
import com.scrollscope.app.iron.LockReason
import com.scrollscope.app.ui.components.IronAvatar
import com.scrollscope.app.ui.components.IronPose
import com.scrollscope.app.ui.theme.IronColors
import com.scrollscope.app.util.Days
import com.scrollscope.app.util.Format
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Wall-clock time, refreshed every 15 s; also rolls Iron Owl's numbers over at midnight. */
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
 * Iron Owl's settings. While any app is locked there's no back door: the switch
 * locks, limits can only go down, and open hours can only get shorter.
 */
@Composable
fun IronSettingsCard() {
    val app = ScrollScopeApp.instance
    val s by app.settingsState.collectAsStateWithLifecycle()
    val snap by app.iron.state.collectAsStateWithLifecycle()
    val now by rememberNow()
    val scope = rememberCoroutineScope()
    fun update(t: (AppSettings) -> AppSettings) = scope.launch { app.settings.update(t) }

    val minute = Days.minuteOfDay(now)
    val reasons = IronRules.APPS.associateWith {
        IronRules.lockReason(it, minute, snap.watched(it), snap.passActive(now), s)
    }
    val locked = reasons.values.any { it != null }
    val subtitle = when {
        !s.ironEnabled -> "Off. Hoot stays soft."
        reasons.values.any { it == LockReason.Night } -> "Night lock until ${Format.clock(s.openHour)}"
        locked -> reasons.filterValues { it != null }.keys.joinToString(" and ") { IronRules.appLabel(it) } +
            " locked till ${Format.clock(s.openHour)}"
        else -> "Locks an app for the day once its time is up"
    }

    Surface(shape = RoundedCornerShape(24.dp), color = IronColors.I900, border = BorderStroke(1.dp, IronColors.I700)) {
        Column {
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                IronAvatar(48.dp, pose = if (s.ironEnabled) IronPose.Guard else IronPose.Night)
                Column(Modifier.weight(1f)) {
                    Text("Iron Owl", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = IronColors.I300, style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = s.ironEnabled,
                    enabled = !(s.ironEnabled && locked),
                    onCheckedChange = { v -> update { it.copy(ironEnabled = v) } },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = IronColors.I200,
                        checkedThumbColor = IronColors.I900,
                        disabledCheckedTrackColor = IronColors.I500,
                        disabledCheckedThumbColor = IronColors.I900,
                    ),
                )
            }
            if (!s.ironEnabled) return@Column

            Line()
            StepRow(
                Icons.Rounded.Movie, "Reels", "${s.reelsLimitMin} min",
                canDown = s.reelsLimitMin > IronRules.LIMIT_MIN,
                canUp = !locked && s.reelsLimitMin < IronRules.LIMIT_MAX,
                onDown = { update { it.copy(reelsLimitMin = step(it.reelsLimitMin, -IronRules.LIMIT_STEP, LimitRange)) } },
                onUp = { update { it.copy(reelsLimitMin = step(it.reelsLimitMin, IronRules.LIMIT_STEP, LimitRange)) } },
            )
            Line()
            StepRow(
                Icons.Rounded.SmartDisplay, "Shorts", "${s.shortsLimitMin} min",
                canDown = s.shortsLimitMin > IronRules.LIMIT_MIN,
                canUp = !locked && s.shortsLimitMin < IronRules.LIMIT_MAX,
                onDown = { update { it.copy(shortsLimitMin = step(it.shortsLimitMin, -IronRules.LIMIT_STEP, LimitRange)) } },
                onUp = { update { it.copy(shortsLimitMin = step(it.shortsLimitMin, IronRules.LIMIT_STEP, LimitRange)) } },
            )
            Line()
            // Opening earlier or locking later widens the window, so both are blocked while locked.
            StepRow(
                Icons.Rounded.WbSunny, "Opens at", Format.clock(s.openHour),
                canDown = !locked && s.openHour > IronRules.OPEN_RANGE.first,
                canUp = s.openHour < IronRules.OPEN_RANGE.last,
                onDown = { update { it.copy(openHour = step(it.openHour, -1, IronRules.OPEN_RANGE)) } },
                onUp = { update { it.copy(openHour = step(it.openHour, 1, IronRules.OPEN_RANGE)) } },
            )
            Line()
            StepRow(
                Icons.Rounded.Bedtime, "Locks at", Format.clock(s.closeHour),
                canDown = s.closeHour > IronRules.CLOSE_RANGE.first,
                canUp = !locked && s.closeHour < IronRules.CLOSE_RANGE.last,
                onDown = { update { it.copy(closeHour = step(it.closeHour, -1, IronRules.CLOSE_RANGE)) } },
                onUp = { update { it.copy(closeHour = step(it.closeHour, 1, IronRules.CLOSE_RANGE)) } },
            )
            Line()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(Icons.Rounded.Key, null, tint = IronColors.I300)
                Text("Emergency pass", Modifier.weight(1f))
                Text(
                    if (snap.passUsedToday) "Used today" else "1 a day · 5 min",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (locked) {
                Line()
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Rounded.Lock, null, tint = IronColors.I400, modifier = Modifier.size(18.dp))
                    Text(
                        "You can't turn Iron Owl off, raise a limit or widen the open hours while an app is locked.",
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
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
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

/** Today's Reels/Shorts minutes against each app's Iron Owl limit. */
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
                Text("Iron Owl", Modifier.weight(1f), fontWeight = FontWeight.Bold)
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
