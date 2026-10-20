@file:OptIn(ExperimentalMaterial3Api::class)

package com.scrollscope.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollscope.app.data.Apps
import com.scrollscope.app.service.ReelAccessibilityService
import com.scrollscope.app.ui.components.Mood
import com.scrollscope.app.ui.components.Owl
import com.scrollscope.app.ui.components.StatusChip
import com.scrollscope.app.ui.theme.HootColors
import com.scrollscope.app.util.DebugLog
import com.scrollscope.app.util.Sharing

@Composable
fun DebugScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val entries by DebugLog.entries.collectAsStateWithLifecycle()
    val running by ReelAccessibilityService.running.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("All") }
    val dumps = remember(entries.size) {
        Sharing.dumpsDir(ctx).listFiles()?.sortedByDescending { it.lastModified() }?.take(5).orEmpty()
    }
    val shown = entries.filter {
        when (filter) {
            "Scroll" -> it.type == "SCROLL" || it.type == "IGNORED"
            "Window" -> it.type == "WINDOW"
            "Session" -> it.type == "SESSION" || it.type == "SERVICE"
            else -> true
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 4.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    Text("Debug log", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    StatusChip(running)
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Scroll", "Window", "Session").forEach { f ->
                        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) })
                    }
                }
            }
            if (dumps.isNotEmpty()) {
                items(dumps, key = { it.name }) { file ->
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp)).background(cs.secondaryContainer)
                            .padding(start = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(file.name, Modifier.weight(1f), color = cs.onSecondaryContainer, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { Sharing.shareText(ctx, file) }) { Icon(Icons.Rounded.Share, "Share dump", tint = cs.onSecondaryContainer) }
                    }
                }
            }
            items(shown) { e ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(e.time, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = cs.onSurfaceVariant)
                    val (bg, fg) = when (e.type) {
                        "SCROLL" -> cs.secondaryContainer to cs.onSecondaryContainer
                        "WINDOW" -> cs.tertiaryContainer to cs.onTertiaryContainer
                        "SESSION", "SERVICE", "DUMP" -> HootColors.GoodContainer to HootColors.Good
                        else -> cs.surfaceContainerHighest to cs.onSurfaceVariant
                    }
                    Text(
                        e.type,
                        Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 6.dp, vertical = 2.dp),
                        color = fg, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                    )
                    Box(
                        Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(
                            when (e.app) {
                                Apps.INSTAGRAM -> cs.primary
                                Apps.YOUTUBE -> cs.tertiary
                                else -> Color.Transparent
                            }
                        )
                    )
                    Text(e.detail, fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = {
                val svc = ReelAccessibilityService.instance
                if (svc == null) {
                    Toast.makeText(ctx, "Service isn’t running", Toast.LENGTH_SHORT).show()
                } else {
                    svc.requestDelayedDump(5_000)
                    Toast.makeText(ctx, "Open a reel — dumping in 5 s", Toast.LENGTH_LONG).show()
                }
            },
            icon = { Owl(Mood.Wide, Modifier.size(32.dp)) },
            text = { Text("Dump in 5s") },
            containerColor = cs.secondaryContainer,
            contentColor = cs.onSecondaryContainer,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}
