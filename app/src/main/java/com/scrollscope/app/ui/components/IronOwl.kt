package com.scrollscope.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.scrollscope.app.ui.theme.IronColors
import com.scrollscope.app.util.rememberMotionEnabled
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Guard: stern, ember eyes. Granted: visor lifted, Hoot's amber eyes. Night: visor lowered, slow blinks. */
enum class IronPose { Guard, Granted, Night }

/**
 * Iron Owl: Hoot in armour, drawn on the same 120×124 grid as [Owl].
 *
 * @param eyeOffset where to look, -1..1 on each axis (follows the user's finger on the lock screen).
 * @param straining trembles and clenches the visor while the user holds him for a pass.
 * @param shakeKey bump to play one "no" head shake.
 * @param fumeKey bump to puff steam from the ears and flare the wings.
 * @param lit 0..1 brightness of the ember eyes (the lock screen flickers them on).
 */
@Composable
fun IronOwl(
    modifier: Modifier = Modifier,
    pose: IronPose = IronPose.Guard,
    eyeOffset: Offset = Offset.Zero,
    straining: Boolean = false,
    shakeKey: Int = 0,
    fumeKey: Int = 0,
    lit: Float = 1f,
    animated: Boolean = true,
) {
    val motion = animated && rememberMotionEnabled()
    val t = rememberInfiniteTransition(label = "iron")
    fun loop(ms: Int) = infiniteRepeatable<Float>(tween(ms, easing = LinearEasing))
    val breathP by t.animateFloat(0f, 1f, loop(if (pose == IronPose.Night) 5600 else 3600), label = "breath")
    val glowP by t.animateFloat(0f, 1f, loop(2400), label = "glow")
    val glintP by t.animateFloat(0f, 1f, loop(6000), label = "glint")
    val scanP by t.animateFloat(0f, 1f, loop(8000), label = "scan")
    val blinkP by t.animateFloat(0f, 1f, loop(5500), label = "blink")
    val trembleP by t.animateFloat(0f, 1f, loop(90), label = "tremble")

    val shake = remember { Animatable(1f) }
    LaunchedEffect(shakeKey) {
        if (shakeKey > 0 && motion) { shake.snapTo(0f); shake.animateTo(1f, tween(460)) }
    }
    val fume = remember { Animatable(1f) }
    LaunchedEffect(fumeKey) {
        if (fumeKey > 0 && motion) { fume.snapTo(0f); fume.animateTo(1f, tween(2200, easing = LinearEasing)) }
    }
    val nod = remember { Animatable(1f) }
    LaunchedEffect(pose) {
        if (pose == IronPose.Granted && motion) { delay(250); nod.snapTo(0f); nod.animateTo(1f, tween(700)) }
    }
    val granted by animateFloatAsState(if (pose == IronPose.Granted) 1f else 0f, tween(450, easing = FastOutSlowInEasing), label = "granted")
    val night by animateFloatAsState(if (pose == IronPose.Night) 1f else 0f, tween(450), label = "night")
    val strain by animateFloatAsState(if (straining) 1f else 0f, tween(200), label = "strain")
    val look by animateOffsetAsState(eyeOffset, tween(120), label = "look")

    Canvas(modifier.semantics { contentDescription = "Iron Hoot" }) {
        val s = min(size.width / 120f, size.height / 124f)
        translate((size.width - 120f * s) / 2f, (size.height - 124f * s) / 2f) {
            scale(s, s, pivot = Offset.Zero) {
                if (!motion) {
                    drawIron(IronFrame(granted = if (pose == IronPose.Granted) 1f else 0f, night = if (pose == IronPose.Night) 1f else 0f))
                    return@scale
                }
                val wave = 0.5f - 0.5f * cos(2f * PI.toFloat() * breathP)
                val fumeQ = if (fume.value < 1f) (fume.value * 2f) % 1f else null
                val scanX = if (look == Offset.Zero && pose == IronPose.Guard && !straining) scan(scanP) else 0f
                val blink = if (pose == IronPose.Night) nightBlink(blinkP) else 1f
                val tw = 2f * PI.toFloat() * trembleP
                drawIron(
                    IronFrame(
                        dx = 0.8f * sin(tw) * strain,
                        dy = 0.3f * cos(tw) * strain + (if (nod.value < 1f) 4f * sin(PI.toFloat() * nod.value) else 0f),
                        rot = shakeAngle(shake.value),
                        sx = 1f + 0.012f * wave,
                        sy = 1f - 0.01f * wave,
                        granted = granted,
                        night = night,
                        strain = strain,
                        glow = lit * (0.82f + 0.18f * (0.5f + 0.5f * cos(2f * PI.toFloat() * glowP)) + (if (fumeQ != null) 0.2f else 0f)),
                        blink = blink,
                        eye = Offset(look.x * 3.2f + scanX, look.y * 2.2f),
                        glintX = glint(glintP),
                        steam = fumeQ,
                        wingDeg = if (fumeQ != null) 16f * sin(PI.toFloat() * fumeQ) else 0f,
                    )
                )
            }
        }
    }
}

