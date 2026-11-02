package com.scrollscope.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.scrollscope.app.ui.theme.HootColors
import com.scrollscope.app.util.rememberMotionEnabled
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class Mood { Awake, Wide, Sleepy, Happy }

fun moodFor(count: Int, limit: Int): Mood =
    if (limit > 0 && count >= limit * 0.8f) Mood.Wide else Mood.Awake

/**
 * Hoot, drawn from the same 120×120 artwork as the launcher icon, with motion:
 * every mood bobs and blinks; Happy hops and flaps, Wide shakes, Sleepy breathes with z's.
 */
@Composable
fun Owl(mood: Mood, modifier: Modifier = Modifier, animated: Boolean = true) {
    val motion = animated && rememberMotionEnabled()
    val t = rememberInfiniteTransition(label = "hoot")
    fun loop(ms: Int) = infiniteRepeatable<Float>(tween(ms, easing = LinearEasing))
    val slow by t.animateFloat(0f, 1f, loop(3200), label = "slow")
    val blinkP by t.animateFloat(0f, 1f, loop(4600), label = "blink")
    val flapP by t.animateFloat(0f, 1f, loop(800), label = "flap")
    val shakeP by t.animateFloat(0f, 1f, loop(2600), label = "shake")
    val zP by t.animateFloat(0f, 1f, loop(3000), label = "z")

    Canvas(modifier.semantics { contentDescription = "Hoot the owl" }) {
        val s = size.minDimension / 120f
        translate((size.width - 120f * s) / 2f, (size.height - 120f * s) / 2f) {
            scale(s, s, pivot = Offset.Zero) {
                if (!motion) {
                    drawOwl(mood, 0f, 0f, 1f, 0f, 1f)
                    if (mood == Mood.Sleepy) drawZs(0.3f, 0.3f)
                    return@scale
                }
                val wave = 0.5f - 0.5f * cos(2f * PI.toFloat() * slow)
                val bobY = when (mood) {
                    Mood.Happy -> hop((slow * 2f) % 1f)
                    Mood.Sleepy -> 0f
                    else -> -3f * wave
                }
                val shakeX = if (mood == Mood.Wide) shake(shakeP) else 0f
                val wing = if (mood == Mood.Happy) 14f * (0.5f - 0.5f * cos(2f * PI.toFloat() * flapP)) else 0f
                val breathe = if (mood == Mood.Sleepy) 1f + 0.03f * wave else 1f
                drawOwl(mood, shakeX, bobY, blink(blinkP), wing, breathe)
                if (mood == Mood.Sleepy) drawZs(zP, (zP + 0.5f) % 1f)
            }
        }
    }
}

/** Owl inside a round backdrop, used as avatar / app-bar icon. */
@Composable
fun OwlAvatar(mood: Mood, size: Dp, modifier: Modifier = Modifier, backdrop: Color = HootColors.OwlBackdrop, animated: Boolean = true) {
    Box(
        modifier.size(size).clip(CircleShape).background(backdrop),
        contentAlignment = Alignment.Center,
    ) { Owl(mood, Modifier.size(size * 0.8f), animated) }
}

private fun hop(q: Float) = if (q < 0.6f) -7f * sin(PI.toFloat() * q / 0.6f) else 0f

private fun blink(q: Float) = when {
    q < 0.90f -> 1f
    q < 0.93f -> 1f - 0.9f * (q - 0.90f) / 0.03f
    q < 0.96f -> 0.1f + 0.9f * (q - 0.93f) / 0.03f
    else -> 1f
}

private fun shake(q: Float) =
    if (q in 0.70f..0.90f) 2f * sin(((q - 0.70f) / 0.20f) * 4f * PI.toFloat()) else 0f

private val EarWing = Color(0xFFA9551F)
private val Body = Color(0xFFD2702F)
private val Belly = Color(0xFFFFD9BC)
private val BellyMark = Color(0xFFE8A877)
private val Face = Color(0xFFFFF4EC)
private val Pupil = Color(0xFF22140C)
private val Beak = Color(0xFFFFB020)
private val Zed = Color(0xFFFFB68A)

/** Quadratic curve expressed as a cubic (avoids version-specific Path APIs). */
private fun Path.quad(x0: Float, y0: Float, cx: Float, cy: Float, x: Float, y: Float) {
    val k = 2f / 3f
    cubicTo(x0 + k * (cx - x0), y0 + k * (cy - y0), x + k * (cx - x), y + k * (cy - y), x, y)
}

private fun DrawScope.oval(cx: Float, cy: Float, rx: Float, ry: Float, c: Color) =
    drawOval(c, topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2, ry * 2))

