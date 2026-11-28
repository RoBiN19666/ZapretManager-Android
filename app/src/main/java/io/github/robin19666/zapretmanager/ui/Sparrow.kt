package io.github.robin19666.zapretmanager.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.robin19666.zapretmanager.core.Chirp
import io.github.robin19666.zapretmanager.ui.theme.LocalNewYear
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * Воробушек для блока «На хлебушек».
 *
 * Клюёт хлебные крошки (они подпрыгивают в момент клевка), убегает, разворачивается
 * и возвращается — и так по кругу. Цикл замкнут: в конце птица стоит в той же позе,
 * что и в начале, поэтому шва между итерациями не видно. Рисуется на Canvas, без
 * картинок и внешних ассетов.
 *
 * По нажатию птица подпрыгивает с оборотом и «чирикает» (если звук мультимедиа
 * включён). С декабря по март к этому добавляется снежная шапка и шарфик.
 */
@Composable
fun SparrowPecking(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val snow = LocalNewYear.current
    val scope = rememberCoroutineScope()
    val boost = remember { Animatable(0f) }

    val transition = rememberInfiniteTransition(label = "sparrow")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 7000, easing = LinearEasing)),
        label = "sparrowTime",
    )

    val tap: () -> Unit = {
        Chirp.play(context)
        onClick?.invoke()
        scope.launch {
            boost.snapTo(0f)
            boost.animateTo(1f, tween(durationMillis = 1600, easing = LinearEasing))
            boost.snapTo(0f)
        }
    }

    Canvas(
        modifier = modifier
            .size(width = 48.dp, height = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = tap),
    ) {
        val unit = size.height / 40f
        val pose = sparrowPose(t, boost.value)

        // Земля и мягкая тень под птицей.
        drawLine(
            color = GROUND.copy(alpha = 0.55f),
            start = Offset(3f * unit, 38f * unit),
            end = Offset(size.width - 3f * unit, 38f * unit),
            strokeWidth = 0.6f * unit,
        )
        if (snow) {
            // Снежные шапки на земле.
            drawOval(SNOW.copy(alpha = 0.35f), Offset(4f * unit, 36.4f * unit), Size(9f * unit, 2.2f * unit))
            drawOval(SNOW.copy(alpha = 0.25f), Offset(26f * unit, 36.8f * unit), Size(7f * unit, 1.8f * unit))
        }
        val originX = (16.5f + pose.x) * unit
        val originY = (32.2f - pose.hop - pose.boostHop) * unit
        drawOval(
            color = Color.Black.copy(alpha = 0.22f * (1f - pose.boostHop / 12f)),
            topLeft = Offset(originX - 8.6f * unit * (1f - pose.hop / 16f), 37.9f * unit - 1.3f * unit),
            size = Size(17.2f * unit * (1f - pose.hop / 16f), 2.6f * unit),
        )

        // Птица. Наклон при клевке — вокруг лап, как у настоящего воробья.
        withTransform({
            translate(originX, originY)
            scale(scaleX = unit * pose.facing, scaleY = unit, pivot = Offset.Zero)
            rotate(degrees = pose.tilt, pivot = Offset(0f, 5.5f))
            if (pose.spin != 0f) rotate(degrees = pose.spin, pivot = Offset(0f, 0f))
        }) {
            drawSparrow(pose, snow)
        }

        // Крошки рисуем поверх птицы: они лежат на земле перед ней.
        sparrowCrumbs(t).forEach { crumb ->
            withTransform({
                translate((16.5f + crumb.x) * unit, (37.6f + crumb.y) * unit - crumb.lift * unit)
                rotate(degrees = crumb.rotation, pivot = Offset.Zero)
            }) {
                drawCrumb(crumb.size * unit, crumb.shade)
            }
        }

        // Секретное нажатие: искры над птицей.
        if (pose.boost > 0f) {
            drawBoostSparks(pose.boost, originX, originY, unit)
        }
    }
}

private class SparrowPose(
    val x: Float,
    val hop: Float,
    val facing: Float,
    val dip: Float,
    val beak: Float,
    val gait: Float,
    val running: Boolean,
    val wing: Float,
    val tail: Float,
    val squash: Float,
    val tilt: Float,
    val boost: Float = 0f,
    val boostHop: Float = 0f,
    val spin: Float = 0f,
)