/** Iron Owl inside a round iron backdrop, for avatars and the bubble. */
@Composable
fun IronAvatar(size: Dp, modifier: Modifier = Modifier, pose: IronPose = IronPose.Guard, animated: Boolean = true) {
    Box(
        modifier.size(size).clip(CircleShape).background(IronColors.I800),
        contentAlignment = Alignment.Center,
    ) { IronOwl(Modifier.size(size * 0.82f), pose = pose, animated = animated) }
}

private class IronFrame(
    val dx: Float = 0f,
    val dy: Float = 0f,
    val rot: Float = 0f,
    val sx: Float = 1f,
    val sy: Float = 1f,
    val granted: Float = 0f,
    val night: Float = 0f,
    val strain: Float = 0f,
    val glow: Float = 1f,
    val blink: Float = 1f,
    val eye: Offset = Offset.Zero,
    val glintX: Float = 0f,
    val steam: Float? = null,
    val wingDeg: Float = 0f,
)

private fun lerpKeys(q: Float, keys: FloatArray, values: FloatArray): Float {
    for (i in 1 until keys.size) {
        if (q <= keys[i]) {
            val f = (q - keys[i - 1]) / (keys[i] - keys[i - 1])
            return values[i - 1] + (values[i] - values[i - 1]) * f
        }
    }
    return values.last()
}

private val shakeK = floatArrayOf(0f, 0.15f, 0.35f, 0.55f, 0.75f, 1f)
private val shakeV = floatArrayOf(0f, -8f, 7f, -5f, 3f, 0f)
private fun shakeAngle(q: Float) = if (q >= 1f) 0f else lerpKeys(q, shakeK, shakeV)

private val scanK = floatArrayOf(0f, 0.60f, 0.66f, 0.73f, 0.79f, 0.86f, 0.92f, 1f)
private val scanV = floatArrayOf(0f, 0f, -3f, -3f, 3f, 3f, 0f, 0f)
private fun scan(q: Float) = lerpKeys(q, scanK, scanV)

private val blinkK = floatArrayOf(0f, 0.78f, 0.84f, 0.90f, 0.96f, 1f)
private val blinkV = floatArrayOf(1f, 1f, 0.12f, 0.12f, 1f, 1f)
private fun nightBlink(q: Float) = lerpKeys(q, blinkK, blinkV)

private fun glint(q: Float) = when {
    q < 0.70f -> 0f
    q < 0.88f -> (q - 0.70f) / 0.18f * 150f
    else -> 150f
}

/** Quadratic curve expressed as a cubic (avoids version-specific Path APIs). */
private fun Path.quad(x0: Float, y0: Float, cx: Float, cy: Float, x: Float, y: Float) {
    val k = 2f / 3f
    cubicTo(x0 + k * (cx - x0), y0 + k * (cy - y0), x + k * (cx - x), y + k * (cy - y), x, y)
}

private fun poly(vararg p: Float) = Path().apply {
    moveTo(p[0], p[1])
    var i = 2
    while (i < p.size) { lineTo(p[i], p[i + 1]); i += 2 }
    close()
}

