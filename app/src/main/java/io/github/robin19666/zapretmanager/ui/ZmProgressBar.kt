package io.github.robin19666.zapretmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.robin19666.zapretmanager.ui.theme.ZmColors

/**
 * Своя полоса загрузки вместо LinearProgressIndicator.
 *
 * В Material3 1.3 у индикатора появился «стоп-индикатор» — зелёная точка у правого края,
 * которая выглядит как заполненный конец шкалы, пока прогресс ещё нулевой.
 * Здесь простая заливка слева направо без артефактов.
 */
@Composable
fun ZmProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
    val fraction = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(ZmColors.surfaceHigh)
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(ZmColors.accent)
            )
        }
    }
}
