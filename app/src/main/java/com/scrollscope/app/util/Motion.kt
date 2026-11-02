package com.scrollscope.app.util

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Respects Android's "Remove animations" / animator duration scale = 0. */
object Motion {
    fun enabled(ctx: Context): Boolean =
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
}

@Composable
fun rememberMotionEnabled(): Boolean {
    val ctx = LocalContext.current
    return remember { Motion.enabled(ctx) }
}
