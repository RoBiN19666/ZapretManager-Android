package io.github.robin19666.zapretmanager.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.robin19666.zapretmanager.ui.theme.ZmColors

/** Пункт нижнего меню. */
internal data class ZmDestination(
    val route: String,
    val label: String,
    val glyph: NavGlyph,
)

/**
 * Нижнее меню «пилюлей», как в Telegram.
 *
 * Иконки рисуются сами (см. [drawNavGlyph]) и анимируются при выборе вкладки:
 * дверь у домика открывается, ползунки едут, самолётик машет крылом,
 * шестерёнка поворачивается. Плашка и подпись размер не меняют — двигается
 * только иконка.
 */
@Composable
internal fun ZmBottomBar(
    items: List<ZmDestination>,
    current: String,
    updateBadge: Boolean,
    onSelect: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(24.dp),
                    ambientColor = Color.Black.copy(alpha = 0.35f),
                    spotColor = Color.Black.copy(alpha = 0.35f),
                )
                .clip(RoundedCornerShape(24.dp))
                .background(ZmColors.surface)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEach { item ->
                ZmBottomBarItem(
                    item = item,
                    selected = item.route == current,
                    showBadge = item.route == "settings" && updateBadge,
                    onSelect = { onSelect(item.route) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ZmBottomBarItem(
    item: ZmDestination,
    selected: Boolean,
    showBadge: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) ZmColors.accent else ZmColors.textSecondary,
        animationSpec = tween(durationMillis = 180),
        label = "navTint",
    )
    val pill by animateColorAsState(
        targetValue = if (selected) ZmColors.accent.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = tween(durationMillis = 180),
        label = "navPill",
    )

    // Лёгкий «прыжок» иконки при выборе и оседание при уходе.
    val pop = remember { Animatable(1f) }
    // Собственная анимация иконки: проигрывается один раз при выборе.
    val glyph = remember { Animatable(0f) }
    var firstRun by remember { mutableStateOf(true) }
    LaunchedEffect(selected) {
        if (firstRun) {
            firstRun = false
            return@LaunchedEffect
        }
        if (selected) {
            pop.animateTo(1.12f, tween(durationMillis = 110, easing = FastOutSlowInEasing))
            pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow))
            glyph.snapTo(0f)
            glyph.animateTo(1f, tween(durationMillis = 900, easing = LinearEasing))
        } else {
            pop.animateTo(0.9f, tween(durationMillis = 100, easing = FastOutSlowInEasing))
            pop.animateTo(1f, tween(durationMillis = 190))
            glyph.snapTo(0f)
        }
    }

    Box(modifier = modifier.height(52.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(18.dp))
                .background(pill),
        )
        Column(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onSelect),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BadgedBox(
                badge = {
                    if (showBadge) Badge()
                },
                modifier = Modifier.graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                },
            ) {
                val pillColor = ZmColors.surface
                Canvas(Modifier.size(24.dp)) {
                    drawNavGlyph(
                        glyph = item.glyph,
                        tint = tint,
                        progress = glyph.value,
                        background = pillColor,
                    )
                }
            }
            Text(
                text = item.label,
                fontSize = 10.sp,
                color = tint,
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
