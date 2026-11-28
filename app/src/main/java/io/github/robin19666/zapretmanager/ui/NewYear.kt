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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
    val transition = rememberInfiniteTransition(label = "snow")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 14_000, easing = LinearEasing)),
        label = "snowFall",
    )
    Canvas(modifier = modifier.fillMaxSize()) {
        val count = 34
        repeat(count) { index ->
            val seed = index * 0.618f
            val startX = ((seed * 7.13f) % 1f) * size.width
            val speed = 0.55f + (index % 5) * 0.12f
            val y = ((seed + progress * speed) % 1f) * (size.height + 40.dp.toPx()) - 20.dp.toPx()
            val sway = sin((progress * 2f * PI.toFloat()) + index) * 14.dp.toPx()
            val radius = (1.dp.toPx() * (0.7f + (index % 4) * 0.22f))
            val alpha = 0.18f + (index % 3) * 0.12f
            drawCircle(Color.White.copy(alpha = alpha), radius = radius, center = Offset(startX + sway, y))
        }
    }
}


/**
 * Ёлочка рядом с воробушком: три яруса, снег на ветках, мигающие игрушки
 * и звезда на макушке. Размер задаёт вызывающая сторона.
 */
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

/** Снежная шапка на кнопке: белые бугры по верхней кромке и пара снежинок. */
@Composable
fun ButtonSnow(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val base = 2.4.dp.toPx()
        var x = base * 0.6f
        var index = 0
        while (x < w) {
            // Крупные перекрывающиеся бугры сливаются в снежную шапку.
            val radius = base * (0.8f + (index % 4) * 0.16f)
            val y = 0.7.dp.toPx() + (index % 3) * 0.4.dp.toPx()
            drawCircle(Color.White.copy(alpha = 0.92f), radius, Offset(x, y))
            x += radius * 1.9f
            index++
        }
        drawCircle(Color.White.copy(alpha = 0.45f), 0.8.dp.toPx(), Offset(w * 0.22f, size.height * 0.62f))
        drawCircle(Color.White.copy(alpha = 0.35f), 0.6.dp.toPx(), Offset(w * 0.77f, size.height * 0.74f))
    }
}
