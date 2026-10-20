package com.scrollscope.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.scrollscope.app.ui.components.Mood
import com.scrollscope.app.ui.components.OwlAvatar
import com.scrollscope.app.ui.theme.HootColors
import com.scrollscope.app.util.PermState
import com.scrollscope.app.util.Permissions

@Composable
fun OnboardingScreen(perms: PermState, onRefresh: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onRefresh() }
    val cs = MaterialTheme.colorScheme

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        OwlAvatar(Mood.Happy, 168.dp)
        Text("Hi, I’m Hoot", style = MaterialTheme.typography.headlineLarge)
        Text("I’ll count every reel you swipe.", color = cs.onSurfaceVariant)

        Surface(shape = RoundedCornerShape(28.dp), color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column {
                PermRow(Icons.Rounded.AccessibilityNew, "Accessibility", perms.accessibility, highlight = !perms.accessibility) {
                    Permissions.openAccessibilitySettings(ctx)
                }
                HorizontalDivider(color = cs.surfaceContainerHighest)
                PermRow(Icons.Rounded.BatteryChargingFull, "Battery", perms.battery, highlight = perms.accessibility && !perms.battery) {
                    Permissions.requestBatteryExemption(ctx)
                }
                HorizontalDivider(color = cs.surfaceContainerHighest)
                PermRow(Icons.Rounded.Notifications, "Notifications", perms.notifications, highlight = false) {
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        if (!perms.accessibility && Build.VERSION.SDK_INT >= 33) {
            TextButton(onClick = { Permissions.openAppInfo(ctx) }) {
                Text("Toggle greyed out? Allow restricted settings")
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Lock, null, tint = HootColors.Good, modifier = Modifier.size(16.dp))
            Text("Stays on your phone", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Button(
            onClick = onDone,
            enabled = perms.accessibility,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Continue") }
    }
}

@Composable
private fun PermRow(icon: ImageVector, label: String, granted: Boolean, highlight: Boolean, onAllow: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (highlight) cs.surfaceContainerHigh else cs.surfaceContainer)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (highlight) cs.primary else cs.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (highlight) cs.onPrimary else cs.primary) }
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (granted) {
            Icon(Icons.Rounded.Check, "Granted", tint = HootColors.Good)
        } else {
            FilledTonalButton(onClick = onAllow) { Text("Allow") }
        }
    }
}
