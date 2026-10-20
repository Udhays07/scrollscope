package com.scrollscope.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.scrollscope.app.ui.screens.DebugScreen
import com.scrollscope.app.ui.screens.InsightsScreen
import com.scrollscope.app.ui.screens.OnboardingScreen
import com.scrollscope.app.ui.screens.SettingsScreen
import com.scrollscope.app.ui.screens.TodayScreen
import com.scrollscope.app.ui.screens.TrendsScreen
import com.scrollscope.app.ui.theme.ScrollScopeTheme
import com.scrollscope.app.util.Permissions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScrollScopeTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { AppRoot() }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val ctx = LocalContext.current
    var perms by remember { mutableStateOf(Permissions.snapshot(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { perms = Permissions.snapshot(ctx) }
    var onboarded by rememberSaveable { mutableStateOf(perms.accessibility) }

    if (!onboarded) {
        OnboardingScreen(perms, onRefresh = { perms = Permissions.snapshot(ctx) }, onDone = { onboarded = true })
    } else {
        MainScaffold(trackingOn = perms.accessibility)
    }
}

private data class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val Tabs = listOf(
    Tab("Today", Icons.Rounded.DonutLarge),
    Tab("Trends", Icons.Rounded.BarChart),
    Tab("Insights", Icons.Rounded.Bedtime),
    Tab("Settings", Icons.Rounded.Tune),
)

@Composable
private fun MainScaffold(trackingOn: Boolean) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var debug by rememberSaveable { mutableStateOf(false) }

    if (debug) {
        Surface(Modifier.fillMaxSize().statusBarsPadding()) { DebugScreen(onBack = { debug = false }) }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Surface(Modifier.padding(padding), color = MaterialTheme.colorScheme.surface) {
            when (tab) {
                0 -> TodayScreen(trackingOn)
                1 -> TrendsScreen()
                2 -> InsightsScreen(onOpenSettings = { tab = 3 })
                else -> SettingsScreen(trackingOn, onOpenDebug = { debug = true })
            }
        }
    }
}
