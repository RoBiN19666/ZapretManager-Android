package io.github.robin19666.zapretmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Bolt
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.robin19666.zapretmanager.BuildConfig
import io.github.robin19666.zapretmanager.byedpi.Strategies
import io.github.robin19666.zapretmanager.core.AppSettings
import io.github.robin19666.zapretmanager.core.EnginePhase
import io.github.robin19666.zapretmanager.core.EngineState
import io.github.robin19666.zapretmanager.core.Versions
import io.github.robin19666.zapretmanager.tgproxy.TgConfig
import io.github.robin19666.zapretmanager.tgproxy.TgStatus
import io.github.robin19666.zapretmanager.ui.EngineRow
import io.github.robin19666.zapretmanager.ui.GhostButton
import io.github.robin19666.zapretmanager.ui.LocalNavBarInset
import io.github.robin19666.zapretmanager.ui.PrimaryButton
import io.github.robin19666.zapretmanager.ui.SectionTitle
import io.github.robin19666.zapretmanager.ui.ZmCard
import io.github.robin19666.zapretmanager.ui.copyToClipboard
import io.github.robin19666.zapretmanager.ui.openUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.robin19666.zapretmanager.ui.theme.ZmColors

@Composable
fun DashboardScreen(
    dpiPhase: EnginePhase,
    dpiState: EngineState,
    tgPhase: EnginePhase,
    settings: AppSettings,
    tgStatus: TgStatus,
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
    val activeStrategy = remember(settings.strategyId, settings.customArgs) {
        Strategies.active(context, settings.strategyId, settings.customArgs)
    }
    val dpiUptime = if (dpiPhase == EnginePhase.RUNNING) Versions.humanDuration(now - dpiState.since) else ""
    val tgUptime = if (tgPhase == EnginePhase.RUNNING) Versions.humanDuration(now - tgStatus.since) else ""

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + LocalNavBarInset.current,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Zapret Manager",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
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
                        EnginePhase.RUNNING -> "«${activeStrategy.title}»"
                        EnginePhase.STARTING -> "Запуск движка…"
                        EnginePhase.STOPPING -> "Остановка…"
                        EnginePhase.ERROR -> dpiState.message.ifBlank { "Ошибка" }
                        EnginePhase.STOPPED -> "Выключен · вся сеть идёт напрямую"
                    },
                    suffix = if (dpiPhase == EnginePhase.RUNNING) " · $dpiUptime" else "",
                    phase = dpiPhase,
                    checked = dpiPhase == EnginePhase.RUNNING || dpiPhase == EnginePhase.STARTING,
                    enabled = dpiPhase != EnginePhase.STOPPING,
                    icon = Icons.Rounded.Bolt,
                    onCheckedChange = { onToggleDpi() },
                )
                Spacer(Modifier.height(10.dp))
                EngineRow(
                    title = "Telegram-прокси",
                    subtitle = when (tgPhase) {
                        EnginePhase.RUNNING -> {
                            val port = tgStatus.port.takeIf { it > 0 } ?: settings.tgPort
                            "127.0.0.1:$port" +
                                if (tgStatus.outbound.isNotBlank()) " · ${tgStatus.outbound}" else ""
                        }
                        EnginePhase.STARTING -> "Запуск прокси…"
                        EnginePhase.STOPPING -> "Остановка…"
                        EnginePhase.ERROR -> tgStatus.message.ifBlank { "Ошибка" }
                        EnginePhase.STOPPED -> "Локальный MTProto-прокси выключен"
                    },
                    suffix = if (tgPhase == EnginePhase.RUNNING) " · $tgUptime" else "",
                    phase = tgPhase,
                    checked = tgPhase == EnginePhase.RUNNING || tgPhase == EnginePhase.STARTING,
                    enabled = tgPhase != EnginePhase.STOPPING,
                    icon = Icons.AutoMirrored.Rounded.Send,
                    onCheckedChange = { onToggleTg() },
                )
            }
        }

        if (tgStatus.running) {
            item {
                ZmCard {
                    SectionTitle("Подключение Telegram")
                    val link = TgConfig.tgLink(settings.tgPort, TgConfig.ensureSecret(context), settings.tgMaskingDomain)
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
                        Text(activeStrategy.title, style = MaterialTheme.typography.titleMedium)
                        if (activeStrategy.custom) {
                            Text(
                                activeStrategy.args,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = ZmColors.accent,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            activeStrategy.note,
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
                    Text("Проверка сервисов", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    PrimaryButton(
                        text = if (checking) "Проверяю…" else "Проверить",
                        modifier = Modifier.width(120.dp),
                        enabled = !checking,
                    ) {
                        checking = true
                        scope.launch {
                            val tgRunning = tgPhase == EnginePhase.RUNNING
                            checks = io.github.robin19666.zapretmanager.core.NetCheck.run(
                                socksPort = if (dpiPhase == EnginePhase.RUNNING) {
                                    io.github.robin19666.zapretmanager.byedpi.ByeDpiVpnService.SOCKS_PORT
                                } else {
                                    null
                                },
                                tgPort = if (tgRunning) {
                                    tgStatus.port.takeIf { it > 0 } ?: settings.tgPort
                                } else {
                                    null
                                },
                                tgRouteReady = if (tgRunning) tgStatus.routeReady else null,
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
                                color = if (check.ok) ZmColors.textSecondary else ZmColors.error,
                                maxLines = 2,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                modifier = Modifier.weight(1.3f),
                            )
                        }
                    }
                }
            }
        }
    }
}