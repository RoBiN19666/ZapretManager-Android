package io.github.robin19666.zapretmanager.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Новогоднее оформление: гирлянда над содержимым и медленно падающий снег.
 *
 * Включается само по календарю (1 декабря — 1 марта), см. `HolidayTheme`.
 * Снег рисуется поверх интерфейса, но не перехватывает касания, поэтому кнопки
 * под ним остаются нажимаемыми.
 */
@Composable
fun NewYearGarland(modifier: Modifier = Modifier, compact: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "garland")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2600, easing = LinearEasing)),
        label = "garlandTwinkle",
    )
    val bulbs = listOf(
        Color(0xFFE4572E),
        Color(0xFFF2C14E),
        Color(0xFF4C9F70),
        Color(0xFF4C8DFF),
        Color(0xFFE4572E),
        Color(0xFFF2C14E),
        Color(0xFF4C9F70),
        Color(0xFF4C8DFF),
        Color(0xFFF2C14E),
    )
    // Компактный вариант — для диалогов: шнур ниже, лампочки мельче.
    val height = if (compact) 16.dp else 26.dp
    val bulbRadius = if (compact) 1.5.dp else 2.3.dp
    val glowRadius = if (compact) 3.6.dp else 5.5.dp
    val dip = if (compact) 2.4.dp else 5.dp
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val width = size.width
        val bottom = size.height - (if (compact) 2.dp.toPx() else 4.dp.toPx())
        val rope = Path().apply {
            moveTo(0f, bottom - 2.dp.toPx())
            quadraticTo(width / 2f, bottom + dip.toPx(), width, bottom - 2.dp.toPx())
        }
        drawPath(
            rope,
            color = Color(0xFF4A5A4A),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 1.dp.toPx(),
                cap = StrokeCap.Round,
            ),
        )
        val visible = if (compact) bulbs.take(7) else bulbs
        visible.forEachIndexed { index, color ->
            val t = (index + 0.5f) / visible.size
            val x = width * t
            // Точка на квадратичной кривой: та же дуга, что у шнура.
            val y = bottom - 2.dp.toPx() + 2f * dip.toPx() * t * (1f - t)
            val twinkle = 0.55f + 0.45f * sin((phase * 2f * PI.toFloat()) + index * 0.9f)
            drawCircle(color.copy(alpha = 0.22f * twinkle), radius = glowRadius.toPx(), center = Offset(x, y))
            drawCircle(color.copy(alpha = 0.85f * twinkle), radius = bulbRadius.toPx(), center = Offset(x, y))
        }
    }
}

@Composable
fun NewYearSnow(modifier: Modifier = Modifier) {
    // Собственное «время» вместо infinite transition: у transition есть шов в конце
    // цикла (снежинки прыгают), а здесь каждая летит непрерывно и сама
    // перезаходит сверху, выйдя за нижнюю кромку.
    var seconds by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val started = withFrameMillis { it }
        while (true) {
            withFrameMillis { now -> seconds = (now - started) / 1000f }
        }
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        val height = size.height
        val width = size.width
        val count = 30
        repeat(count) { index ->
            val seed = (index * 0.618f) % 1f
            val speed = 26f + (index % 5) * 9f // dp в секунду
            val x0 = ((seed * 7.13f) % 1f) * width
            val travel = (seed * height + seconds * speed * density) % (height + 30.dp.toPx())
            val y = travel - 15.dp.toPx()
            val sway = sin(seconds * 0.7f + index) * (6 + (index % 4) * 4).dp.toPx()
            val radius = 0.8.dp.toPx() * (0.7f + (index % 4) * 0.25f)
            val alpha = 0.16f + (index % 3) * 0.1f
            drawCircle(Color.White.copy(alpha = alpha), radius, Offset(x0 + sway, y))
        }

        // Намет на верхней кромке меню: сначала растёт, потом держится на пределе.
        val pile = (seconds * 0.18f * density).coerceAtMost(8.dp.toPx())
        if (pile > 0.5f) {
            val path = Path().apply {
                moveTo(0f, height)
                lineTo(0f, height - pile * 0.55f)
                var x = 0f
                var bump = 0
                while (x < width) {
                    val step = (width / 9f) * (0.8f + ((bump * 37) % 10) / 25f)
                    val top = height - pile * (0.55f + 0.45f * ((bump * 53) % 10) / 10f)
                    quadraticTo(x + step / 2f, top, x + step, height - pile * 0.5f)
                    x += step
                    bump++
                }
                lineTo(width, height)
                close()
            }
            drawPath(path, color = Color(0xFFF2F8FF).copy(alpha = 0.85f))
            drawPath(
                path,
                color = Color(0xFFCFE0F2).copy(alpha = 0.5f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.7.dp.toPx()),
            )
        }
    }
}

