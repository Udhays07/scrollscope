@file:OptIn(ExperimentalMaterial3Api::class)

package com.scrollscope.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollscope.app.data.Stats
import com.scrollscope.app.data.TrendsUi
import com.scrollscope.app.ui.components.HootCard
import com.scrollscope.app.ui.components.HootTopBar
import com.scrollscope.app.ui.components.StatTile
import com.scrollscope.app.ui.theme.HootColors
import com.scrollscope.app.util.Format
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun TrendsScreen() {
    var range by rememberSaveable { mutableIntStateOf(7) }
    val ui by remember(range) { Stats.trends(range) }.collectAsStateWithLifecycle(TrendsUi())
    val cs = MaterialTheme.colorScheme

    Column(Modifier.verticalScroll(rememberScrollState())) {
        HootTopBar("Trends")
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(7 to "Week", 30 to "Month").forEachIndexed { i, (days, label) ->
                    SegmentedButton(
                        selected = range == days,
                        onClick = { range = days },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                    ) { Text(label) }
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("%,d".format(ui.total), fontSize = 45.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (ui.prevTotal > 0) {
                    val pct = (ui.total - ui.prevTotal) * 100 / ui.prevTotal
                    val up = pct > 0
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (up) HootColors.BadContainer else HootColors.GoodContainer)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val tint = if (up) cs.error else HootColors.Good
                        Icon(if (up) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward, null, tint = tint, modifier = Modifier.size(16.dp))
                        Text("${kotlin.math.abs(pct)}%", color = tint, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            HootCard(radius = 28.dp) { BarChart(ui, range) }

            val days = ui.days
            val hi = days.maxByOrNull { it.count }
            val lo = days.minByOrNull { it.count }
            val over = days.count { it.count > ui.limit }
            fun dayName(d: java.time.LocalDate) =
                if (range == 7) d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) else "${d.dayOfMonth}/${d.monthValue}"
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(Icons.Rounded.ArrowUpward, hi?.let { "${it.count} · ${dayName(it.date)}" } ?: "–", "peak", cs.primary, Modifier.weight(1f))
                StatTile(Icons.Rounded.ArrowDownward, lo?.let { "${it.count} · ${dayName(it.date)}" } ?: "–", "lowest", HootColors.Good, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(Icons.Rounded.Warning, if (over == 1) "1 day" else "$over days", "over limit", cs.error, Modifier.weight(1f))
                StatTile(Icons.Rounded.Timer, Format.duration(ui.watchMs), "watched", cs.tertiary, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BarChart(ui: TrendsUi, range: Int) {
    val cs = MaterialTheme.colorScheme
    val values = ui.days.map { it.count }
    val avg = if (values.isNotEmpty()) values.sum().toFloat() / values.size else 0f
    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
        if (values.isEmpty()) return@Canvas
        val top = (maxOf(values.maxOrNull() ?: 0, ui.limit) * 1.05f).coerceAtLeast(1f)
        val gap = (if (range == 7) 10.dp else 3.dp).toPx()
        val w = (size.width - gap * (values.size - 1)) / values.size
        val r = if (range == 7) 12.dp.toPx() else 3.dp.toPx()
        values.forEachIndexed { i, v ->
            val h = (v / top * size.height).coerceAtLeast(4.dp.toPx())
            val color = when {
                i == values.lastIndex -> cs.primary
                v > ui.limit -> cs.error
                else -> HootColors.BarMuted
            }
            drawRoundRect(color, Offset(i * (w + gap), size.height - h), Size(w, h), CornerRadius(r))
        }
        val y = size.height - avg / top * size.height
        drawLine(
            cs.outline, Offset(0f, y), Offset(size.width, y),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
        )
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        ui.days.forEachIndexed { i, d ->
            val label = when {
                range == 7 -> d.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
                i % 7 == 6 -> "${d.date.dayOfMonth}"
                else -> ""
            }
            Text(label, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
}
