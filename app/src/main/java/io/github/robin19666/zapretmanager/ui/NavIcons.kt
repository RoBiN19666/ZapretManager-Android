package io.github.robin19666.zapretmanager.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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
 * Рисует иконку в квадрате [size]×[size] (по умолчанию 24×24 dp).
 *
 * [progress] — 0..1: 0 — покой, 1 — конец проигрывания анимации. Анимация
 * проигрывается один раз при выборе вкладки:
 *  * домик — дверь открывается и закрывается;
 *  * ползунки — бегунки едут влево-вправо;
 *  * самолётик — машет крылом;
 *  * шестерёнка — поворачивается туда-обратно.
 *
 * [background] нужен для «вырезания» просветов (дверной проём, отверстие шестерёнки).
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

/** Домик с открывающейся дверью. */
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
    // Дверь: петля слева. Проём всегда тёмный, створка приоткрывается и закрывается.
    val open = sin(PI.toFloat() * progress) // 0 → 1 → 0
    val doorTop = u(13.4f)
    val doorH = u(21.8f) - doorTop
    val doorW = u(5.2f) * (1f - 0.7f * open)
    drawRoundRect(
        color = background,
        topLeft = Offset(u(9.6f), doorTop),
        size = Size(u(5.2f), doorH),
        cornerRadius = CornerRadius(u(0.9f), u(0.9f)),
    )
    drawRoundRect(
        color = tint.copy(alpha = 0.55f),
        topLeft = Offset(u(9.6f), doorTop),
        size = Size(doorW, doorH),
        cornerRadius = CornerRadius(u(0.9f), u(0.9f)),
    )
}

/** Ползунки: три линии с бегунками, которые едут в стороны. */
private fun DrawScope.drawSliders(unit: Float, tint: Color, progress: Float) {
    fun u(v: Float) = v * unit
    val rows = listOf(6.4f, 12f, 17.6f)
    val phase = sin(progress * 2f * PI.toFloat())
    rows.forEachIndexed { index, y ->
        drawLine(
            color = tint.copy(alpha = 0.85f),
            start = Offset(u(3.2f), u(y)),
            end = Offset(u(20.8f), u(y)),
            strokeWidth = u(2.2f),
            cap = StrokeCap.Round,
        )
        val base = when (index) {
            0 -> 8.6f
            1 -> 15.2f
            else -> 10.4f
        }
        val direction = if (index % 2 == 0) 1f else -1f
        val knobX = base + direction * phase * 2.6f
        drawCircle(color = tint, radius = u(2.5f), center = Offset(u(knobX), u(y)))
    }
}

/** Самолётик, машущий крылом. */
private fun DrawScope.drawPlane(unit: Float, tint: Color, progress: Float) {
    fun u(v: Float) = v * unit
    val flap = sin(progress * 2f * PI.toFloat())

    // Корпус «бумажного» самолётика: нос справа.
    drawPath(
        Path().apply {
            moveTo(u(21.6f), u(11.4f))
            lineTo(u(2.4f), u(3.4f))
            lineTo(u(6.6f), u(20.6f))
            lineTo(u(12.4f), u(14.6f))
            close()
        },
        color = tint,
    )
    // Нижнее крыло поднимается и опускается.
    val lift = flap * 3.2f
    drawPath(
        Path().apply {
            moveTo(u(12.4f), u(14.6f))
            lineTo(u(6.6f), u(20.6f))
            lineTo(u(14.4f), u(15.4f) - u(lift))
            close()
        },
        color = tint.copy(alpha = 0.7f),
    )
    // Линия сгиба крыла.
    drawLine(
        color = tint,
        start = Offset(u(14.2f), u(12.8f) - u(lift * 0.35f)),
        end = Offset(u(7.6f), u(16.2f)),
        strokeWidth = u(1.4f),
        cap = StrokeCap.Round,
    )
}

/** Шестерёнка: поворачивается туда-обратно. */
private fun DrawScope.drawGear(unit: Float, tint: Color, progress: Float, background: Color) {
    fun u(v: Float) = v * unit
    val angle = sin(progress * 2f * PI.toFloat()) * 26f
    rotate(degrees = angle, pivot = Offset(u(12f), u(12f))) {
        repeat(8) { index ->
            rotate(degrees = index * 45f, pivot = Offset(u(12f), u(12f))) {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(u(10.3f), u(1.2f)),
                    size = Size(u(3.4f), u(4.4f)),
                    cornerRadius = CornerRadius(u(1.2f), u(1.2f)),
                )
            }
        }
        drawCircle(color = tint, radius = u(6.8f), center = Offset(u(12f), u(12f)))
        drawCircle(color = background, radius = u(2.9f), center = Offset(u(12f), u(12f)))
        drawCircle(
            color = tint.copy(alpha = 0.9f),
            radius = u(2.9f),
            center = Offset(u(12f), u(12f)),
            style = Stroke(width = u(1.2f)),
        )
    }
}