private val earsOuter = Path().apply {
    addPath(poly(25f, 36f, 31f, 7f, 51f, 27f)); addPath(poly(95f, 36f, 89f, 7f, 69f, 27f))
}
private val earsInner = Path().apply {
    addPath(poly(31f, 7f, 35f, 27f, 45f, 28f)); addPath(poly(89f, 7f, 85f, 27f, 75f, 28f))
}
private val bodyClip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(18f, 23f, 102f, 113f)) }
private val scallops = Path().apply {
    listOf(27f to 82f, 25f to 92f, 30f to 101f, 85f to 82f, 87f to 92f, 82f to 101f).forEach { (x, y) ->
        moveTo(x, y); quad(x, y, x + 4f, y + 4f, x + 8f, y)
    }
}
private val wingL = Path().apply {
    moveTo(18f, 70f); quad(18f, 70f, 14f, 92f, 34f, 104f); quad(34f, 104f, 26f, 86f, 30f, 66f); close()
}
private val wingR = Path().apply {
    moveTo(102f, 70f); quad(102f, 70f, 106f, 92f, 86f, 104f); quad(86f, 104f, 94f, 86f, 90f, 66f); close()
}
private val wingLEdge = Path().apply { moveTo(29.5f, 67f); quad(29.5f, 67f, 26f, 86f, 33f, 103f) }
private val wingREdge = Path().apply { moveTo(90.5f, 67f); quad(90.5f, 67f, 94f, 86f, 87f, 103f) }
private val plateShine = Path().apply { moveTo(44f, 77f); quad(44f, 77f, 60f, 70.5f, 76f, 77f) }
private val keyhole = poly(58.2f, 88.6f, 61.8f, 88.6f, 63f, 97f, 57f, 97f)
private val helm = Path().apply {
    moveTo(23f, 47f); cubicTo(24f, 29f, 41f, 19f, 60f, 19f); cubicTo(79f, 19f, 96f, 29f, 97f, 47f); close()
}
private val helmShine = Path().apply { moveTo(31f, 40f); cubicTo(34f, 30f, 45f, 24f, 57f, 23f) }
private val crestL = poly(57f, 19f, 63f, 19f, 62.4f, 44f, 57.6f, 44f)
private val crestR = poly(60f, 19f, 63f, 19f, 62.4f, 44f, 60f, 44f)
private val browL = poly(24f, 39f, 57f, 48f, 55.5f, 54.5f, 24f, 46f)
private val browR = poly(96f, 39f, 63f, 48f, 64.5f, 54.5f, 96f, 46f)
private val beak = poly(54f, 65f, 66f, 65f, 60f, 75f)
private val beakShade = poly(60f, 75f, 66f, 65f, 63f, 65f)
private val beakCap = poly(54f, 65f, 66f, 65f, 64.5f, 68.5f, 55.5f, 68.5f)
private val claws = Path().apply {
    listOf(44f to -1f, 49f to 0f, 54f to 1f, 66f to -1f, 71f to 0f, 76f to 1f).forEach { (x, d) ->
        val y = if (d == 0f) 116.5f else 115.5f
        moveTo(x, y); lineTo(x + d, y + 3f)
    }
}

private val bodyBrush = Brush.verticalGradient(listOf(Color(0xFF434A54), Color(0xFF2B3037)), startY = 23f, endY = 113f)
private val helmBrush = Brush.verticalGradient(listOf(Color(0xFF8C97A3), Color(0xFF58616B)), startY = 19f, endY = 47f)
private val plateBrush = Brush.verticalGradient(listOf(Color(0xFF9AA5B1), Color(0xFF6C7682)), startY = 70f, endY = 110f)
private val emberStops = arrayOf(
    0f to Color(0xFFFFE9C4), 0.35f to Color(0xFFFFB35A), 0.7f to IronColors.Ember, 1f to Color(0xFFA8231A),
)
private val emberL = Brush.radialGradient(*emberStops, center = Offset(45f, 58f), radius = 9f)
private val emberR = Brush.radialGradient(*emberStops, center = Offset(75f, 58f), radius = 9f)

private val Rivet = Color(0xFFE3E9EF)
private val Seam = Color(0xFF22272D)
private val Shade = Color(0xFF1D2126)
private val Socket = Color(0xFF15181C)
private val PlateRing = Color(0xFF5F6974)
private val Highlight = Color(0xFFDFE6EC)
private val Amber = Color(0xFFFFB020)
private val Pupil = Color(0xFF22140C)

