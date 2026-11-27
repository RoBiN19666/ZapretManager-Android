package io.github.robin19666.zapretmanager.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.robin19666.zapretmanager.BuildConfig
import io.github.robin19666.zapretmanager.R
import io.github.robin19666.zapretmanager.byedpi.Strategies
import io.github.robin19666.zapretmanager.core.AppSettings
import io.github.robin19666.zapretmanager.core.EnginePhase
import io.github.robin19666.zapretmanager.core.LogLine
import io.github.robin19666.zapretmanager.core.Versions
import io.github.robin19666.zapretmanager.tgproxy.TgConfig
import io.github.robin19666.zapretmanager.tgproxy.TgStatus
import io.github.robin19666.zapretmanager.ui.EngineRow
import io.github.robin19666.zapretmanager.ui.GhostButton
import io.github.robin19666.zapretmanager.ui.KeyValueRow
import io.github.robin19666.zapretmanager.ui.PrimaryButton
import io.github.robin19666.zapretmanager.ui.SectionTitle
import io.github.robin19666.zapretmanager.ui.ZmCard
import io.github.robin19666.zapretmanager.ui.ZmViewModel
import io.github.robin19666.zapretmanager.ui.copyToClipboard
import io.github.robin19666.zapretmanager.ui.openUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.robin19666.zapretmanager.ui.theme.ZmColors

