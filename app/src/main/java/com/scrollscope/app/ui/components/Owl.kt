package com.scrollscope.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.scrollscope.app.ui.theme.HootColors

enum class Mood { Awake, Wide, Sleepy, Happy }

fun moodFor(count: Int, limit: Int): Mood =
    if (limit > 0 && count >= limit * 0.8f) Mood.Wide else Mood.Awake

/** Hoot, drawn from the same 120×120 artwork as the launcher icon. */
@Composable
fun Owl(mood: Mood, modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = "Hoot the owl" }) {
        val s = size.minDimension / 120f
        translate((size.width - 120f * s) / 2f, (size.height - 120f * s) / 2f) {
            scale(s, s, pivot = Offset.Zero) { drawOwl(mood) }
        }
    }
}

/** Owl inside a round backdrop, used as avatar / app-bar icon. */
@Composable
fun OwlAvatar(mood: Mood, size: Dp, modifier: Modifier = Modifier, backdrop: Color = HootColors.OwlBackdrop) {
    Box(
        modifier.size(size).clip(CircleShape).background(backdrop),
        contentAlignment = Alignment.Center,
    ) { Owl(mood, Modifier.size(size * 0.8f)) }
}

private val EarWing = Color(0xFFA9551F)
private val Body = Color(0xFFD2702F)
private val Belly = Color(0xFFFFD9BC)
private val BellyMark = Color(0xFFE8A877)
private val Face = Color(0xFFFFF4EC)
private val Pupil = Color(0xFF22140C)
private val Beak = Color(0xFFFFB020)

/** Quadratic curve expressed as a cubic (avoids version-specific Path APIs). */
private fun Path.quad(x0: Float, y0: Float, cx: Float, cy: Float, x: Float, y: Float) {
    val k = 2f / 3f
    cubicTo(x0 + k * (cx - x0), y0 + k * (cy - y0), x + k * (cx - x), y + k * (cy - y), x, y)
}

private fun DrawScope.oval(cx: Float, cy: Float, rx: Float, ry: Float, c: Color) =
    drawOval(c, topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2, ry * 2))

private fun DrawScope.dot(cx: Float, cy: Float, r: Float, c: Color) = drawCircle(c, r, Offset(cx, cy))

private fun DrawScope.drawOwl(mood: Mood) {
    drawPath(Path().apply {
        moveTo(26f, 34f); lineTo(33f, 10f); lineTo(50f, 27f); close()
        moveTo(94f, 34f); lineTo(87f, 10f); lineTo(70f, 27f); close()
    }, EarWing)
    oval(60f, 68f, 42f, 45f, Body)
    drawPath(Path().apply {
        moveTo(18f, 70f); quad(18f, 70f, 14f, 92f, 34f, 104f); quad(34f, 104f, 26f, 86f, 30f, 66f); close()
        moveTo(102f, 70f); quad(102f, 70f, 106f, 92f, 86f, 104f); quad(86f, 104f, 94f, 86f, 90f, 66f); close()
    }, EarWing)
    oval(60f, 90f, 23f, 20f, Belly)
    drawPath(Path().apply {
        moveTo(51f, 86f); quad(51f, 86f, 55f, 90f, 59f, 86f)
        moveTo(61f, 86f); quad(61f, 86f, 65f, 90f, 69f, 86f)
        moveTo(56f, 94f); quad(56f, 94f, 60f, 98f, 64f, 94f)
    }, BellyMark, style = Stroke(width = 2f, cap = StrokeCap.Round))
    dot(44f, 55f, 17f, Face)
    dot(76f, 55f, 17f, Face)

    val lid = Stroke(width = 3.5f, cap = StrokeCap.Round)
    when (mood) {
        Mood.Awake -> {
            dot(45f, 56f, 8f, Pupil); dot(75f, 56f, 8f, Pupil)
            dot(48f, 53f, 2.6f, Color.White); dot(78f, 53f, 2.6f, Color.White)
        }
        Mood.Wide -> {
            dot(44f, 55f, 12f, Pupil); dot(76f, 55f, 12f, Pupil)
            dot(48f, 51f, 3.4f, Color.White); dot(80f, 51f, 3.4f, Color.White)
        }
        Mood.Sleepy -> drawPath(Path().apply {
            moveTo(35f, 56f); quad(35f, 56f, 44f, 62f, 53f, 56f)
            moveTo(67f, 56f); quad(67f, 56f, 76f, 62f, 85f, 56f)
        }, Pupil, style = lid)
        Mood.Happy -> drawPath(Path().apply {
            moveTo(36f, 59f); quad(36f, 59f, 44f, 49f, 52f, 59f)
            moveTo(68f, 59f); quad(68f, 59f, 76f, 49f, 84f, 59f)
        }, Pupil, style = lid)
    }

    drawPath(Path().apply { moveTo(54f, 65f); lineTo(66f, 65f); lineTo(60f, 75f); close() }, Beak)
    oval(49f, 113f, 7f, 4f, Beak)
    oval(71f, 113f, 7f, 4f, Beak)
}
