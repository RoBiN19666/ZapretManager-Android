package io.github.robin19666.zapretmanager.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Р’РѕСЂРѕР±СѓС€РµРє РґР»СЏ Р±Р»РѕРєР° В«РќР° С…Р»РµР±СѓС€РµРєВ».
 *
 * РљР»СЋС‘С‚ С…Р»РµР±, СѓР±РµРіР°РµС‚ РІРїСЂР°РІРѕ, СЂР°Р·РІРѕСЂР°С‡РёРІР°РµС‚СЃСЏ Рё РІРѕР·РІСЂР°С‰Р°РµС‚СЃСЏ Рє РєРѕСЂРєРµ вЂ” Рё С‚Р°Рє РїРѕ
 * РєСЂСѓРіСѓ. Р¦РёРєР» Р·Р°РјРєРЅСѓС‚: РІ РєРѕРЅС†Рµ РїС‚РёС†Р° СЃС‚РѕРёС‚ РІ С‚РѕР№ Р¶Рµ РїРѕР·Рµ, С‡С‚Рѕ Рё РІ РЅР°С‡Р°Р»Рµ
 * (Сѓ РєРѕСЂРєРё, РјРѕСЂРґРѕР№ РІРїСЂР°РІРѕ), РїРѕСЌС‚РѕРјСѓ С€РІР° РјРµР¶РґСѓ РёС‚РµСЂР°С†РёСЏРјРё РЅРµ РІРёРґРЅРѕ.
 *
 * Р РёСЃСѓРµС‚СЃСЏ РЅР° Canvas, Р±РµР· РєР°СЂС‚РёРЅРѕРє Рё РІРЅРµС€РЅРёС… Р°СЃСЃРµС‚РѕРІ.
 */
@Composable
fun SparrowPecking(
    modifier: Modifier = Modifier,
    bodyColor: Color = Color(0xFF9C7A50),
    lightColor: Color = Color(0xFFD3B688),
    beakColor: Color = Color(0xFFF0A33C),
    breadColor: Color = Color(0xFFD9A05B),
) {
    val transition = rememberInfiniteTransition(label = "sparrow")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 7000, easing = LinearEasing)),
        label = "sparrowTime",
    )

    Canvas(modifier = modifier.size(width = 78.dp, height = 38.dp)) {
        val unit = size.height / 40f

        // 0.00вЂ“0.52 вЂ” РєР»СЋС‘С‚ (С‚СЂРё РєР»РµРІРєР°), 0.52вЂ“0.70 вЂ” СѓР±РµРіР°РµС‚ РІР»РµРІРѕ,
        // 0.70вЂ“0.78 вЂ” СЂР°Р·РІРѕСЂРѕС‚, 0.78вЂ“0.95 вЂ” РІРѕР·РІСЂР°С‰Р°РµС‚СЃСЏ, 0.95вЂ“1.00 вЂ” СЃС‚РѕРёС‚.
        var offsetX = 0f
        var hop = 0f
        var dip = 0f
        var facing = 1f
        val runDistance = 20f
        when {
            t < 0.52f -> {
                val local = t / 0.52f
                val peck = (local * 3f) % 1f
                dip = if (peck < 0.55f) sin(peck / 0.55f * PI.toFloat()) else 0f
            }
            t < 0.70f -> {
                val p = (t - 0.52f) / 0.18f
                offsetX = -ease(p) * runDistance
                hop = abs(sin(p * PI.toFloat() * 3f)) * 5f
                facing = -1f
            }
            t < 0.78f -> {
                offsetX = -runDistance
                facing = 1f
                dip = sin((t - 0.70f) / 0.08f * PI.toFloat()) * 0.25f
            }
            t < 0.95f -> {
                val p = (t - 0.78f) / 0.17f
                offsetX = -runDistance + ease(p) * runDistance
                hop = abs(sin(p * PI.toFloat() * 3f)) * 5f
                facing = 1f
            }
            else -> {
                facing = 1f
            }
        }

        // РҐР»РµР±РЅР°СЏ РєРѕСЂРєР° Рё РєСЂРѕС€РєРё вЂ” РјРёС€РµРЅСЊ, Рє РєРѕС‚РѕСЂРѕР№ РїС‚РёС†Р° РІРѕР·РІСЂР°С‰Р°РµС‚СЃСЏ.
        drawRoundRect(
            color = breadColor,
            topLeft = Offset(46f * unit, 30f * unit),
            size = Size(16f * unit, 8f * unit),
            cornerRadius = CornerRadius(2.5f * unit, 2.5f * unit),
        )
        drawRoundRect(
            color = breadColor.copy(alpha = 0.55f),
            topLeft = Offset(48f * unit, 28.4f * unit),
            size = Size(11f * unit, 3f * unit),
            cornerRadius = CornerRadius(1.5f * unit, 1.5f * unit),
        )
        drawCircle(breadColor.copy(alpha = 0.8f), radius = 1.1f * unit, center = Offset(43f * unit, 36f * unit))
        drawCircle(breadColor.copy(alpha = 0.6f), radius = 0.9f * unit, center = Offset(40.5f * unit, 34f * unit))

        val originX = (38f + offsetX) * unit
        val originY = (30f - hop) * unit
        withTransform({
            translate(originX, originY)
            scale(scaleX = unit * facing, scaleY = unit, pivot = Offset.Zero)
        }) {
            drawSparrow(dip = dip, bodyColor = bodyColor, lightColor = lightColor, beakColor = beakColor)
        }
    }
}

