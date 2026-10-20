package com.scrollscope.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollscope.app.data.Stats
import com.scrollscope.app.data.TodayUi
import com.scrollscope.app.data.TrendsUi
import com.scrollscope.app.ui.components.Mood
import com.scrollscope.app.ui.components.Owl
import com.scrollscope.app.ui.components.OwlAvatar
import com.scrollscope.app.util.Format
import com.scrollscope.app.util.Sharing
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CardBg = Color(0xFF2E1D13)
private val CardInk = Color(0xFFFFEDE4)
private val CardSub = Color(0xFFE8CDBE)
private val CardTile = Color(0xFF3B2516)
private val Amber = Color(0xFFFFB68A)
private val BarMuted = Color(0xFF5D3A22)

@Composable
fun ShareDialog(ui: TodayUi, onDismiss: () -> Unit) {
    val week by remember { Stats.trends(7) }.collectAsStateWithLifecycle(TrendsUi())
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.End) {
            Box(
                Modifier.drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                },
            ) { ShareCard(ui, week) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = {
                    scope.launch {
                        val bmp = layer.toImageBitmap().asAndroidBitmap()
                        Sharing.shareImage(ctx, bmp)
                        onDismiss()
                    }
                }) { Text("Share") }
            }
        }
    }
}

/** 4:5 card, sized for Instagram stories / WhatsApp status. */
@Composable
fun ShareCard(ui: TodayUi, week: TrendsUi) {
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE · d MMM", Locale.getDefault()))
    val mood = if (ui.count < ui.limit) Mood.Happy else Mood.Wide
    Column(
        Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 5f)
            .clip(RoundedCornerShape(28.dp))
            .background(CardBg)
            .padding(22.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OwlAvatar(Mood.Awake, 26.dp, backdrop = Color(0xFF1A120E))
            Text("ScrollScope", color = CardSub, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(date, color = CardSub, fontSize = 13.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Owl(mood, Modifier.size(112.dp))
            Column {
                Text("${ui.count}", color = Amber, fontSize = 68.sp, fontWeight = FontWeight.Bold, lineHeight = 68.sp)
                Text("reels today", color = CardInk, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
        val values = week.days.map { it.count }
        Canvas(Modifier.fillMaxWidth().height(44.dp)) {
            if (values.isEmpty()) return@Canvas
            val max = (values.maxOrNull() ?: 1).coerceAtLeast(1)
            val gap = 6.dp.toPx()
            val w = (size.width - gap * (values.size - 1)) / values.size
            values.forEachIndexed { i, v ->
                val h = (v.toFloat() / max * size.height).coerceAtLeast(4.dp.toPx())
                drawRoundRect(
                    if (i == values.lastIndex) Amber else BarMuted,
                    topLeft = Offset(i * (w + gap), size.height - h),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val avg = if (ui.count > 0) ui.watchMs / ui.count / 1000 else 0
            CardStat(Icons.Rounded.Timer, Format.duration(ui.watchMs), Modifier.weight(1f))
            CardStat(Icons.Rounded.Layers, "${ui.sessions.size}", Modifier.weight(1f))
            CardStat(Icons.Rounded.Speed, "${avg}s", Modifier.weight(1f))
        }
    }
}

@Composable
private fun CardStat(icon: ImageVector, value: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(CardTile).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = Amber, modifier = Modifier.size(20.dp))
        Text(value, color = CardInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
