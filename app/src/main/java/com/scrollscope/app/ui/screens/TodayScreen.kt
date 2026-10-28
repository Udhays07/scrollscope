package com.scrollscope.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollscope.app.data.Apps
import com.scrollscope.app.data.Stats
import com.scrollscope.app.data.TodayUi
import com.scrollscope.app.ui.components.HootCard
import com.scrollscope.app.ui.components.HootTopBar
import com.scrollscope.app.ui.components.InfoChip
import com.scrollscope.app.ui.components.Owl
import com.scrollscope.app.ui.components.moodFor
import com.scrollscope.app.ui.theme.HootColors
import com.scrollscope.app.util.Days
import com.scrollscope.app.util.Format
import com.scrollscope.app.util.Permissions

@Composable
fun TodayScreen(trackingOn: Boolean) {
    val ui by remember { Stats.today() }.collectAsStateWithLifecycle(TodayUi())
    var sharing by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val mood = moodFor(ui.count, ui.limit)

    Column(Modifier.verticalScroll(rememberScrollState())) {
        HootTopBar("Today", mood) {
            IconButton(onClick = { sharing = true }) { Icon(Icons.Rounded.Share, "Share") }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!trackingOn) {
                HootCard(color = HootColors.BadContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Tracking is off", Modifier.weight(1f), color = cs.error)
                        TextButton(onClick = { Permissions.openAccessibilitySettings(ctx) }) { Text("Fix") }
                    }
                }
            }

            HootCard(radius = 28.dp) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
                        ProgressRing(ui.count, ui.limit, Modifier.size(210.dp))
                        Owl(mood, Modifier.size(120.dp))
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        val shown by animateIntAsState(ui.count, tween(900, easing = FastOutSlowInEasing), label = "count")
                        Text("$shown", fontSize = 57.sp, fontWeight = FontWeight.SemiBold, lineHeight = 57.sp)
                        Text(" / ${ui.limit}", fontSize = 20.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoChip(Icons.Rounded.Timer, Format.duration(ui.watchMs))
                        InfoChip(Icons.Rounded.Layers, "${ui.sessions.size}")
                        val avg = if (ui.count > 0) ui.watchMs / ui.count / 1000 else 0
                        InfoChip(Icons.Rounded.Bolt, "${avg}s")
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppCard(Icons.Rounded.Movie, ui.byApp[Apps.INSTAGRAM] ?: 0, "Reels", cs.primary, cs.primaryContainer, Modifier.weight(1f))
                AppCard(Icons.Rounded.SmartDisplay, ui.byApp[Apps.YOUTUBE] ?: 0, "Shorts", cs.tertiary, cs.tertiaryContainer, Modifier.weight(1f))
            }

            HootCard(radius = 20.dp) { Timeline(ui) }
        }
    }

    if (sharing) ShareDialog(ui, onDismiss = { sharing = false })
}

@Composable
private fun ProgressRing(count: Int, limit: Int, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val pct = if (limit > 0) count.toFloat() / limit else 0f
    val color = when {
        pct >= 1f -> cs.error
        pct >= 0.8f -> HootColors.Amber
        else -> cs.primary
    }
    val track = cs.surfaceContainerHighest
    val fill = remember { Animatable(0f) }
    LaunchedEffect(pct) { fill.animateTo(pct.coerceIn(0f, 1f), tween(1100, easing = FastOutSlowInEasing)) }
    Canvas(modifier) {
        val w = 16.dp.toPx()
        val inset = w / 2
        val arcSize = Size(size.width - w, size.height - w)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
        drawArc(color, -90f, 360f * fill.value, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
    }
}

@Composable
private fun AppCard(icon: ImageVector, count: Int, label: String, tint: Color, bg: Color, modifier: Modifier) {
    HootCard(modifier, radius = 20.dp) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(bg),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
        Text("$count", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Timeline(ui: TodayUi) {
    val cs = MaterialTheme.colorScheme
    val maxCount = (ui.sessions.maxOfOrNull { it.reelCount } ?: 1).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(48.dp)) {
        val base = size.height - 1.dp.toPx()
        drawLine(cs.surfaceContainerHighest, Offset(0f, base), Offset(size.width, base), strokeWidth = 2.dp.toPx())
        ui.sessions.forEach { s ->
            val x = Days.minuteOfDay(s.startTs) / 1440f * size.width
            val frac = s.reelCount.toFloat() / maxCount
            val h = 6.dp.toPx() + frac * (size.height - 8.dp.toPx())
            val w = 6.dp.toPx() + frac * 6.dp.toPx()
            val color = if (s.app == Apps.YOUTUBE) cs.tertiary else cs.primary
            drawRoundRect(
                color,
                topLeft = Offset((x - w / 2).coerceIn(0f, size.width - w), base - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("12a", "6a", "12p", "6p", "12a").forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
}
