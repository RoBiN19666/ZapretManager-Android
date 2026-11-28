package io.github.robin19666.zapretmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.launch
import io.github.robin19666.zapretmanager.BuildConfig
import io.github.robin19666.zapretmanager.core.AppSettings
import io.github.robin19666.zapretmanager.core.EnginePhase
import io.github.robin19666.zapretmanager.core.Versions
import io.github.robin19666.zapretmanager.tgproxy.TgConfig
import io.github.robin19666.zapretmanager.tgproxy.TgSecret
import io.github.robin19666.zapretmanager.tgproxy.TgNative
import io.github.robin19666.zapretmanager.tgproxy.TgProxyController
import io.github.robin19666.zapretmanager.tgproxy.TgStatus
import io.github.robin19666.zapretmanager.ui.GhostButton
import io.github.robin19666.zapretmanager.ui.KeyValueRow
import io.github.robin19666.zapretmanager.ui.PrimaryButton
import io.github.robin19666.zapretmanager.ui.SectionTitle
import io.github.robin19666.zapretmanager.ui.StatusDot
import io.github.robin19666.zapretmanager.ui.SwitchRow
import io.github.robin19666.zapretmanager.ui.ZmCard
import io.github.robin19666.zapretmanager.ui.ZmViewModel
import io.github.robin19666.zapretmanager.ui.copyToClipboard
import io.github.robin19666.zapretmanager.ui.openUrl
import io.github.robin19666.zapretmanager.ui.toast
import io.github.robin19666.zapretmanager.ui.theme.ZmColors

