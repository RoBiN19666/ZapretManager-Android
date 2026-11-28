package io.github.robin19666.zapretmanager.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Иконки нижнего меню со своими анимациями. */
internal enum class NavGlyph { HOME, SLIDERS, PLANE, GEAR }

/**
 * Рисует иконку в квадрате [size]×[size].
 *
 * [progress] — это не «проигрывание», а **состояние**: 0 — вкладка не выбрана,
 * 1 — выбрана. Поэтому при уходе с вкладки анимация проигрывается в обратном
 * порядке (дверь закрывается, ползунки возвращаются, шестерёнка откручивается
 * назад).
 */
internal fun DrawScope.drawNavGlyph(
    glyph: NavGlyph,
    tint: Color,
    progress: Float,
    size: Float = this.size.minDimension,
    background: Color = Color.Transparent,
) {
    val unit = size / 24f
    val p = progress.coerceIn(0f, 1f)
    translate(
        left = (this.size.width - size) / 2f,
        top = (this.size.height - size) / 2f,
    ) {
        when (glyph) {
            NavGlyph.HOME -> drawHome(unit, tint, p, background)
            NavGlyph.SLIDERS -> drawSliders(unit, tint, p)
            NavGlyph.PLANE -> drawPlane(unit, tint, p)
            NavGlyph.GEAR -> drawGear(unit, tint, p, background)
        }
    }
}

private fun DrawScope.drawHome(unit: Float, tint: Color, progress: Float, background: Color) {
    fun u(v: Float) = v * unit

    // Крыша.
    drawPath(
        Path().apply {
            moveTo(u(12f), u(2f))
            lineTo(u(22f), u(10.8f))
            lineTo(u(2f), u(10.8f))
            close()
        },
        color = tint,
    )
    // Корпус.
    drawRoundRect(
        color = tint,
        topLeft = Offset(u(4.6f), u(10.2f)),
        size = Size(u(14.8f), u(11.6f)),
        cornerRadius = CornerRadius(u(1.6f), u(1.6f)),
    )
    // Дверь: петля слева, при выборе открывается и так остаётся; при уходе — закрывается.
    val doorTop = u(13.4f)
    val doorH = u(21.8f) - doorTop
    val doorW = u(5.2f) * (1f - 0.72f * progress)
    drawRoundRect(
        color = background,
        topLeft = Offset(u(9.6f), doorTop),
        size = Size(u(5.2f), doorH),
        cornerRadius = CornerRadius(u(0.9f), u(0.9f)),
    )
    drawRoundRect(
        color = tint.copy(alpha = 0.62f),
        topLeft = Offset(u(9.6f), doorTop),
        size = Size(doorW, doorH),
        cornerRadius = CornerRadius(u(0.9f), u(0.9f)),
    )
}

private fun DrawScope.drawSliders(unit: Float, tint: Color, progress: Float) {
    fun u(v: Float) = v * unit
    val rows = listOf(6.4f, 12f, 17.6f)
    rows.forEachIndexed { index, y ->
        drawLine(
            color = tint.copy(alpha = 0.85f),
            start = Offset(u(3.2f), u(y)),
            end = Offset(u(20.8f), u(y)),
            strokeWidth = u(2.2f),
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        val base = when (index) {
            0 -> 8.6f
            1 -> 15.2f
            else -> 10.4f
        }
        // В выбранном состоянии бегунки стоят разведёнными, при уходе возвращаются.
        val shift = if (index % 2 == 0) 2.4f else -2.4f
        val knobX = base + shift * progress
        drawCircle(color = tint, radius = u(2.5f), center = Offset(u(knobX), u(y)))
    }
}

/** Бумажный самолётик: машут оба крыла. */
private fun DrawScope.drawPlane(unit: Float, tint: Color, progress: Float) {
    fun u(v: Float) = v * unit
    // Машут оба крыла: их кончики приподнимаются, пока вкладка выбрана.
    val lift = progress * 2.4f

    // Левое (верхнее) крыло, чуть приглушённое.
    drawPath(
        Path().apply {
            moveTo(u(21.8f), u(2.6f))
            lineTo(u(10.6f), u(13f))
            lineTo(u(2.2f), u(9.2f) - u(lift * 0.75f))
            close()
        },
        color = tint.copy(alpha = 0.7f),
    )
    // Правое (нижнее) крыло.
    drawPath(
        Path().apply {
            moveTo(u(21.8f), u(2.6f))
            lineTo(u(15.2f), u(21.4f) - u(lift))
            lineTo(u(10.6f), u(13f))
            close()
        },
        color = tint,
    )
}

private fun DrawScope.drawGear(unit: Float, tint: Color, progress: Float, background: Color) {
    fun u(v: Float) = v * unit
    val angle = progress * 30f
    rotate(degrees = angle, pivot = Offset(u(12f), u(12f))) {
        // Шесть крупных скруглённых зубцов, вросших в тело.
        repeat(6) { index ->
            rotate(degrees = index * 60f, pivot = Offset(u(12f), u(12f))) {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(u(9.4f), u(1.9f)),
                    size = Size(u(5.2f), u(5.6f)),
                    cornerRadius = CornerRadius(u(2.4f), u(2.4f)),
                )
            }
        }
        drawCircle(color = tint, radius = u(7.6f), center = Offset(u(12f), u(12f)))
        drawCircle(color = background, radius = u(3.1f), center = Offset(u(12f), u(12f)))
        drawCircle(
            color = tint.copy(alpha = 0.8f),
            radius = u(3.1f),
            center = Offset(u(12f), u(12f)),
            style = Stroke(width = u(1.1f)),
        )
    }
}
