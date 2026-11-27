package io.github.robin19666.zapretmanager.ui.screens

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.robin19666.zapretmanager.BuildConfig
import io.github.robin19666.zapretmanager.R
import io.github.robin19666.zapretmanager.byedpi.DpiEngine
import io.github.robin19666.zapretmanager.byedpi.Native
import io.github.robin19666.zapretmanager.core.AppSettings
import io.github.robin19666.zapretmanager.core.LogBus
import io.github.robin19666.zapretmanager.core.Power
import io.github.robin19666.zapretmanager.core.SplitMode
import io.github.robin19666.zapretmanager.core.UpdateManager
import io.github.robin19666.zapretmanager.ui.GhostButton
import io.github.robin19666.zapretmanager.ui.KeyValueRow
import io.github.robin19666.zapretmanager.ui.PrimaryButton
import io.github.robin19666.zapretmanager.ui.SectionTitle
import io.github.robin19666.zapretmanager.ui.SwitchRow
import io.github.robin19666.zapretmanager.ui.ZmCard
import io.github.robin19666.zapretmanager.ui.ZmViewModel
import io.github.robin19666.zapretmanager.ui.copyToClipboard
import io.github.robin19666.zapretmanager.ui.openUrl
import io.github.robin19666.zapretmanager.ui.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.robin19666.zapretmanager.ui.theme.ZmColors

private data class AppEntry(val packageName: String, val label: String)

