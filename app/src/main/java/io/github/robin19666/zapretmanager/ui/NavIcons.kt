package io.github.robin19666.zapretmanager.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
 * Рисует иконку в квадрате [size]×[size] (по умолчанию — весь холст).
 *
 * Стиль — тонкие линии с круглыми концами, как в референсе: без «жирных» заливок,
 * с деталями (дверь, бегунки, сгиб крыла, зубцы шестерёнки).
 *
 * [progress] — 0..1, анимация проигрывается один раз при выборе вкладки:
 * домик открывает дверь, ползунки едут, самолётик машет крылом, шестерёнка
 * поворачивается.
 */
internal fun DrawScope.drawNavGlyph(
    glyph: NavGlyph,
    tint: Color,
    progress: Float,
    size: Float = this.size.minDimension,
) {
    val unit = size / 24f
    val p = progress.coerceIn(0f, 1f)
    translate(
        left = (this.size.width - size) / 2f,
        top = (this.size.height - size) / 2f,
    ) {
        val width = 1.85f * unit
        val stroke = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(path: Path) = drawPath(path, color = tint, style = stroke)

        when (glyph) {
            NavGlyph.HOME -> {
                // Крыша.
                line(
                    Path().apply {
                        moveTo(unit * 3.2f, unit * 10.6f)
                        lineTo(unit * 12f, unit * 2.9f)
                        lineTo(unit * 20.8f, unit * 10.6f)
                    },
                )
                // Стены.
                line(
                    Path().apply {
                        moveTo(unit * 5.4f, unit * 10.2f)
                        lineTo(unit * 5.4f, unit * 21f)
                        lineTo(unit * 18.6f, unit * 21f)
                        lineTo(unit * 18.6f, unit * 10.2f)
                    },
                )
                // Дверь: петля слева, при открывании сужается и «уходит» вглубь.
                val open = sin(PI.toFloat() * p)
                val doorW = unit * 4.9f * (1f - 0.68f * open)
                line(
                    Path().apply {
                        moveTo(unit * 9.5f, unit * 21f)
                        lineTo(unit * 9.5f, unit * 14.2f)
                        lineTo(unit * 9.5f + doorW, unit * 14.2f)
                        lineTo(unit * 9.5f + doorW, unit * 21f)
                    },
                )
                // Тень открытой створки.
                if (open > 0.08f) {
                    line(
                        Path().apply {
                            moveTo(unit * 9.5f, unit * 14.2f)
                            lineTo(unit * 9.5f + doorW, unit * 15.1f)
                        },
                    )
                }
            }

            NavGlyph.SLIDERS -> {
                val rows = floatArrayOf(6.6f, 12f, 17.4f)
                val phase = sin(p * 2f * PI.toFloat())
                rows.forEachIndexed { index, y ->
                    line(
                        Path().apply {
                            moveTo(unit * 3.2f, unit * y)
                            lineTo(unit * 20.8f, unit * y)
                        },
                    )
                    val base = when (index) {
                        0 -> 8.8f
                        1 -> 15.2f
                        else -> 10.6f
                    }
                    val direction = if (index % 2 == 0) 1f else -1f
                    val knobX = base + direction * phase * 2.4f
                    drawCircle(
                        color = tint,
                        radius = unit * 2.3f,
                        center = Offset(unit * knobX, unit * y),
                        style = Stroke(width = width),
                    )
                    drawCircle(
                        color = tint,
                        radius = unit * 0.7f,
                        center = Offset(unit * knobX, unit * y),
                    )
                }
            }

            NavGlyph.PLANE -> {
                val lift = sin(p * 2f * PI.toFloat()) * 2.4f
                // Верхнее крыло.
                line(
                    Path().apply {
                        moveTo(unit * 21.6f, unit * 11.6f)
                        lineTo(unit * 2.6f, unit * 3.4f)
                        lineTo(unit * 9.2f, unit * 11.6f)
                    },
                )
                // Нижнее крыло машет.
                line(
                    Path().apply {
                        moveTo(unit * 21.6f, unit * 11.6f)
                        lineTo(unit * 9.2f, unit * 11.6f)
                        lineTo(unit * 2.6f, unit * (20.2f - lift))
                    },
                )
                // Линия сгиба.
                line(
                    Path().apply {
                        moveTo(unit * 9.2f, unit * 11.6f)
                        lineTo(unit * 15.4f, unit * 16.6f)
                    },
                )
            }

            NavGlyph.GEAR -> {
                val angle = sin(p * 2f * PI.toFloat()) * 24f
                rotate(degrees = angle, pivot = Offset(unit * 12f, unit * 12f)) {
                    drawCircle(
                        color = tint,
                        radius = unit * 6.1f,
                        center = Offset(unit * 12f, unit * 12f),
                        style = Stroke(width = width),
                    )
                    drawCircle(
                        color = tint,
                        radius = unit * 2.5f,
                        center = Offset(unit * 12f, unit * 12f),
                        style = Stroke(width = width),
                    )
                    repeat(8) { index ->
                        val a = (index * 45f - 90f) * PI.toFloat() / 180f
                        val inner = unit * 5.4f
                        val outer = unit * 8.8f
                        line(
                            Path().apply {
                                moveTo(
                                    unit * 12f + cos(a) * inner,
                                    unit * 12f + sin(a) * inner,
                                )
                                lineTo(
                                    unit * 12f + cos(a) * outer,
                                    unit * 12f + sin(a) * outer,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