@Composable
fun NewYearTree(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "tree")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2200, easing = LinearEasing)),
        label = "treeTwinkle",
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f

        // Снег под ёлкой.
        drawOval(
            Color.White.copy(alpha = 0.55f),
            topLeft = Offset(w * 0.12f, h * 0.93f),
            size = Size(w * 0.76f, h * 0.06f),
        )
        // Ствол.
        drawRect(
            Color(0xFF6B4A2B),
            topLeft = Offset(w * 0.45f, h * 0.78f),
            size = Size(w * 0.10f, h * 0.16f),
        )

        // Ярусы: снизу вверх, чтобы верхние перекрывали нижние.
        data class Tier(val apexY: Float, val baseY: Float, val half: Float, val color: Color)
        val tiers = listOf(
            Tier(0.44f, 0.80f, 0.46f, Color(0xFF3D9666)),
            Tier(0.26f, 0.62f, 0.34f, Color(0xFF35895A)),
            Tier(0.10f, 0.40f, 0.23f, Color(0xFF2E7D50)),
        )
        tiers.forEach { tier ->
            drawPath(
                Path().apply {
                    moveTo(cx, h * tier.apexY)
                    lineTo(cx + w * tier.half, h * tier.baseY)
                    lineTo(cx - w * tier.half, h * tier.baseY)
                    close()
                },
                color = tier.color,
            )
            // Снег лежит на нижней кромке яруса.
            drawLine(
                color = Color.White.copy(alpha = 0.9f),
                start = Offset(cx - w * tier.half, h * tier.baseY),
                end = Offset(cx + w * tier.half, h * tier.baseY),
                strokeWidth = (h * 0.022f).coerceAtLeast(1.2f),
                cap = StrokeCap.Round,
            )
        }

        // Игрушки: каждая мигает в своей фазе.
        val toys = listOf(
            Triple(0.50f, 0.55f, Color(0xFFE4572E)),
            Triple(0.34f, 0.66f, Color(0xFFF2C14E)),
            Triple(0.66f, 0.68f, Color(0xFF4C8DFF)),
            Triple(0.44f, 0.74f, Color(0xFFF2C14E)),
            Triple(0.60f, 0.79f, Color(0xFFE4572E)),
            Triple(0.50f, 0.33f, Color(0xFFF4F9FF)),
            Triple(0.38f, 0.45f, Color(0xFF4C8DFF)),
        )
        toys.forEachIndexed { index, toy ->
            val (x, y, color) = toy
            val twinkle = 0.45f + 0.55f * sin(phase * 2f * PI.toFloat() + index * 1.3f)
            val radius = (h * 0.028f).coerceAtLeast(1.2f)
            drawCircle(color.copy(alpha = 0.25f * twinkle), radius * 2.2f, Offset(w * x, h * y))
            drawCircle(color.copy(alpha = 0.55f + 0.45f * twinkle), radius, Offset(w * x, h * y))
        }

        // Звезда на макушке.
        val starCenter = Offset(cx, h * 0.08f)
        val glow = 0.6f + 0.4f * sin(phase * 2f * PI.toFloat())
        drawCircle(Color(0xFFF2C14E).copy(alpha = 0.22f * glow), h * 0.10f, starCenter)
        drawPath(starPath(starCenter, h * 0.075f), color = Color(0xFFF2C14E))
        drawCircle(Color(0xFFFFF3C4), h * 0.02f, starCenter)
    }
}

