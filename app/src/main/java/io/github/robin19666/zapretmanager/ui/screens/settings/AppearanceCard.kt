package io.github.robin19666.zapretmanager.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.robin19666.zapretmanager.core.AppSettings
import io.github.robin19666.zapretmanager.ui.SectionTitle
import io.github.robin19666.zapretmanager.ui.SwitchRow
import io.github.robin19666.zapretmanager.ui.ZmCard
import io.github.robin19666.zapretmanager.ui.ZmViewModel
import io.github.robin19666.zapretmanager.ui.screens.ACCENT_PALETTE
import io.github.robin19666.zapretmanager.ui.theme.ZmColors

/** Раздел «Оформление»: тема, цвет акцента, фон и AMOLED. */
@Composable
internal fun AppearanceCard(settings: AppSettings, vm: ZmViewModel) {
    ZmCard {
        SectionTitle("Оформление")
        Text("Тема интерфейса", fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "system" to "Как в системе",
                "light" to "Светлая",
                "dark" to "Тёмная",
            ).forEach { (mode, label) ->
                FilterChip(
                    selected = settings.themeMode == mode,
                    onClick = { vm.updateSettings { it.copy(themeMode = mode) } },
                    label = { Text(label, fontSize = 11.sp, maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ZmColors.accent.copy(alpha = 0.18f),
                        selectedLabelColor = ZmColors.accent,
                    ),
                )
            }
        }
        Text("Цвет акцента", fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ACCENT_PALETTE.forEach { (value, label) ->
                ColorDot(
                    color = Color(value),
                    label = label,
                    selected = settings.accentColor == value,
                    onClick = { vm.updateSettings { it.copy(accentColor = value) } },
                )
            }
        }

        Text("Фон", fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val auto = settings.backgroundTint == 0L
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFFFF6B6B),
                                    Color(0xFFFBBF24),
                                    Color(0xFF4ADE80),
                                    Color(0xFF4C8DFF),
                                    Color(0xFFA78BFA),
                                    Color(0xFFF472B6),
                                ),
                            ),
                        )
                        .border(
                            width = if (auto) 3.dp else 1.dp,
                            color = if (auto) ZmColors.textPrimary else ZmColors.divider,
                            shape = CircleShape,
                        )
                        .clickable { vm.updateSettings { it.copy(backgroundTint = 0L) } },
                )
                Text(
                    "Авто",
                    fontSize = 8.sp,
                    color = if (auto) ZmColors.accent else ZmColors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            ACCENT_PALETTE.forEach { (value, label) ->
                ColorDot(
                    color = Color(value),
                    label = label,
                    selected = settings.backgroundTint == value,
                    onClick = { vm.updateSettings { it.copy(backgroundTint = value) } },
                )
            }
        }
        Text(
            "«Авто» — фон подкрашивается цветом кнопок; можно выбрать отдельный цвет фона.",
            fontSize = 10.sp,
            color = ZmColors.textSecondary,
        )

        SwitchRow(
            title = "AMOLED-чёрный фон",
            subtitle = "Чистый чёрный только для тёмной темы",
            checked = settings.amoled,
            onCheckedChange = { value -> vm.updateSettings { it.copy(amoled = value) } },
        )
    }
}

/** Кружок цвета с подписью: используется и для акцента, и для фона. */
@Composable
private fun ColorDot(
    color: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) ZmColors.textPrimary else ZmColors.divider,
                    shape = CircleShape,
                )
                .clickable(onClick = onClick),
        )
        Text(
            label,
            fontSize = 8.sp,
            color = if (selected) ZmColors.accent else ZmColors.textSecondary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