private class Crumb(
    val x: Float,
    val y: Float,
    val size: Float,
    val lift: Float,
    val rotation: Float,
    val shade: Boolean,
)

/**
 * Таймлайн цикла (доля от периода):
 * 0.00–0.50 — три клевка, 0.50–0.56 — встряхивается, 0.56–0.72 — бежит вправо,
 * 0.72–0.78 — разворот, 0.78–0.94 — бежит обратно, 0.94–1.00 — встаёт в исходную позу.
 */
private fun sparrowPose(t: Float, boost: Float): SparrowPose {
    var x = 0f
    var hop = 0f
    var facing = 1f
    var dip = 0f
    var beak = 0f
    var gait = 0f
    var running = false
    var wing = 0f
    var tail = 0f
    var squash = 0f
    var tilt = 0f

    when {
        t < 0.50f -> {
            val cycle = 0.50f / 3f
            val u = (t % cycle) / cycle
            if (u < 0.55f) dip = sin(u / 0.55f * PI.toFloat())
            beak = (dip * 1.7f).coerceAtMost(1f)
            tail = sin(t * 6f * PI.toFloat()) * 0.35f
            tilt = dip * 20f
        }
        t < 0.56f -> {
            val p = (t - 0.50f) / 0.06f
            hop = sin(p * PI.toFloat()) * 1.4f
            tail = 0.8f
        }
        t < 0.72f -> {
            val p = (t - 0.56f) / 0.16f
            x = ease(p) * 10f
            hop = abs(sin(p * PI.toFloat() * 3f)) * 3f
            running = true
            gait = p * 3f
            wing = p
            tilt = -3.5f
        }
        t < 0.78f -> {
            val p = (t - 0.72f) / 0.06f
            x = 10f
            facing = -1f
            squash = sin(p * PI.toFloat()) * 0.16f
            tail = 1f
        }
        t < 0.94f -> {
            val p = (t - 0.78f) / 0.16f
            x = 10f - ease(p) * 10f
            hop = abs(sin(p * PI.toFloat() * 3f)) * 3f
            running = true
            facing = -1f
            gait = p * 3f
            wing = 1f - p
            tilt = -3.5f
        }
        else -> {
            val p = (t - 0.94f) / 0.06f
            facing = 1f
            hop = sin(p * PI.toFloat()) * 1.1f
            tail = sin(p * PI.toFloat()) * 0.6f
        }
    }

    // Секретное нажатие: подскок с оборотом вокруг своей оси.
    val boostHop = sin(boost * PI.toFloat()) * 9f
    val spin = boost * 360f
    return SparrowPose(
        x, hop, facing, dip, beak, gait, running, wing, tail, squash, tilt,
        boost = boost, boostHop = boostHop, spin = spin,
    )
}

/** Крошки под клювом; в момент клевка каждая подпрыгивает и проворачивается. */
private fun sparrowCrumbs(t: Float): List<Crumb> {
    val base = listOf(
        5.2f to 0.2f to 1.15f, 6.4f to -0.35f to 0.8f, 7.1f to 0.45f to 1.3f,
        8.2f to -0.15f to 0.95f, 9.0f to 0.5f to 0.72f, 9.8f to -0.3f to 1.05f,
        10.9f to 0.3f to 0.85f, 12.0f to -0.2f to 1.2f, 13.2f to 0.25f to 0.75f,
    )
    return base.mapIndexed { index, item ->
        val (pos, size) = item
        val (cx, dy) = pos
        var lift = 0f
        var drift = 0f
        if (t < 0.50f) {
            val cycle = 0.50f / 3f
            val u = (t % cycle) / cycle
            if (u >= 0.48f) {
                val v = (u - 0.48f) / 0.47f
                if (v <= 1f) {
                    val weight = 0.55f + 0.45f * ((index * 5) % 4) / 3f
                    lift = sin(v * PI.toFloat()) * 3.4f * weight
                    drift = (if (index % 2 == 0) 1f else -1f) * v * (0.5f + 0.5f * (index % 3))
                }
            }
        }
        Crumb(
            x = cx + drift,
            y = dy,
            size = size,
            lift = lift,
            rotation = drift * 26f,
            shade = index % 3 == 0,
        )
    }
}