private fun DrawScope.dot(cx: Float, cy: Float, r: Float, c: Color) = drawCircle(c, r, Offset(cx, cy))

private val wingLeft = Path().apply {
    moveTo(18f, 70f); quad(18f, 70f, 14f, 92f, 34f, 104f); quad(34f, 104f, 26f, 86f, 30f, 66f); close()
}
private val wingRight = Path().apply {
    moveTo(102f, 70f); quad(102f, 70f, 106f, 92f, 86f, 104f); quad(86f, 104f, 94f, 86f, 90f, 66f); close()
}
private val ears = Path().apply {
    moveTo(26f, 34f); lineTo(33f, 10f); lineTo(50f, 27f); close()
    moveTo(94f, 34f); lineTo(87f, 10f); lineTo(70f, 27f); close()
}
private val bellyMarks = Path().apply {
    moveTo(51f, 86f); quad(51f, 86f, 55f, 90f, 59f, 86f)
    moveTo(61f, 86f); quad(61f, 86f, 65f, 90f, 69f, 86f)
    moveTo(56f, 94f); quad(56f, 94f, 60f, 98f, 64f, 94f)
}
private val sleepyLids = Path().apply {
    moveTo(35f, 56f); quad(35f, 56f, 44f, 62f, 53f, 56f)
    moveTo(67f, 56f); quad(67f, 56f, 76f, 62f, 85f, 56f)
}
private val happyLids = Path().apply {
    moveTo(36f, 59f); quad(36f, 59f, 44f, 49f, 52f, 59f)
    moveTo(68f, 59f); quad(68f, 59f, 76f, 49f, 84f, 59f)
}
private val beak = Path().apply { moveTo(54f, 65f); lineTo(66f, 65f); lineTo(60f, 75f); close() }

private fun DrawScope.drawOwl(mood: Mood, dx: Float, dy: Float, blinkY: Float, wingDeg: Float, breathe: Float) {
    translate(dx, dy) {
        scale(breathe, 2f - breathe, pivot = Offset(60f, 112f)) {
            drawPath(ears, EarWing)
            oval(60f, 68f, 42f, 45f, Body)
            rotate(wingDeg, pivot = Offset(30f, 68f)) { drawPath(wingLeft, EarWing) }
            rotate(-wingDeg, pivot = Offset(90f, 68f)) { drawPath(wingRight, EarWing) }
            oval(60f, 90f, 23f, 20f, Belly)
            drawPath(bellyMarks, BellyMark, style = Stroke(width = 2f, cap = StrokeCap.Round))
            dot(44f, 55f, 17f, Face)
            dot(76f, 55f, 17f, Face)

            val lid = Stroke(width = 3.5f, cap = StrokeCap.Round)
            when (mood) {
                Mood.Awake -> scale(1f, blinkY, pivot = Offset(60f, 56f)) {
                    dot(45f, 56f, 8f, Pupil); dot(75f, 56f, 8f, Pupil)
                    dot(48f, 53f, 2.6f, Color.White); dot(78f, 53f, 2.6f, Color.White)
                }
                Mood.Wide -> scale(1f, blinkY, pivot = Offset(60f, 55f)) {
                    dot(44f, 55f, 12f, Pupil); dot(76f, 55f, 12f, Pupil)
                    dot(48f, 51f, 3.4f, Color.White); dot(80f, 51f, 3.4f, Color.White)
                }
                Mood.Sleepy -> drawPath(sleepyLids, Pupil, style = lid)
                Mood.Happy -> drawPath(happyLids, Pupil, style = lid)
            }

            drawPath(beak, Beak)
            oval(49f, 113f, 7f, 4f, Beak)
            oval(71f, 113f, 7f, 4f, Beak)
        }
    }
}

/** Two floating "z" marks, each at its own loop progress 0..1. */
private fun DrawScope.drawZs(q1: Float, q2: Float) {
    fun z(x: Float, y: Float, w: Float, q: Float) {
        val alpha = if (q < 0.2f) q / 0.2f else 1f - (q - 0.2f) / 0.8f
        val ox = x + 8f * q
        val oy = y - 16f * q
        val p = Path().apply {
            moveTo(ox, oy); lineTo(ox + w, oy); lineTo(ox, oy + w); lineTo(ox + w, oy + w)
        }
        drawPath(p, Zed.copy(alpha = alpha.coerceIn(0f, 1f)), style = Stroke(width = 2.2f, cap = StrokeCap.Round))
    }
    z(96f, 22f, 8f, q1)
    z(104f, 10f, 6f, q2)
}
