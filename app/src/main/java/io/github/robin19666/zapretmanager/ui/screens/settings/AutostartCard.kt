package io.github.robin19666.zapretmanager.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.robin19666.zapretmanager.core.AppSettings
import io.github.robin19666.zapretmanager.core.Schedule
import io.github.robin19666.zapretmanager.ui.SectionTitle
import io.github.robin19666.zapretmanager.ui.SwitchRow
import io.github.robin19666.zapretmanager.ui.ZmCard
import io.github.robin19666.zapretmanager.ui.ZmViewModel
import io.github.robin19666.zapretmanager.ui.theme.ZmColors

/** Раздел «Автозапуск»: загрузка, открытие приложения и расписание. */
@Composable
internal fun AutostartCard(settings: AppSettings, vm: ZmViewModel) {
    ZmCard {
        SectionTitle("Автозапуск")
        SwitchRow(
            title = "Ускорение соединения при загрузке",
            subtitle = "Требуется ранее выданное VPN-разрешение",
            checked = settings.autostartDpi,
            onCheckedChange = { value -> vm.updateSettings { it.copy(autostartDpi = value) } },
        )
        SwitchRow(
            title = "Telegram-прокси при загрузке",
            checked = settings.autostartTg,
            onCheckedChange = { value -> vm.updateSettings { it.copy(autostartTg = value) } },
        )
        SwitchRow(
            title = "Ускорение соединения при открытии приложения",
            subtitle = "Если VPN-разрешение уже выдано",
            checked = settings.autostartOnLaunch,
            onCheckedChange = { value -> vm.updateSettings { it.copy(autostartOnLaunch = value) } },
        )
        SwitchRow(
            title = "Telegram-прокси при открытии приложения",
            subtitle = "Сразу поднимать локальный MTProto-прокси",
            checked = settings.autostartTgOnLaunch,
            onCheckedChange = { value -> vm.updateSettings { it.copy(autostartTgOnLaunch = value) } },
        )
        SwitchRow(
            title = "Работать по расписанию",
            subtitle = "Вне окна обход выключается, в окне включается обратно",
            checked = settings.scheduleEnabled,
            onCheckedChange = { value -> vm.updateSettings { it.copy(scheduleEnabled = value) } },
        )
        if (settings.scheduleEnabled) {
            TimeRow(
                title = "Включать с",
                minutes = settings.scheduleFrom,
                onPick = { value -> vm.updateSettings { it.copy(scheduleFrom = value) } },
            )
            TimeRow(
                title = "Выключать до",
                minutes = settings.scheduleTo,
                onPick = { value -> vm.updateSettings { it.copy(scheduleTo = value) } },
            )
            Text(
                if (settings.scheduleFrom == settings.scheduleTo) {
                    "Окно нулевой длины — обход работает всё время."
                } else if (settings.scheduleFrom < settings.scheduleTo) {
                    "Обход работает с ${Schedule.format(settings.scheduleFrom)} до ${Schedule.format(settings.scheduleTo)}."
                } else {
                    "Окно через полночь: с ${Schedule.format(settings.scheduleFrom)} до ${Schedule.format(settings.scheduleTo)}."
                },
                fontSize = 11.sp,
                color = ZmColors.textSecondary,
            )
        }
    }
}

/** Строка с временем: по нажатию открывает системный выбор времени. */
@Composable
private fun TimeRow(
    title: String,
    minutes: Int,
    onPick: (Int) -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                android.app.TimePickerDialog(
                    context,
                    { _, hour, minute -> onPick(Schedule.of(hour, minute)) },
                    minutes / 60,
                    minutes % 60,
                    true,
                ).show()
            }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 13.sp, color = ZmColors.textPrimary)
        Text(Schedule.format(minutes), fontSize = 13.sp, color = ZmColors.accent)
    }
}