private fun ease(p: Float): Float {
    val x = p.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun DrawScope.drawSparrow(pose: SparrowPose, snow: Boolean) {
    val dip = pose.dip
    val headY = -6.1f + dip * 7.4f
    val headX = 6.6f + dip * 1.4f
    val squash = 1f - pose.squash

    // Лапы: бедро, голень и три пальца. При беге шагают по очереди.
    for (i in 0..1) {
        val baseX = if (i == 0) -1.8f else 2.2f
        var lift = 0f
        var step = 0f
        if (pose.running) {
            val phase = (pose.gait + i * 0.5f) % 1f
            lift = (sin(phase * 2f * PI.toFloat())).coerceAtLeast(0f) * 2.6f
            step = cos(phase * 2f * PI.toFloat()) * 2.2f
        }
        val footX = baseX + step
        val footY = 5.5f - lift
        val kneeX = baseX + step * 0.3f
        val kneeY = 1.9f - lift * 0.5f

        drawLine(BEAK_DARK, Offset(baseX, -0.8f), Offset(kneeX, kneeY), 1.3f, StrokeCap.Round)
        drawLine(BEAK, Offset(kneeX, kneeY), Offset(footX, footY), 1.05f, StrokeCap.Round)
        drawLine(BEAK, Offset(footX, footY), Offset(footX + 1.7f, footY + 0.35f), 0.95f, StrokeCap.Round)
        drawLine(BEAK, Offset(footX, footY), Offset(footX + 1.1f, footY + 1.0f), 0.85f, StrokeCap.Round)
        drawLine(BEAK, Offset(footX, footY), Offset(footX - 1.0f, footY + 0.55f), 0.85f, StrokeCap.Round)
    }

    // Хвост из трёх перьев, слегка подрагивает.
    val flick = pose.tail * 1.4f
    val tailYs = floatArrayOf(-3.4f, -1.3f, 0.7f)
    tailYs.forEachIndexed { index, dy ->
        val tipX = -12.8f - abs(index - 1) * 0.7f
        drawPath(
            Path().apply {
                moveTo(-7.0f, -1.4f)
                lineTo(tipX, dy - 1.2f + flick * 0.4f)
                lineTo(-11.7f, dy + 1.4f + flick * 0.4f)
                close()
            },
            color = BODY_DARK,
        )
    }

    // Тело: спинка, светлое брюшко и мягкий блик.
    drawOval(BODY, Offset(-8.2f * squash, -0.5f - 5.5f / squash), Size(16.4f * squash, 11f / squash))
    drawOval(LIGHT, Offset(0.9f - 5.4f * squash, 1.7f - 2.9f), Size(10.8f * squash, 5.8f))
    drawOval(BODY_LIGHT.copy(alpha = 0.55f), Offset(-6.8f, -5.4f), Size(8.4f, 6f))

    // Крыло с парой перьев.
    val wingY = -1.4f - pose.wing * 1f
    drawOval(BODY_DARK, Offset(-5.5f, wingY - 2.7f), Size(8.6f, 5.4f))
    for (k in 0..1) {
        val y = wingY + 0.3f + k * 1.2f
        drawLine(BODY.copy(alpha = 0.7f), Offset(-4.2f, y), Offset(2.2f, y + 0.45f), 0.5f, StrokeCap.Round)
    }

    // Голова, щечка, клюв и глаз с бликом.
    drawCircle(BODY, 4.0f, Offset(headX, headY))
    drawCircle(LIGHT, 2.0f, Offset(headX + 0.9f, headY + 1.1f))

    val open = pose.beak * 1f
    drawPath(
        Path().apply {
            moveTo(headX + 3.3f, headY - 1.3f)
            lineTo(headX + 6.6f, headY - 0.4f - open * 0.5f)
            lineTo(headX + 3.3f, headY + 0.1f)
            close()
        },
        color = BEAK,
    )
    drawPath(
        Path().apply {
            moveTo(headX + 3.3f, headY + 0.15f)
            lineTo(headX + 6.1f, headY + 0.25f + open * 0.8f)
            lineTo(headX + 3.3f, headY + 1.1f)
            close()
        },
        color = BEAK_DARK,
    )

    drawCircle(DARK, 0.95f, Offset(headX + 1.5f, headY - 1.3f))
    drawCircle(Color.White.copy(alpha = 0.85f), 0.32f, Offset(headX + 1.8f, headY - 1.6f))

    if (snow) {
        // Снежная шапка: на голове, на спинке и на хвосте.
        drawOval(SNOW, Offset(headX - 3.4f, headY - 5.0f), Size(7.2f, 2.4f))
        drawOval(SNOW.copy(alpha = 0.9f), Offset(-6.4f, -6.1f), Size(9.0f, 1.9f))
        drawCircle(SNOW, 1.0f, Offset(-12.4f, -2.2f))
        // Тёплый шарфик на шее.
        drawPath(
            Path().apply {
                moveTo(headX - 2.6f, headY + 2.4f)
                lineTo(headX + 1.6f, headY + 3.0f)
                lineTo(headX + 1.2f, headY + 5.2f)
                lineTo(headX - 3.0f, headY + 4.4f)
                close()
            },
            color = SCARF,
        )
        drawPath(
            Path().apply {
                moveTo(headX - 2.8f, headY + 4.4f)
                lineTo(headX - 5.2f, headY + 6.2f)
                lineTo(headX - 3.6f, headY + 6.6f)
                lineTo(headX - 2.0f, headY + 5.0f)
                close()
            },
            color = SCARF_DARK,
        )
    }
}

/** Искры после нажатия: три звёздочки разлетаются вверх и гаснут. */
private fun DrawScope.drawBoostSparks(boost: Float, originX: Float, originY: Float, unit: Float) {
    val alpha = (1f - boost).coerceIn(0f, 1f)
    val rise = boost * 16f
    val spread = 9f + boost * 7f
    val points = listOf(-0.7f, 0f, 0.7f)
    points.forEachIndexed { index, factor ->
        val cx = originX + factor * spread * unit
        val cy = originY - (6f + rise + index * 2.2f) * unit
        val size = (1.5f - boost * 0.7f).coerceAtLeast(0.6f) * unit
        val color = if (index == 1) ACCENT_SPARK else SNOW
        drawPath(star(cx, cy, size), color = color.copy(alpha = alpha * 0.9f))
    }
}

private fun star(cx: Float, cy: Float, radius: Float): Path = Path().apply {
    moveTo(cx, cy - radius)
    lineTo(cx + radius * 0.35f, cy - radius * 0.35f)
    lineTo(cx + radius, cy)
    lineTo(cx + radius * 0.35f, cy + radius * 0.35f)
    lineTo(cx, cy + radius)
    lineTo(cx - radius * 0.35f, cy + radius * 0.35f)
    lineTo(cx - radius, cy)
    lineTo(cx - radius * 0.35f, cy - radius * 0.35f)
    close()
}

/** Одна крошка: неправильный четырёхугольник и тёмная грань. */
private fun DrawScope.drawCrumb(size: Float, shade: Boolean) {
    drawPath(
        Path().apply {
            moveTo(-size, -size * 0.45f)
            lineTo(size, -size * 0.6f)
            lineTo(size * 0.75f, size * 0.55f)
            lineTo(-size * 0.85f, size * 0.45f)
            close()
        },
        color = CRUMB,
    )
    if (shade) {
        drawPath(
            Path().apply {
                moveTo(-size * 0.15f, -size * 0.35f)
                lineTo(size * 0.5f, -size * 0.25f)
                lineTo(size * 0.25f, size * 0.35f)
                close()
            },
            color = CRUMB_DARK.copy(alpha = 0.55f),
        )
    }
}

private val BODY = Color(0xFF9C7A50)
private val BODY_DARK = Color(0xFF7C5F3C)
private val BODY_LIGHT = Color(0xFFB08B5C)
private val LIGHT = Color(0xFFDCC29A)
private val BEAK = Color(0xFFF0A33C)
private val BEAK_DARK = Color(0xFFC97F22)
private val CRUMB = Color(0xFFE7BA79)
private val CRUMB_DARK = Color(0xFFC99353)
private val DARK = Color(0xFF20160B)
private val GROUND = Color(0xFF5A4A36)
private val SNOW = Color(0xFFF4F9FF)
private val SCARF = Color(0xFFD9544D)
private val SCARF_DARK = Color(0xFFA83B36)
private val ACCENT_SPARK = Color(0xFFF2C14E)
