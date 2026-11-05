package com.scrollscope.app.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.scrollscope.app.ui.theme.IronColors
import com.scrollscope.app.util.rememberMotionEnabled
import kotlin.math.abs

/**
 * Countdown ring: a full [IronColors.I700] track with a 4 dp arc on top that starts at
 * 12 o'clock and sweeps clockwise for `360 * fraction` degrees. With motion on, real jumps
 * (a change of at least 0.01) ease over 900 ms and per-second ticks snap; with reduced motion
 * it draws the exact value. The animated value is read only in the draw phase, so the ring
 * redraws without recomposing every frame.
 */
@Composable
fun TimerRing(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val target = fraction.coerceIn(0f, 1f)
    val motion = rememberMotionEnabled()
    val animated = remember { Animatable(target) }
    LaunchedEffect(target, motion) {
        if (motion && abs(target - animated.value) >= 0.01f) animated.animateTo(target, tween(900))
        else animated.snapTo(target)
    }
    Canvas(modifier) {
        val sweep = if (motion) animated.value else target
        val stroke = 4.dp.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)
        drawArc(IronColors.I700, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
        if (sweep > 0f) {
            drawArc(color, -90f, 360f * sweep, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}