/** Пятиконечная звезда. */
private fun starPath(center: Offset, radius: Float): Path = Path().apply {
    val points = 5
    for (i in 0 until points * 2) {
        val angle = -PI / 2 + i * PI / points
        val r = if (i % 2 == 0) radius else radius * 0.45f
        val x = center.x + (r * kotlin.math.cos(angle)).toFloat()
        val y = center.y + (r * sin(angle)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/**
 * Снег на кнопке в плоском 2D-стиле: полоса по верхней кромке (закрывает её целиком,
 * чтобы кнопка не выглядывала), нависы через боковые грани и ряд сосулек.
 *
 * Все три части сливаются в один силуэт через `Path.op(Union)` — поэтому на стыке
 * полосы и боковых нависов нет шва. Рисуется в полосе, выровненной по верхнему краю
 * кнопки: `y = 0` — её кромка. `seed` делает рисунок разным у разных кнопок.
 */
@Composable
fun ButtonSnow(seed: Int = 0, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val unit = 1.dp.toPx()

        fun noise(i: Int): Float {
            val raw = sin((seed + i * 131) * 12.9898f) * 43758.5453f
            return raw - kotlin.math.floor(raw)
        }

        val top = -3.4f * unit
        val bottom = 8.2f * unit
        val left = -1f * unit
        val right = w + 1f * unit

        // Полоса вдоль кромки на всю ширину — верхний край кнопки полностью под снегом.
        val band = Path().apply {
            moveTo(left, bottom)
            lineTo(left, top)
            var x = left
            var i = 0
            while (x < right) {
                val step = (10f + noise(i) * 16f) * unit
                val peak = top - (1.0f + noise(i + 40) * 2.2f) * unit
                val endX = minOf(x + step, right)
                quadraticTo((x + endX) / 2f, peak, endX, top)
                x = endX
                i++
            }
            lineTo(right, bottom)
            var bx = right
            var j = 0
            while (bx > left) {
                val step = (14f + noise(j + 70) * 18f) * unit
                val dip = bottom + (0.3f + noise(j + 17) * 1.6f) * unit
                val endX = maxOf(bx - step, left)
                quadraticTo((bx + endX) / 2f, dip, endX, bottom)
                bx = endX
                j++
            }
            close()
        }

        // Навис через угол: заходит на полосу (перекрытие), поэтому стык не видно.
        fun sidePath(mirrored: Boolean): Path {
            val origin = if (mirrored) w else 0f
            val sign = if (mirrored) -1f else 1f
            fun x(value: Float) = origin + sign * value * unit
            fun y(value: Float) = value * unit
            return Path().apply {
                moveTo(x(10.0f), y(-2.4f))
                quadraticTo(x(1.0f), y(-5.0f), x(-3.8f), y(-2.2f))
                quadraticTo(x(-7.0f), y(1.8f), x(-6.6f), y(7.4f))
                quadraticTo(x(-6.4f), y(13.2f), x(-5.2f), y(15.8f))
                quadraticTo(x(-3.8f), y(18.2f), x(-2.4f), y(15.2f))
                quadraticTo(x(-1.2f), y(12.2f), x(-1.4f), y(8.0f))
                quadraticTo(x(-1.4f), y(3.4f), x(5.0f), y(3.2f))
                close()
            }
        }

        val combined = Path().apply { op(band, sidePath(false), PathOperation.Union) }
        val merged = Path().apply { op(combined, sidePath(true), PathOperation.Union) }

        // Мягкая тень под всей массой снега.
        val shadow = Path().apply {
            addPath(merged)
            translate(Offset(0f, 1.2f * unit))
        }
        drawPath(shadow, color = Color(0xFF0F1722).copy(alpha = 0.10f))
        drawPath(merged, color = Color(0xFFF7FBFF))
        drawPath(
            merged,
            color = Color(0xFFD6E4F2).copy(alpha = 0.55f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.5f * unit),
        )

        // Сосульки под полосой и две длинные — под боковыми нависами.
        val positions = floatArrayOf(0.13f, 0.28f, 0.42f, 0.56f, 0.70f, 0.85f)
        positions.forEachIndexed { index, position ->
            val cx = w * position
            val length = (3.2f + noise(index + 5) * 4.4f) * unit
            val half = (1.0f + noise(index + 45) * 0.8f) * unit
            drawPath(
                Path().apply {
                    moveTo(cx - half, bottom - 2.6f * unit)
                    lineTo(cx + half, bottom - 2.6f * unit)
                    lineTo(cx, bottom - 1.6f * unit + length)
                    close()
                },
                color = Color(0xFFE7F1FC),
            )
        }
        listOf(-4.4f to 6.4f, (w / unit + 4.4f) to 6.0f).forEach { (offset, length) ->
            val cx = offset * unit
            drawPath(
                Path().apply {
                    moveTo(cx - 1.3f * unit, 13.0f * unit)
                    lineTo(cx + 1.3f * unit, 13.0f * unit)
                    lineTo(cx, 14.0f * unit + length * unit)
                    close()
                },
                color = Color(0xFFE7F1FC),
            )
        }
    }
}
