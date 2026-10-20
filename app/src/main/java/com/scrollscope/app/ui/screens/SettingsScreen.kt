package com.scrollscope.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollscope.app.ScrollScopeApp
import com.scrollscope.app.data.AppSettings
import com.scrollscope.app.ui.components.HootCard
import com.scrollscope.app.ui.components.HootTopBar
import com.scrollscope.app.ui.components.StatusChip
import com.scrollscope.app.util.Format
import com.scrollscope.app.work.DailySummaryWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(trackingOn: Boolean, onOpenDebug: () -> Unit) {
    val app = ScrollScopeApp.instance
    val s by app.settingsState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var confirmReset by remember { mutableStateOf(false) }

    fun update(t: (AppSettings) -> AppSettings) = scope.launch { app.settings.update(t) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            withContext(Dispatchers.IO) {
                val rows = app.db.stats().allEvents()
                ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { w ->
                    w.write("timestamp,day,hour,app,session,dwell_ms\n")
                    rows.forEach { r -> w.write("${r.ts},${r.day},${r.hour},${r.app},${r.sessionId},${r.dwellMs ?: ""}\n") }
                }
            }
            Toast.makeText(ctx, "Exported", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        HootTopBar("Settings") { StatusChip(trackingOn) }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Group {
                Item(Icons.Rounded.TrackChanges, "Daily limit") {
                    Row(
                        Modifier.clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { update { it.copy(dailyLimit = (it.dailyLimit - 25).coerceAtLeast(25)) } }) { Text("−", fontSize = 20.sp) }
                        Text("${s.dailyLimit}", Modifier.widthIn(min = 36.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { update { it.copy(dailyLimit = (it.dailyLimit + 25).coerceAtMost(1000)) } }) { Text("+", fontSize = 20.sp) }
                    }
                }
                Divider()
                Item(Icons.Rounded.Pause, "Pause at ${s.pauseAfter}", onClick = {
                    val next = when (s.pauseAfter) { 20 -> 30; 30 -> 50; 50 -> 80; else -> 20 }
                    update { it.copy(pauseAfter = next) }
                }) {
                    Switch(s.pauseEnabled, onCheckedChange = { v -> update { it.copy(pauseEnabled = v) } })
                }
            }
            Group {
                Item(Icons.Rounded.Visibility, "Floating Hoot") {
                    Switch(s.bubbleEnabled, onCheckedChange = { v -> update { it.copy(bubbleEnabled = v) } })
                }
                Divider()
                Item(Icons.Rounded.Movie, "Reels", tint = cs.primary) {
                    Switch(s.trackInstagram, onCheckedChange = { v -> update { it.copy(trackInstagram = v) } })
                }
                Divider()
                Item(Icons.Rounded.SmartDisplay, "Shorts", tint = cs.tertiary) {
                    Switch(s.trackYoutube, onCheckedChange = { v -> update { it.copy(trackYoutube = v) } })
                }
            }
            Group {
                Item(Icons.Rounded.Notifications, "Summary", onClick = {
                    val next = if (s.summaryHour >= 23) 20 else s.summaryHour + 1
                    update { it.copy(summaryHour = next) }
                    DailySummaryWorker.schedule(ctx, next, replace = true)
                }) { Value(Format.hour12(s.summaryHour)) }
                Divider()
                Item(Icons.Rounded.History, "Keep history", onClick = {
                    val next = when (s.retentionDays) { 30 -> 90; 90 -> 365; else -> 30 }
                    update { it.copy(retentionDays = next) }
                }) { Value("${s.retentionDays} days") }
                Divider()
                Item(Icons.Rounded.Download, "Export CSV", onClick = { exportLauncher.launch("scrollscope.csv") }) {
                    Icon(Icons.Rounded.ChevronRight, null)
                }
                Divider()
                Item(Icons.Rounded.BugReport, "Debug log", onClick = onOpenDebug) {
                    Icon(Icons.Rounded.ChevronRight, null)
                }
                Divider()
                Item(Icons.Rounded.DeleteOutline, "Reset data", tint = cs.error, labelColor = cs.error, onClick = { confirmReset = true }) {}
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset all data?") },
            text = { Text("Every reel and session will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch(Dispatchers.IO) {
                        app.db.stats().clearEvents()
                        app.db.stats().clearSessions()
                    }
                }) { Text("Reset", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column { content() }
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)

@Composable
private fun Value(text: String) =
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)

@Composable
private fun Item(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    ListItem(
        headlineContent = { Text(label, color = labelColor) },
        leadingContent = { Icon(icon, null, tint = tint) },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}
