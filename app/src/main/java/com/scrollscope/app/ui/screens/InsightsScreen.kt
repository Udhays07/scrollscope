package com.scrollscope.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollscope.app.data.InsightsUi
import com.scrollscope.app.data.Stats
import com.scrollscope.app.ui.components.HootCard
import com.scrollscope.app.ui.components.HootTopBar
import com.scrollscope.app.ui.components.Mood
import com.scrollscope.app.ui.components.OwlAvatar
import com.scrollscope.app.ui.theme.HootColors
import com.scrollscope.app.util.Format

private val HeatLevels = listOf(
    Color(0xFF1C1C1C), Color(0xFF3A2414), Color(0xFF7A4524), Color(0xFFB8642E), Color(0xFFFFB68A),
)

@Composable
fun InsightsScreen(onOpenSettings: () -> Unit) {
    val ui by remember { Stats.insights() }.collectAsStateWithLifecycle(InsightsUi())
    val cs = MaterialTheme.colorScheme

    Column(Modifier.verticalScroll(rememberScrollState())) {
        HootTopBar("Insights")
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HootCard(radius = 28.dp) { Heatmap(ui) }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigTile(Icons.Rounded.Bedtime, ui.peakHour?.let { Format.hour12(it) } ?: "–", "peak hour", cs.primary, Modifier.weight(1f))
                BigTile(Icons.Rounded.Bolt, ui.skipPercent?.let { "$it%" } ?: "–", "quick skips", cs.tertiary, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigTile(Icons.Rounded.Warning, "${ui.longest}", "longest binge", cs.error, Modifier.weight(1f))
                BigTile(Icons.Rounded.Timer, ui.avgSecondsPerReel?.let { "${it}s" } ?: "–", "per reel", HootColors.Good, Modifier.weight(1f))
            }

            if (ui.total > 0 && ui.latePercent >= 30) {
                HootCard(radius = 28.dp, color = cs.secondaryContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OwlAvatar(Mood.Sleepy, 56.dp)
                        Text(
                            "${ui.latePercent}% after 10 PM",
                            Modifier.weight(1f),
                            color = cs.onSecondaryContainer,
                            fontWeight = FontWeight.Medium,
                        )
                        Button(onClick = onOpenSettings) { Text("Fix") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Heatmap(ui: InsightsUi) {
    val cs = MaterialTheme.colorScheme
    val max = (ui.grid.maxOfOrNull { row -> row.maxOrNull() ?: 0 } ?: 0).coerceAtLeast(1)
    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            dayLabels.forEach {
                Text(it, Modifier.height(18.dp).width(12.dp), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
        Canvas(Modifier.weight(1f).height((18 * 7 + 4 * 6).dp)) {
            val gap = 2.dp.toPx()
            val rowGap = 4.dp.toPx()
            val cw = (size.width - gap * 23) / 24
            val ch = 18.dp.toPx()
            ui.grid.forEachIndexed { r, row ->
                row.forEachIndexed { h, v ->
                    val level = if (v == 0) 0 else (1 + (v * 3.999f / max).toInt()).coerceAtMost(4)
                    drawRoundRect(
                        HeatLevels[level],
                        Offset(h * (cw + gap), r * (ch + rowGap)),
                        Size(cw, ch),
                        CornerRadius(4.dp.toPx()),
                    )
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("12a", "6a", "12p", "6p", "12a").forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun BigTile(icon: ImageVector, value: String, label: String, tint: Color, modifier: Modifier) {
    HootCard(modifier, radius = 20.dp) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