private fun DrawScope.dot(cx: Float, cy: Float, r: Float, c: Color) = drawCircle(c, r, Offset(cx, cy))
private fun DrawScope.oval(cx: Float, cy: Float, rx: Float, ry: Float, c: Color) =
    drawOval(c, topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2, ry * 2))
private fun DrawScope.oval(cx: Float, cy: Float, rx: Float, ry: Float, b: Brush, alpha: Float = 1f) =
    drawOval(b, topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2, ry * 2), alpha = alpha)

private fun DrawScope.drawIron(f: IronFrame) {
    drawOval(Color.Black.copy(alpha = 0.5f), topLeft = Offset(30f, 115.1f), size = Size(60f, 6.8f))
    translate(f.dx, f.dy) {
        rotate(f.rot, pivot = Offset(60f, 100f)) {
            scale(f.sx, f.sy, pivot = Offset(60f, 116f)) {
                f.steam?.let { q -> drawSteam(q) }

                drawPath(earsOuter, IronColors.I600)
                drawPath(earsInner, IronColors.I500)
                oval(60f, 68f, 42f, 45f, bodyBrush)
                clipPath(bodyClip) { oval(74f, 90f, 40f, 34f, Shade.copy(alpha = 0.55f)) }
                drawPath(scallops, Seam, style = Stroke(1.6f, cap = StrokeCap.Round))

                rotate(f.wingDeg, pivot = Offset(30f, 68f)) {
                    drawPath(wingL, IronColors.Wing)
                    drawPath(wingLEdge, IronColors.I400, style = Stroke(1.2f, cap = StrokeCap.Round))
                    dot(23.5f, 80f, 1.6f, IronColors.I200); dot(26.5f, 92f, 1.6f, IronColors.I200)
                }
                rotate(-f.wingDeg, pivot = Offset(90f, 68f)) {
                    drawPath(wingR, IronColors.Wing)
                    drawPath(wingREdge, IronColors.I400, style = Stroke(1.2f, cap = StrokeCap.Round))
                    dot(96.5f, 80f, 1.6f, IronColors.I200); dot(93.5f, 92f, 1.6f, IronColors.I200)
                }

                oval(60f, 90f, 23f, 20f, plateBrush)
                drawOval(PlateRing, topLeft = Offset(41f, 74f), size = Size(38f, 32f), style = Stroke(1.2f))
                drawPath(plateShine, Highlight.copy(alpha = 0.6f), style = Stroke(1.4f, cap = StrokeCap.Round))
                dot(44f, 83f, 1.5f, Rivet); dot(76f, 83f, 1.5f, Rivet); dot(47f, 100f, 1.5f, Rivet); dot(73f, 100f, 1.5f, Rivet)
                dot(60f, 87f, 3.4f, Socket); drawPath(keyhole, Socket)

                drawPath(helm, helmBrush)
                drawPath(helmShine, IronColors.I300.copy(alpha = 0.75f), style = Stroke(2.4f, cap = StrokeCap.Round))
                drawPath(crestL, IronColors.I300)
                drawPath(crestR, IronColors.I400)
                if (f.night < 0.5f && f.glintX in 1f..149f) {
                    clipPath(helm) {
                        val gx = f.glintX - 20f
                        drawPath(poly(gx - 3.64f, 10f, gx + 3.36f, 10f, gx - 12.65f, 54f, gx - 19.65f, 54f), Color.White.copy(alpha = 0.3f))
                    }
                }
                dot(31f, 41f, 1.4f, Rivet); dot(41f, 31f, 1.4f, Rivet); dot(79f, 31f, 1.4f, Rivet); dot(89f, 41f, 1.4f, Rivet)

                dot(44f, 55f, 17f, Socket); drawCircle(IronColors.I600, 17f, Offset(44f, 55f), style = Stroke(2f))
                dot(76f, 55f, 17f, Socket); drawCircle(IronColors.I600, 17f, Offset(76f, 55f), style = Stroke(2f))

                val emberA = (1f - f.granted) * f.glow.coerceIn(0f, 1f)
                val eyeSy = (1f - 0.45f * f.night) * f.blink
                translate(f.eye.x, f.eye.y) {
                    scale(1f, eyeSy, pivot = Offset(60f, 58f)) {
                        if (emberA > 0.01f) {
                            listOf(45f, 75f).forEach { cx ->
                                oval(cx, 58f, 14f, 9.5f, IronColors.Ember.copy(alpha = 0.16f * emberA))
                                oval(cx, 58f, 11f, 7f, IronColors.Ember.copy(alpha = 0.22f * emberA))
                            }
                            oval(45f, 58f, 9f, 5.2f, emberL, emberA)
                            oval(75f, 58f, 9f, 5.2f, emberR, emberA)
                            oval(45f, 58f, 4.6f, 1.4f, Color(0xFFFFF6E2).copy(alpha = emberA))
                            oval(75f, 58f, 4.6f, 1.4f, Color(0xFFFFF6E2).copy(alpha = emberA))
                        }
                        if (f.granted > 0.01f) {
                            val a = f.granted
                            dot(45f, 57f, 7.5f, Amber.copy(alpha = a)); dot(75f, 57f, 7.5f, Amber.copy(alpha = a))
                            dot(45f, 57f, 4f, Pupil.copy(alpha = a)); dot(75f, 57f, 4f, Pupil.copy(alpha = a))
                            dot(47f, 55f, 1.6f, Color.White.copy(alpha = a)); dot(77f, 55f, 1.6f, Color.White.copy(alpha = a))
                        }
                    }
                }

                val browDeg = 5f * f.strain + 7f * f.night - 17f * f.granted
                translate(0f, 2.5f * f.night - 3f * f.granted) {
                    rotate(browDeg, pivot = Offset(24f, 42f)) { drawBrow(browL, 24f, 46f, 55.5f, 54.5f, 25f, 39.5f, 56f, 48f, 28f) }
                    rotate(-browDeg, pivot = Offset(96f, 42f)) { drawBrow(browR, 96f, 46f, 64.5f, 54.5f, 95f, 39.5f, 64f, 48f, 92f) }
                }

                drawPath(beak, Color(0xFFE39A2B))
                drawPath(beakShade, Color(0xFFB87414))
                drawPath(beakCap, Color(0xFF9AA5B1))
                oval(49f, 113f, 7f, 4f, IronColors.I400)
                oval(71f, 113f, 7f, 4f, IronColors.I400)
                drawPath(claws, IronColors.I200, style = Stroke(1.4f, cap = StrokeCap.Round))
            }
        }
    }
}