/** РџС‚РёС†Р° РІ Р»РѕРєР°Р»СЊРЅС‹С… РµРґРёРЅРёС†Р°С…: С†РµРЅС‚СЂ С‚РµР»Р° РІ (0,0), РіРѕР»РѕРІР° СЃРїСЂР°РІР°. */
private fun DrawScope.drawSparrow(
    dip: Float,
    bodyColor: Color,
    lightColor: Color,
    beakColor: Color,
) {
    val headY = -6f + dip * 6f

    // РҐРІРѕСЃС‚.
    val tail = Path().apply {
        moveTo(-7f, -1f)
        lineTo(-14f, -4.5f)
        lineTo(-13f, 1.5f)
        close()
    }
    drawPath(tail, color = bodyColor)

    // РўРµР»Рѕ Рё СЃРІРµС‚Р»РѕРµ Р±СЂСЋС€РєРѕ.
    drawOval(color = bodyColor, topLeft = Offset(-8.5f, -6f), size = Size(17f, 11.5f))
    drawOval(color = lightColor, topLeft = Offset(-4f, -1.5f), size = Size(10f, 6.5f))

    // РљСЂС‹Р»Рѕ.
    drawOval(color = bodyColor.copy(alpha = 0.75f), topLeft = Offset(-4.5f, -4.5f), size = Size(8f, 5.5f))

    // Р“РѕР»РѕРІР° Рё С‰РµС‡РєР°.
    drawCircle(color = bodyColor, radius = 4.3f, center = Offset(6.5f, headY))
    drawCircle(color = lightColor, radius = 2.1f, center = Offset(7.4f, headY + 0.9f))

    // РљР»СЋРІ.
    val beak = Path().apply {
        moveTo(10.3f, headY - 1.2f)
        lineTo(13.6f, headY + 0.1f)
        lineTo(10.3f, headY + 1.3f)
        close()
    }
    drawPath(beak, color = beakColor)

    // Р“Р»Р°Р·.
    drawCircle(color = Color(0xFF20160B), radius = 0.9f, center = Offset(8f, headY - 1.5f))

    // Р›Р°РїРєРё.
    drawLine(
        color = beakColor,
        start = Offset(-2f, 5.2f),
        end = Offset(-2f, 8.5f),
        strokeWidth = 1.2f,
    )
    drawLine(
        color = beakColor,
        start = Offset(2f, 5.2f),
        end = Offset(2.6f, 8.5f),
        strokeWidth = 1.2f,
    )
}

/** РџР»Р°РІРЅРѕРµ СѓСЃРєРѕСЂРµРЅРёРµ/Р·Р°РјРµРґР»РµРЅРёРµ РґР»СЏ В«РїСЂРѕР±РµР¶РµРєВ». */
private fun ease(p: Float): Float {
    val x = p.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