@Composable
fun DashboardScreen(
    vm: ZmViewModel,
    dpiPhase: EnginePhase,
    tgPhase: EnginePhase,
    settings: AppSettings,
    tgStatus: TgStatus,
    logs: List<LogLine>,
    appUpdateVersion: String?,
    onToggleDpi: () -> Unit,
    onToggleTg: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val strategy = remember(settings.strategyId, settings.customArgs) {
        Strategies.resolve(context, settings.strategyId)
    }
    val dpiUptime = if (dpiPhase == EnginePhase.RUNNING) Versions.humanDuration(now - vm.dpi.value.since) else ""
    val tgUptime = if (tgPhase == EnginePhase.RUNNING) Versions.humanDuration(now - tgStatus.since) else ""

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.logo_zm),
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Zapret Manager",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Ускорение соединения и Telegram-прокси",
                        style = MaterialTheme.typography.bodySmall,
                        color = ZmColors.textSecondary,
                    )
                }
                Text(
                    "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZmColors.textSecondary,
                )
            }
        }

        if (appUpdateVersion != null) {
            item {
                ZmCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Доступно обновление $appUpdateVersion", color = ZmColors.accent)
                            Text("Менеджер можно обновить из раздела «Обновления»", color = ZmColors.textSecondary, fontSize = 12.sp)
                        }
                        PrimaryButton("Открыть", modifier = Modifier.width(110.dp)) { onNavigate("updates") }
                    }
                }
            }
        }

        item {
            ZmCard {
                EngineRow(
                    title = "Ускорение соединения",
                    subtitle = when (dpiPhase) {
                        EnginePhase.RUNNING -> "«${strategy.title}» · $dpiUptime"
                        EnginePhase.STARTING -> "Запуск движка…"
                        EnginePhase.STOPPING -> "Остановка…"
                        EnginePhase.ERROR -> vm.dpi.value.message.ifBlank { "Ошибка" }
                        EnginePhase.STOPPED -> "Выключен · вся сеть идёт напрямую"
                    },
                    phase = dpiPhase,
                    checked = dpiPhase == EnginePhase.RUNNING || dpiPhase == EnginePhase.STARTING,
                    enabled = dpiPhase != EnginePhase.STOPPING,
                    onCheckedChange = { onToggleDpi() },
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = ZmColors.divider)
                EngineRow(
                    title = "Telegram-прокси",
                    subtitle = when (tgPhase) {
                        EnginePhase.RUNNING -> "127.0.0.1:${settings.tgPort} · $tgUptime" +
                            if (tgStatus.outbound.isNotBlank()) " · ${tgStatus.outbound}" else ""
                        EnginePhase.STARTING -> "Запуск прокси…"
                        EnginePhase.STOPPING -> "Остановка…"
                        EnginePhase.ERROR -> tgStatus.message.ifBlank { "Ошибка" }
                        EnginePhase.STOPPED -> "Локальный MTProto-прокси выключен"
                    },
                    phase = tgPhase,
                    checked = tgPhase == EnginePhase.RUNNING || tgPhase == EnginePhase.STARTING,
                    enabled = tgPhase != EnginePhase.STOPPING,
                    onCheckedChange = { onToggleTg() },
                )
                if (dpiPhase == EnginePhase.ERROR || tgPhase == EnginePhase.ERROR) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = (if (dpiPhase == EnginePhase.ERROR) vm.dpi.value.message else tgStatus.message),
                        color = ZmColors.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (tgStatus.running) {
            item {
                ZmCard {
                    SectionTitle("Подключение Telegram")
                    val link = TgConfig.tgLink(settings.tgPort, TgConfig.ensureSecret(), settings.tgMaskingDomain)
                    Text(
                        link,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = ZmColors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton("Применить", modifier = Modifier.weight(1f)) {
                            context.openUrl(link)
                        }
                        GhostButton("Копировать", modifier = Modifier.weight(1f)) {
                            context.copyToClipboard("MTProto", link)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Откроется Telegram с предложением подключить прокси. Ссылка содержит секрет — не публикуйте её.",
                        fontSize = 11.sp,
                        color = ZmColors.textSecondary,
                    )
                }
            }
        }

        item {
            ZmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SectionTitle("Текущая стратегия")
                        Text(strategy.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            strategy.note,
                            fontSize = 12.sp,
                            color = ZmColors.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    GhostButton("Выбрать", modifier = Modifier.width(110.dp)) { onNavigate("strategies") }
                }
            }
        }

        item {
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            var checks by remember { mutableStateOf<List<io.github.robin19666.zapretmanager.core.SiteCheck>>(emptyList()) }
            var checking by remember { mutableStateOf(false) }
            ZmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Проверка сервисов", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Запросы идут через активный туннель — так видно, работает ли ускорение",
                            fontSize = 11.sp,
                            color = ZmColors.textSecondary,
                        )
                    }
                    PrimaryButton(
                        text = if (checking) "Проверяю…" else "Проверить",
                        modifier = Modifier.width(120.dp),
                        enabled = !checking,
                    ) {
                        checking = true
                        scope.launch {
                            checks = io.github.robin19666.zapretmanager.core.NetCheck.run(
                                if (dpiPhase == EnginePhase.RUNNING) {
                                    io.github.robin19666.zapretmanager.byedpi.ByeDpiVpnService.SOCKS_PORT
                                } else {
                                    null
                                }
                            )
                            checking = false
                        }
                    }
                }
                if (checks.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    checks.forEach { check ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.foundation.layout.Box(
                                Modifier
                                    .size(8.dp)
                                    .background(
                                        if (check.ok) ZmColors.accent else ZmColors.error,
                                        androidx.compose.foundation.shape.CircleShape,
                                    )
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(check.name, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text(
                                "${check.millis} мс · ${check.detail}",
                                fontSize = 11.sp,
                                color = ZmColors.textSecondary,
                            )
                        }
                    }
                }
            }
        }

        item {
            ZmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Журнал", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.clearLog() }) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = "Очистить", tint = ZmColors.textSecondary)
                    }
                    IconButton(onClick = {
                        val text = logs.takeLast(200).joinToString("\n") { "${it.tag}: ${it.message}" }
                        context.openUrl("https://github.com/${BuildConfig.SELF_REPO}")
                        context.copyToClipboard("Журнал", text)
                    }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Копировать", tint = ZmColors.textSecondary)
                    }
                }
                if (logs.isEmpty()) {
                    Text("Пока пусто", fontSize = 12.sp, color = ZmColors.textSecondary)
                } else {
                    Column(Modifier.background(ZmColors.surfaceHigh, RoundedCornerShape(10.dp)).padding(10.dp)) {
                        logs.takeLast(8).forEach { line ->
                            val color = when (line.level) {
                                io.github.robin19666.zapretmanager.core.LogLevel.ERROR -> ZmColors.error
                                io.github.robin19666.zapretmanager.core.LogLevel.WARN -> ZmColors.warn
                                else -> ZmColors.textPrimary
                            }
                            Text(
                                "${line.timeLabel()} ${line.tag}: ${line.message}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = color,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                KeyValueRow("Байт получено", Versions.humanBytes(tgStatus.bytesDown))
                KeyValueRow("Байт отправлено", Versions.humanBytes(tgStatus.bytesUp))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("GitHub проекта", modifier = Modifier.weight(1f)) {
                    context.openUrl("https://github.com/${BuildConfig.SELF_REPO}")
                }
                GhostButton("Обновления", modifier = Modifier.weight(1f)) { onNavigate("updates") }
            }
        }
    }
}

private fun LogLine.timeLabel(): String {
    val date = java.util.Date(time)
    return java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(date)
}