@Composable
fun TelegramScreen(
    vm: ZmViewModel,
    status: TgStatus,
    settings: AppSettings,
) {
    val context = LocalContext.current
    var port by rememberSaveable(settings.tgPort) { mutableStateOf(settings.tgPort.toString()) }
    var domain by rememberSaveable(settings.tgMaskingDomain) { mutableStateOf(settings.tgMaskingDomain) }
    val routes = remember {
        listOf(
            "cf_proxy_ws" to "Cloudflare Proxy",
            "direct_ws" to "Прямой WS",
            "tcp_fallback" to "Прямой TCP",
        )
    }
    var route by rememberSaveable(settings.tgRoute) { mutableStateOf(settings.tgRoute) }
    val link = remember(settings.tgPort, settings.tgMaskingDomain, settings.tgSecret) {
        TgConfig.tgLink(settings.tgPort, TgConfig.ensureSecret(context), settings.tgMaskingDomain)
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Telegram-прокси", style = MaterialTheme.typography.headlineSmall)
        }

        item {
            ZmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(status.phase)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (status.phase) {
                                EnginePhase.RUNNING -> "Работает"
                                EnginePhase.STARTING -> "Запуск…"
                                EnginePhase.STOPPING -> "Остановка…"
                                EnginePhase.ERROR -> "Ошибка"
                                EnginePhase.STOPPED -> "Выключен"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            status.message.ifBlank { "127.0.0.1:${settings.tgPort}" },
                            fontSize = 12.sp,
                            color = ZmColors.textSecondary,
                        )
                    }
                    PrimaryButton(
                        text = if (status.running) "Стоп" else "Старт",
                        modifier = Modifier.width(110.dp),
                        enabled = !status.busy,
                    ) { TgProxyController.toggle() }
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = ZmColors.divider)
                Spacer(Modifier.height(8.dp))
                KeyValueRow("Статус рантайма", status.runtimeStatus.ifBlank { "—" })
                KeyValueRow("Исходящий маршрут", status.outbound.ifBlank { TgConfig.routeLabel(settings.tgRoute) })
                KeyValueRow("Получено", Versions.humanBytes(status.bytesDown))
                KeyValueRow("Отправлено", Versions.humanBytes(status.bytesUp))
                if (status.running && !status.routeReady) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Маршрут не подтверждён: ${status.runtimeStatus.ifBlank { "нет статуса" }}. " +
                            "Telegram может не подключиться — нажмите «Подобрать маршрут».",
                        color = ZmColors.warn,
                        fontSize = 12.sp,
                    )
                }
                if (status.lastError.isNotBlank()) {
                    Text(
                        "Ошибка рантайма: ${status.lastError}",
                        color = ZmColors.error,
                        fontSize = 11.sp,
                    )
                }
                if (status.phase == EnginePhase.ERROR && status.message.isNotBlank()) {
                    Text(status.message, color = ZmColors.error, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                GhostButton("Подобрать маршрут", modifier = Modifier.fillMaxWidth()) {
                    TgProxyController.restart()
                }
            }
        }

        item {
            ZmCard {
                SectionTitle("Подключение")
                Text(
                    link,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = ZmColors.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Применить", modifier = Modifier.weight(1f)) { context.openUrl(link) }
                    GhostButton("Копировать", modifier = Modifier.weight(1f)) {
                        context.copyToClipboard("MTProto", link)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Ссылка t.me", modifier = Modifier.weight(1f)) {
                        context.openUrl(TgConfig.tmeLink(settings.tgPort, TgConfig.ensureSecret(context), settings.tgMaskingDomain))
                    }
                    GhostButton("SOCKS5", modifier = Modifier.weight(1f)) {
                        context.openUrl(TgConfig.socksLink(settings.tgPort))
                    }
                }
                Text(
                    "Секрет: ${TgConfig.ensureSecret(context).take(8)}… · формат ${if (settings.tgMaskingDomain.isBlank()) "dd" else "ee (Fake TLS)"}",
                    fontSize = 11.sp,
                    color = ZmColors.textSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                // Рантайм мог подняться со старым секретом (гонка процессов при
                // авто-восстановлении): тогда Telegram шлёт «не тот» секрет, и все
                // рукопожатия отклоняются, хотя прокси числится работающим.
                val appSecret = TgSecret.peek(context)
                val appFingerprint = if (appSecret != null) TgSecret.fingerprint(appSecret) else ""
                val mismatch = status.running &&
                    status.secretFingerprint.isNotEmpty() &&
                    appFingerprint.isNotEmpty() &&
                    status.secretFingerprint != appFingerprint
                if (mismatch) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Секрет запущенного прокси не совпадает с приложением — Telegram не подключится. " +
                            "Нажмите «Применить» после перезапуска прокси.",
                        fontSize = 11.sp,
                        color = ZmColors.error,
                    )
                    Spacer(Modifier.height(6.dp))
                    GhostButton("Перезапустить прокси", modifier = Modifier.fillMaxWidth()) {
                        TgProxyController.restart()
                    }
                }
            }
        }

        item {
            ZmCard {
                SectionTitle("Параметры")
                OutlinedTextField(
                    value = port,
                    onValueChange = { value -> port = value.filter { it.isDigit() }.take(5) },
                    label = { Text("Порт") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZmColors.accent,
                        unfocusedBorderColor = ZmColors.divider,
                    ),
                )
                Spacer(Modifier.height(10.dp))
                Text("Маршрут", fontSize = 12.sp, color = ZmColors.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    routes.forEach { (value, label) ->
                        FilterChip(
                            selected = route == value,
                            onClick = { route = value },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ZmColors.accent.copy(alpha = 0.18f),
                                selectedLabelColor = ZmColors.accent,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it.trim() },
                    label = { Text("Домен маскировки (Fake TLS)") },
                    placeholder = { Text("например www.google.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZmColors.accent,
                        unfocusedBorderColor = ZmColors.divider,
                    ),
                )
                Text(
                    "С доменом ссылка использует секрет ee…, без домена — dd…. Меняйте домен, только если прокси не подключается.",
                    fontSize = 11.sp,
                    color = ZmColors.textSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Сохранить и перезапустить", modifier = Modifier.fillMaxWidth()) {
                    val parsedPort = port.toIntOrNull()?.coerceIn(1024, 65535) ?: settings.tgPort
                    vm.updateSettings {
                        it.copy(tgPort = parsedPort, tgRoute = route, tgMaskingDomain = domain)
                    }
                    if (status.running) {
                        TgProxyController.stop()
                    }
                    context.toast("Сохранено — запустите прокси заново")
                }
            }
        }

        item {
            ZmCard {
                SwitchRow(
                    title = "Открывать Telegram при старте",
                    subtitle = "Сразу предлагать подключение после запуска прокси",
                    checked = settings.tgAutoApply,
                    onCheckedChange = { value -> vm.updateSettings { it.copy(tgAutoApply = value) } },
                )
            }
        }

        item {
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            var portCheck by remember { mutableStateOf<io.github.robin19666.zapretmanager.core.SiteCheck?>(null) }
            var checking by remember { mutableStateOf(false) }
            ZmCard {
                SectionTitle("Диагностика")
                KeyValueRow("Go-рантайм", "отдельный процесс :tg")
                KeyValueRow("Порт", "127.0.0.1:${settings.tgPort}")
                KeyValueRow("Выбранный маршрут", status.parsed["selected_backend"].orEmpty().ifBlank { "—" })
                KeyValueRow("Фактический маршрут", status.parsed["actual_backend"].orEmpty().ifBlank { "—" })
                if (status.routeReason.isNotBlank()) {
                    KeyValueRow("Причина", status.routeReason)
                }
                if (portCheck != null) {
                    Text(
                        "Проверка порта: ${if (portCheck!!.ok) "открыт" else "закрыт"} " +
                            "(${portCheck!!.detail}, ${portCheck!!.millis} мс)",
                        fontSize = 11.sp,
                        color = if (portCheck!!.ok) ZmColors.accent else ZmColors.error,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Проверить порт", modifier = Modifier.weight(1f), enabled = !checking) {
                        checking = true
                        scope.launch {
                            portCheck = io.github.robin19666.zapretmanager.core.NetCheck.checkTgProxy(settings.tgPort)
                            checking = false
                        }
                    }
                    GhostButton("Копировать отчёт", modifier = Modifier.weight(1f)) {
                        val crash = io.github.robin19666.zapretmanager.core.CrashLog.read(context)
                            .orEmpty()
                            .take(600)
                        val trace = io.github.robin19666.zapretmanager.core.TgTrace.tail(context, 10)
                        context.copyToClipboard("Отчёт Telegram", tgReport(context, status, settings, crash, trace))
                    }
                }
                Text(
                    "Отчёт содержит статус рантайма и настройки без секрета — его можно приложить к issue.",
                    fontSize = 10.sp,
                    color = ZmColors.textSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        item {
            ZmCard {
                Text("Как это работает", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Прокси слушает только локальный адрес. Трафик Telegram уходит на Cloudflare Proxy по WebSocket, " +
                        "поэтому ограничения провайдера не мешают. Остальные приложения идут напрямую — прокси не является VPN.",
                    fontSize = 12.sp,
                    color = ZmColors.textSecondary,
                )
                Spacer(Modifier.height(6.dp))
                Column(Modifier.background(ZmColors.surfaceHigh, RoundedCornerShape(10.dp)).padding(10.dp)) {
                    Text("1. Включите прокси", fontSize = 11.sp)
                    Text("2. Нажмите «Применить» и подтвердите в Telegram", fontSize = 11.sp)
                    Text("3. Проверьте чаты: сообщения должны отправляться", fontSize = 11.sp)
                }
            }
        }
    }
}

/** Текстовый отчёт для issue: без секрета, но со всеми статусами рантайма. */
private fun tgReport(
    context: android.content.Context,
    status: TgStatus,
    settings: AppSettings,
    crash: String = "",
    trace: String = "",
): String = buildString {
    appendLine("Zapret Manager Android / Telegram-прокси")
    appendLine("app: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    appendLine("android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
    appendLine("abis: ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
    appendLine("port: ${settings.tgPort}")
    appendLine("route: ${settings.tgRoute}")
    appendLine("maskingDomain: ${settings.tgMaskingDomain.ifBlank { "-" }}")
    appendLine("phase: ${status.phase}")
    appendLine("message: ${status.message}")
    appendLine("runtime_status: ${status.runtimeStatus}")
    appendLine("outbound: ${status.outbound}")
    appendLine("route_reason: ${status.routeReason}")
    appendLine("last_error: ${status.lastError}")
    appendLine("secret_fingerprint_app: ${TgSecret.peek(context).let { if (it == null) "-" else TgSecret.fingerprint(it) }}")
    appendLine("secret_fingerprint_runtime: ${status.secretFingerprint.ifBlank { "-" }}")
    appendLine("native_process: :tg")
    if (trace.isNotBlank()) {
        appendLine("trace: $trace")
    }
    if (crash.isNotBlank()) {
        appendLine("last_crash:")
        appendLine(crash)
    }
    appendLine("status_raw: ${status.statusText}")
}