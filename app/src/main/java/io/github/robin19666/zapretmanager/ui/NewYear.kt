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
fun NewYearGarland(modifier: Modifier = Modifier) {
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
    Canvas(modifier = modifier.fillMaxWidth().height(26.dp)) {
        val width = size.width
        val bottom = size.height - 4.dp.toPx()
        val rope = Path().apply {
            moveTo(0f, bottom - 3.dp.toPx())
            quadraticTo(width / 2f, bottom + 5.dp.toPx(), width, bottom - 3.dp.toPx())
        }
        drawPath(
            rope,
            color = Color(0xFF4A5A4A),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 1.2.dp.toPx(),
                cap = StrokeCap.Round,
            ),
        )
        bulbs.forEachIndexed { index, color ->
            val t = (index + 0.5f) / bulbs.size
            val x = width * t
            // Точка на квадратичной кривой: та же дуга, что у шнура.
            val y = bottom - 3.dp.toPx() + 2f * 5.dp.toPx() * t * (1f - t)
            val twinkle = 0.55f + 0.45f * sin((phase * 2f * PI.toFloat()) + index * 0.9f)
            drawCircle(color.copy(alpha = 0.22f * twinkle), radius = 5.5.dp.toPx(), center = Offset(x, y))
            drawCircle(color.copy(alpha = 0.85f * twinkle), radius = 2.3.dp.toPx(), center = Offset(x, y))
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