private fun DrawScope.drawBrow(
    plate: Path,
    ex0: Float, ey0: Float, ex1: Float, ey1: Float,
    hx0: Float, hy0: Float, hx1: Float, hy1: Float,
    rivetX: Float,
) {
    drawPath(plate, IronColors.I300)
    drawLine(PlateRing, Offset(ex0, ey0), Offset(ex1, ey1), strokeWidth = 1.4f)
    drawLine(Highlight.copy(alpha = 0.7f), Offset(hx0, hy0), Offset(hx1, hy1), strokeWidth = 1f)
    dot(rivetX, 43f, 1.3f, Rivet)
}

/** Two puffs per ear, rising and fading; [q] is one puff's progress 0..1. */
private fun DrawScope.drawSteam(q: Float) {
    val alpha = if (q < 0.25f) q / 0.25f * 0.75f else 0.75f * (1f - (q - 0.25f) / 0.75f)
    val rise = -22f * q + 6f
    val grow = 0.5f + 1.1f * q
    listOf(30f to 12f, 90f to 12f).forEach { (x, y) -> dot(x, y + rise, 4f * grow, IronColors.I200.copy(alpha = alpha)) }
    val q2 = ((q + 0.18f) % 1f)
    val a2 = if (q2 < 0.25f) q2 / 0.25f * 0.6f else 0.6f * (1f - (q2 - 0.25f) / 0.75f)
    listOf(35f to 6f, 85f to 6f).forEach { (x, y) -> dot(x, y - 22f * q2 + 6f, 3f * (0.5f + 1.1f * q2), IronColors.I200.copy(alpha = a2)) }
}
