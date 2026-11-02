@file:OptIn(ExperimentalMaterial3Api::class)

package com.scrollscope.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollscope.app.data.DayValue
import com.scrollscope.app.data.Stats
import com.scrollscope.app.data.TrendsUi
import com.scrollscope.app.ui.components.HootCard
import com.scrollscope.app.ui.components.HootTopBar
import com.scrollscope.app.ui.components.Mood
import com.scrollscope.app.ui.components.OwlAvatar
import com.scrollscope.app.ui.theme.HootColors
import com.scrollscope.app.util.Format
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val DayFmt = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())

@Composable
fun TrendsScreen() {
    var range by rememberSaveable { mutableIntStateOf(7) }
    // -1 means "today" (the last day in range)
    var sel7 by rememberSaveable { mutableIntStateOf(-1) }
    var sel30 by rememberSaveable { mutableIntStateOf(-1) }
    val ui by remember(range) { Stats.trends(range) }.collectAsStateWithLifecycle(TrendsUi())
    val cs = MaterialTheme.colorScheme

    val days = ui.days
    val rawSel = if (range == 7) sel7 else sel30
    val selIdx = if (rawSel in days.indices) rawSel else days.lastIndex
    val avg = if (days.isNotEmpty()) ui.total / days.size else 0

    Column(Modifier.verticalScroll(rememberScrollState())) {
        HootTopBar("Trends")
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(7 to "Week", 30 to "Month").forEachIndexed { i, (n, label) ->
                    SegmentedButton(
                        selected = range == n,
                        onClick = { range = n },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                    ) { Text(label) }
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("%,d".format(ui.total), fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
                Text(" reels", color = cs.onSurfaceVariant, modifier = Modifier.weight(1f).padding(top = 8.dp))
                if (ui.prevTotal > 0) {
                    val pct = (ui.total - ui.prevTotal) * 100 / ui.prevTotal
                    val up = pct > 0
                    val tint = if (up) cs.error else HootColors.Good
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (up) HootColors.BadContainer else HootColors.GoodContainer)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (up) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward, null, tint = tint, modifier = Modifier.size(14.dp))
                        Text("${kotlin.math.abs(pct)}%", color = tint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            days.getOrNull(selIdx)?.let { DayDetail(it, avg, ui.limit, isToday = selIdx == days.lastIndex) }

            HootCard(radius = 24.dp) {
                if (range == 7) {
                    WeekBars(days, selIdx, ui.limit) { sel7 = it }
                } else {
                    MonthCalendar(days, selIdx, ui.limit) { sel30 = it }
                }
            }

            val over = days.count { it.count > ui.limit }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallTile("$avg/day", "average", cs.onSurface, Modifier.weight(1f))
                SmallTile(if (over == 1) "1 day" else "$over days", "over limit", cs.error, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DayDetail(d: DayValue, avg: Int, limit: Int, isToday: Boolean) {
    val cs = MaterialTheme.colorScheme
    val over = d.count > limit
    val mood = when {
        over -> Mood.Wide
        d.count < avg -> Mood.Happy
        else -> Mood.Awake
    }
    val pct = if (avg > 0) (d.count - avg) * 100 / avg else 0
    val (tag, tagBg, tagFg) = when {
        over -> Triple("Over limit", HootColors.BadContainer, cs.error)
        pct > 0 -> Triple("+$pct% vs avg", cs.secondaryContainer, cs.onSecondaryContainer)
        else -> Triple("$pct% vs avg", HootColors.GoodContainer, HootColors.Good)
    }

    HootCard(radius = 24.dp, modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OwlAvatar(mood, 52.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    (if (isToday) "Today · " else "") + d.date.format(DayFmt),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${d.count}", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = if (over) cs.error else cs.onSurface)
                    Text(" reels", fontSize = 15.sp, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
            Text(
                tag,
                Modifier.clip(RoundedCornerShape(6.dp)).background(tagBg).padding(horizontal = 8.dp, vertical = 4.dp),
                color = tagFg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            )
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DetailChip(Icons.Rounded.Timer, Format.duration(d.dwellMs))
            DetailChip(Icons.Rounded.Layers, if (d.sessions == 1) "1 session" else "${d.sessions} sessions")
            DetailChip(Icons.Rounded.Bedtime, d.peakHour?.let { Format.hour12(it) } ?: "–")
        }
    }
}

@Composable
private fun DetailChip(icon: ImageVector, text: String) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun WeekBars(days: List<DayValue>, selIdx: Int, limit: Int, onPick: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
    val top = (maxOf(days.maxOfOrNull { it.count } ?: 0, limit) * 1.05f).coerceAtLeast(1f)
    val barArea = 170.dp
    val limitColor = cs.error.copy(alpha = 0.6f)

    Box(Modifier.fillMaxWidth().height(196.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height - (limit / top) * barArea.toPx()
            drawLine(limitColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            days.forEachIndexed { i, d ->
                val on = i == selIdx
                val over = d.count > limit
                val color by animateColorAsState(
                    when {
                        on && over -> cs.error
                        on -> cs.primary
                        over -> HootColors.BarOver
                        else -> HootColors.BarMuted
                    },
                    label = "bar",
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button) { onPick(i) }
                        .semantics {
                            selected = on
                            contentDescription = "${d.date.format(DayFmt)}, ${d.count} reels"
                        },
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (on) Text("${d.count}", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    val h = barArea * ((d.count / top) * grow.value)
                    Box(
                        Modifier.fillMaxWidth().height(maxOf(h, 4.dp))
                            .clip(RoundedCornerShape(10.dp)).background(color)
                    )
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        days.forEachIndexed { i, d ->
            val on = i == selIdx
            Text(
                d.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                color = if (on) cs.primary else cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MonthCalendar(days: List<DayValue>, selIdx: Int, limit: Int, onPick: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    if (days.isEmpty()) return
    val lead = days.first().date.dayOfWeek.value - 1 // Monday-first grid
    val cells: List<Int?> = List(lead) { null } + days.indices.toList()

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEach {
            Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = cs.onSurfaceVariant)
        }
    }
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (k in 0 until 7) {
                    val idx = week.getOrNull(k)
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (idx != null) CalendarCell(days[idx], idx == selIdx, limit) { onPick(idx) }
                    }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(cs.error))
        Text("  over limit", fontSize = 11.sp, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text("less ", fontSize = 11.sp, color = cs.onSurfaceVariant)
        HootColors.Heat.forEach { Box(Modifier.padding(horizontal = 2.dp).size(12.dp).clip(RoundedCornerShape(3.dp)).background(it)) }
        Text(" more", fontSize = 11.sp, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun CalendarCell(d: DayValue, on: Boolean, limit: Int, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val r = if (limit > 0) d.count.toFloat() / limit else 0f
    val level = when {
        r < 0.5f -> 0
        r < 0.65f -> 1
        r < 0.85f -> 2
        else -> 3
    }
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .fillMaxSize()
            .then(if (on) Modifier.border(2.dp, cs.primary, shape) else Modifier)
            .padding(if (on) 3.dp else 0.dp)
            .clip(shape)
            .background(HootColors.Heat[level])
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                selected = on
                contentDescription = "${d.date.format(DayFmt)}, ${d.count} reels"
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${d.date.dayOfMonth}",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (level == 3) Color(0xFF1A0E05) else cs.onSurface,
        )
        if (d.count > limit) {
            Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(6.dp).clip(CircleShape).background(cs.error))
        }
    }
}

@Composable
private fun SmallTile(value: String, label: String, valueColor: Color, modifier: Modifier) {
    HootCard(modifier, radius = 20.dp) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