@Composable
fun SettingsScreen(
    vm: ZmViewModel,
    settings: AppSettings,
    onOpenDiagnostics: () -> Unit = {},
    onOpenLists: () -> Unit = {},
) {
    val context = LocalContext.current
    var dns by rememberSaveable(settings.vpnDns) { mutableStateOf(settings.vpnDns) }
    var showPicker by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }

    LaunchedEffect(showPicker) {
        if (showPicker && apps.isEmpty()) {
            apps = withContext(Dispatchers.IO) { loadLaunchableApps(context.packageManager) }
        }
    }

    val vpnGranted = DpiEngine.prepareIntent(context) == null
    val canInstall = UpdateManager.canInstall(context)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text("Настройки", style = MaterialTheme.typography.headlineSmall)
                Text("Поведение приложения, туннель и диагностика", fontSize = 12.sp, color = ZmColors.textSecondary)
            }
        }

        item {
            ZmCard {
                SectionTitle("Оформление")
                Text(
                    "Тема интерфейса",
                    fontSize = 13.sp,
                )
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
                Text(
                    "«Как в системе» переключается автоматически вместе с оформлением Android.",
                    fontSize = 11.sp,
                    color = ZmColors.textSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        item {
            ZmCard {
                SectionTitle("Автозапуск и обновления")
                SwitchRow(
                    title = "Обход DPI при загрузке",
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
                    title = "Проверять обновления при старте",
                    checked = settings.checkUpdatesOnStart,
                    onCheckedChange = { value -> vm.updateSettings { it.copy(checkUpdatesOnStart = value) } },
                )
                SwitchRow(
                    title = "Автообновление компонентов",
                    subtitle = "Стратегии и списки сайтов без участия пользователя",
                    checked = settings.autoUpdateComponents,
                    onCheckedChange = { value -> vm.updateSettings { it.copy(autoUpdateComponents = value) } },
                )
                SwitchRow(
                    title = "Уведомления о запуске",
                    checked = settings.notifyOnStart,
                    onCheckedChange = { value -> vm.updateSettings { it.copy(notifyOnStart = value) } },
                )
            }
        }

        item {
            ZmCard {
                SectionTitle("Туннель")
                SwitchRow(
                    title = "IPv6 в туннеле",
                    subtitle = "Включайте, только если провайдер поддерживает IPv6",
                    checked = settings.vpnIpv6,
                    onCheckedChange = { value -> vm.updateSettings { it.copy(vpnIpv6 = value) } },
                )
                OutlinedTextField(
                    value = dns,
                    onValueChange = { dns = it },
                    label = { Text("DNS-сервер") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZmColors.accent,
                        unfocusedBorderColor = ZmColors.divider,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                PrimaryButton("Сохранить DNS", modifier = Modifier.fillMaxWidth()) {
                    vm.updateSettings { it.copy(vpnDns = dns.trim()) }
                    context.toast("DNS сохранён")
                }

                Spacer(Modifier.height(12.dp))
                Text("Какие приложения пускать через туннель", fontSize = 12.sp, color = ZmColors.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        SplitMode.ALL to "Все",
                        SplitMode.EXCLUDE to "Кроме выбранных",
                        SplitMode.INCLUDE to "Только выбранные",
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = settings.splitMode == mode,
                            onClick = { vm.updateSettings { it.copy(splitMode = mode) } },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ZmColors.accent.copy(alpha = 0.18f),
                                selectedLabelColor = ZmColors.accent,
                            ),
                        )
                    }
                }
                if (settings.splitMode != SplitMode.ALL) {
                    Spacer(Modifier.height(8.dp))
                    GhostButton(
                        text = "Выбрать приложения (${settings.splitApps.size})",
                        modifier = Modifier.fillMaxWidth(),
                    ) { showPicker = true }
                }
            }
        }

        item {
            ZmCard {
                SectionTitle("Питание")
                val ignoring = Power.isIgnoring(context)
                KeyValueRow(
                    "Режим батареи",
                    if (ignoring) "без ограничений" else "оптимизируется системой",
                )
                Text(
                    "Android усыпляет фоновые приложения и может остановить обход DPI или " +
                        "Telegram-прокси при выключенном экране. Разрешение «Без ограничений» " +
                        "не влияет на другие приложения и расход батареи почти не меняет.",
                    fontSize = 11.sp,
                    color = ZmColors.textSecondary,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                GhostButton(
                    text = if (ignoring) "Открыть настройки батареи" else "Разрешить работу в фоне",
                    modifier = Modifier.fillMaxWidth(),
                ) { Power.open(context) }
            }
        }

        item {
            ZmCard {
                SectionTitle("Источники обновлений")
                Text(
                    "Приложение публичное, поэтому релизы, стратегии и пул Cloudflare-доменов " +
                        "загружаются без авторизации — токены не нужны.",
                    fontSize = 11.sp,
                    color = ZmColors.textSecondary,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Репозиторий", modifier = Modifier.weight(1f)) {
                        context.openUrl("https://github.com/${BuildConfig.SELF_REPO}")
                    }
                    GhostButton("Релизы", modifier = Modifier.weight(1f)) {
                        context.openUrl("https://github.com/${BuildConfig.SELF_REPO}/releases")
                    }
                }
            }
        }

        item {
            ZmCard {
                SectionTitle("Списки сайтов")
                Text(
                    "8 файлов доменов для проверки сайтов и тестов: правка, массовая вставка, " +
                        "удаление дублей, откат к версии из ByeByeDPI.",
                    fontSize = 11.sp,
                    color = ZmColors.textSecondary,
                )
                Spacer(Modifier.height(8.dp))
                GhostButton("Открыть списки", modifier = Modifier.fillMaxWidth()) {
                    onOpenLists()
                }
            }
        }

        item {
            ZmCard {
                SectionTitle("Диагностика")
                PrimaryButton("Полная диагностика", modifier = Modifier.fillMaxWidth()) {
                    onOpenDiagnostics()
                }
                Spacer(Modifier.height(10.dp))
                KeyValueRow(
                    "VPN-разрешение",
                    if (vpnGranted) "выдано" else "не выдано",
                )
                KeyValueRow(
                    "Установка APK",
                    if (canInstall) "разрешена" else "запрещена",
                )
                KeyValueRow("Android", "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                KeyValueRow("ABI", Build.SUPPORTED_ABIS.joinToString(", "))
                KeyValueRow("Движок byedpi", if (Native.ensureLoaded()) "загружен" else (Native.unavailableReason() ?: "нет"))
                KeyValueRow("Движок Telegram", "в процессе :tg")
                KeyValueRow("Версия", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Проверить снова", modifier = Modifier.weight(1f)) {
                        if (!vpnGranted) {
                            val intent = DpiEngine.prepareIntent(context)
                            if (intent != null) {
                                runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }
                        }
                        if (!canInstall) {
                            runCatching { context.startActivity(UpdateManager.installPermissionIntent(context)) }
                        }
                    }
                    GhostButton("Копировать журнал", modifier = Modifier.weight(1f)) {
                        context.copyToClipboard("Журнал", LogBus.exportText())
                    }
                }
                if (!vpnGranted) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "VPN-разрешение запрашивается при первом включении обхода.",
                        fontSize = 11.sp,
                        color = ZmColors.textSecondary,
                    )
                }
            }
        }

        item {
            ZmCard {
                SectionTitle("О программе")
                Image(
                    painter = painterResource(R.drawable.logo_wide),
                    contentDescription = "Zapret Manager",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.height(8.dp))
                Text("Zapret Manager для Android", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Единый менеджер: обход DPI (byedpi + hev-socks5-tunnel) и локальный MTProto-прокси " +
                        "для Telegram (TG WS Proxy). Проект использует GPLv3-компоненты.",
                    fontSize = 12.sp,
                    color = ZmColors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Репозиторий", modifier = Modifier.weight(1f)) {
                        context.openUrl("https://github.com/${BuildConfig.SELF_REPO}")
                    }
                    GhostButton("Релизы", modifier = Modifier.weight(1f)) {
                        context.openUrl("https://github.com/${BuildConfig.SELF_REPO}/releases")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("ByeByeDPI", modifier = Modifier.weight(1f)) {
                        context.openUrl("https://github.com/${BuildConfig.BYEDPI_REPO}")
                    }
                    GhostButton("TG WS Proxy", modifier = Modifier.weight(1f)) {
                        context.openUrl("https://github.com/${BuildConfig.TGWS_REPO}")
                    }
                }
            }
        }
    }

    if (showPicker) {
        AppPickerDialog(
            apps = apps,
            selected = settings.splitApps,
            onDismiss = { showPicker = false },
            onToggle = { packageName ->
                val next = settings.splitApps.toMutableSet()
                if (!next.add(packageName)) next.remove(packageName)
                vm.updateSettings { it.copy(splitApps = next) }
            },
        )
    }
}

@Composable
private fun AppPickerDialog(
    apps: List<AppEntry>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onToggle: (String) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .background(ZmColors.surface, MaterialTheme.shapes.large)
                .padding(16.dp),
        ) {
            Text("Приложения", style = MaterialTheme.typography.titleMedium)
            Text(
                "Отмечено: ${selected.size}",
                fontSize = 11.sp,
                color = ZmColors.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
            if (apps.isEmpty()) {
                Text("Загрузка списка…", fontSize = 12.sp, color = ZmColors.textSecondary)
            }
            LazyColumn(Modifier.heightIn(max = 440.dp)) {
                items(apps, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(app.packageName) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = app.packageName in selected,
                            onCheckedChange = { onToggle(app.packageName) },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(app.label, fontSize = 13.sp, maxLines = 1)
                            Text(app.packageName, fontSize = 10.sp, color = ZmColors.textSecondary, maxLines = 1)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Готово", modifier = Modifier.fillMaxWidth()) { onDismiss() }
        }
    }
}

private fun loadLaunchableApps(pm: android.content.pm.PackageManager): List<AppEntry> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}