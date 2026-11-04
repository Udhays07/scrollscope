package com.scrollscope.app.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.scrollscope.app.ui.theme.IronColors
import com.scrollscope.app.util.rememberMotionEnabled

/**
 * Countdown ring: a full [IronColors.I700] track with a 4 dp arc on top that starts at
 * 12 o'clock and sweeps clockwise for `360 * fraction` degrees. With motion on, the sweep
 * eases between readings; with reduced motion it draws the exact value.
 */
@Composable
fun TimerRing(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val target = fraction.coerceIn(0f, 1f)
    val motion = rememberMotionEnabled()
    val animated by animateFloatAsState(target, tween(900), label = "timerRing")
    val sweep = if (motion) animated else target
    Canvas(modifier) {
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
