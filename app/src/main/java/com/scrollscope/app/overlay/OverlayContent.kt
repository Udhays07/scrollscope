package com.scrollscope.app.overlay

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollscope.app.ui.components.Mood
import com.scrollscope.app.ui.components.Owl
import com.scrollscope.app.ui.theme.HootColors
import kotlinx.coroutines.flow.StateFlow

@Composable
fun BubbleContent(
    state: StateFlow<BubbleState>,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onTap: () -> Unit,
) {
    val s by state.collectAsStateWithLifecycle()
    val pct = if (s.limit > 0) s.count.toFloat() / s.limit else 0f
    val cs = MaterialTheme.colorScheme
    val (ring, onRing) = when {
        pct >= 1f -> cs.error to cs.onError
        pct >= 0.8f -> HootColors.Amber to HootColors.OnAmber
        else -> cs.primary to cs.onPrimary
    }
    val mood = if (pct >= 0.8f) Mood.Wide else Mood.Awake

    Box(
        Modifier
            .size(width = 64.dp, height = 74.dp)
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = onDragEnd) { change, drag ->
                    change.consume()
                    onDrag(drag.x, drag.y)
                }
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }, onLongPress = { onTap() }) },
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(HootColors.OwlBackdrop)
                .border(3.dp, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Owl(mood, Modifier.size(46.dp)) }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(11.dp))
                .background(ring)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Text("${s.count}", color = onRing, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MenuContent(
    anchorXpx: Int,
    anchorYpx: Int,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
    onDump: () -> Unit,
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
        val x = remember(anchorXpx) { (anchorXpx + with(density) { 64.dp.roundToPx() } - menuWpx).coerceIn(0, (screenWpx - menuWpx).coerceAtLeast(0)) }
        Surface(
            modifier = Modifier.offset { IntOffset(x, anchorYpx) }.width(menuW),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 6.dp,
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                MenuRow(Icons.Rounded.Snooze, "Snooze 10 min", onSnooze)
                MenuRow(Icons.Rounded.AccountTree, "Dump screen tree", onDump)
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
            .background(Color.Black.copy(alpha = 0.62f))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 320.dp).padding(24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(120.dp).clip(CircleShape).background(HootColors.OwlBackdrop),
                    contentAlignment = Alignment.Center,
                ) { Owl(Mood.Sleepy, Modifier.size(96.dp)) }
                Text("$count in a row", style = MaterialTheme.typography.headlineSmall)
                Text("Stretch break?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onKeepWatching, modifier = Modifier.weight(1f).height(48.dp)) { Text("5 more") }
                    Button(onClick = onTakeBreak, modifier = Modifier.weight(1f).height(48.dp)) { Text("Take a break") }
                }
            }
        }
    }
}
