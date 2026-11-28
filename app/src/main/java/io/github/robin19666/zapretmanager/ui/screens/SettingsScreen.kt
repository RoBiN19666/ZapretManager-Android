package io.github.robin19666.zapretmanager.ui.screens

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.robin19666.zapretmanager.ui.theme.ZmColors
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap

private data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.ImageBitmap? = null,
)

private const val SEC_LOOK = "look"
private const val SEC_AUTOSTART = "autostart"
private const val SEC_TUNNEL = "tunnel"
private const val SEC_POWER = "power"
private const val SEC_DATA = "data"
private const val SEC_NETWORK = "network"
private const val SEC_UPDATES = "updates"
private const val SEC_DIAG = "diag"
private const val SEC_ABOUT = "about"

private val SECTION_TITLES = mapOf(
    SEC_LOOK to "Оформление",
    SEC_AUTOSTART to "Автозапуск",
    SEC_TUNNEL to "Туннель и сеть",
    SEC_POWER to "Питание",
    SEC_DATA to "Данные и копия",
    SEC_NETWORK to "Инструменты сети",
    SEC_UPDATES to "Обновления",
    SEC_DIAG to "Диагностика и журнал",
    SEC_ABOUT to "О программе",
)

@Composable
fun SettingsScreen(
    vm: ZmViewModel,
    settings: AppSettings,
    onOpenDiagnostics: () -> Unit = {},
    onOpenLists: () -> Unit = {},
    onOpenSiteCheck: () -> Unit = {},
    onOpenNetwork: () -> Unit = {},
    onOpenHosts: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    androidx.activity.compose.BackHandler(enabled = section != null) { section = null }
    val logs by io.github.robin19666.zapretmanager.core.LogBus.lines.collectAsState()
    var dns by rememberSaveable(settings.vpnDns) { mutableStateOf(settings.vpnDns) }
    var showPicker by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var backupMessage by remember { mutableStateOf("") }
    var backupBusy by remember { mutableStateOf(false) }
    var storageData by remember { mutableStateOf(0L) }
    var storageCache by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        val sizes = withContext(Dispatchers.IO) {
            io.github.robin19666.zapretmanager.core.Cleanup.dataSize(context) to
                io.github.robin19666.zapretmanager.core.Cleanup.cacheSize(context)
        }
        storageData = sizes.first
        storageCache = sizes.second
    }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            backupBusy = true
            scope.launch {
                val report = io.github.robin19666.zapretmanager.core.Backup.import(context, uri)
                backupMessage = report.summary + ". Перезапустите приложение, чтобы применить настройки."
                backupBusy = false
            }
        }
    }

    LaunchedEffect(showPicker) {
        if (showPicker && apps.isEmpty()) {
            apps = withContext(Dispatchers.IO) { loadLaunchableApps(context.packageManager) }
        }
    }

    // Разрешения выдаются в системных экранах; при возврате в приложение
    // состав композиции не меняется, поэтому обновляем проверки по ON_RESUME.
    var permissionEpoch by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionEpoch++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val vpnGranted = remember(permissionEpoch) { DpiEngine.prepareIntent(context) == null }
    val canInstall = remember(permissionEpoch) { UpdateManager.canInstall(context) }

    if (section == null) {
        SettingsHub(onOpen = { section = it })
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SettingsPageHeader(SECTION_TITLES[section] ?: "Настройки") { section = null } }

        if (section == SEC_LOOK) {
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
                Text("Цвет акцента", fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    listOf(
                        0xFF31D0AA to "Бирюзовый",
                        0xFF4C8DFF to "Синий",
                        0xFFA78BFA to "Фиолетовый",
                        0xFF4ADE80 to "Зелёный",
                        0xFFFF9F43 to "Оранжевый",
                        0xFFFF6B6B to "Красный",
                        0xFFF472B6 to "Розовый",
                        0xFFFBBF24 to "Янтарный",
                    ).forEach { (value, label) ->
                        val selected = settings.accentColor == value
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(value))
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) ZmColors.textPrimary else ZmColors.divider,
                                        shape = CircleShape,
                                    )
                                    .clickable { vm.updateSettings { it.copy(accentColor = value) } }
                            )
                            Text(
                                label,
                                fontSize = 8.sp,
                                color = if (selected) ZmColors.accent else ZmColors.textSecondary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }

                SwitchRow(
                    title = "AMOLED-чёрный фон",
                    subtitle = "Чистый чёрный только для тёмной темы",
                    checked = settings.amoled,
                    onCheckedChange = { value -> vm.updateSettings { it.copy(amoled = value) } },
                )
            }
        }
        }

        if (section == SEC_AUTOSTART) {
        item {
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
            }
        }
        }

        if (section == SEC_UPDATES) {
        item {
            ZmCard {
                SectionTitle("Обновления")
                SwitchRow(
                    title = "Проверять обновления при старте",
                    subtitle = "Не чаще раза в 12 часов",
                    checked = settings.checkUpdatesOnStart,
                    onCheckedChange = { value -> vm.updateSettings { it.copy(checkUpdatesOnStart = value) } },
                )
                SwitchRow(
                    title = "Автообновление компонентов",
                    subtitle = "Стратегии и списки сайтов без участия пользователя",
                    checked = settings.autoUpdateComponents,
                    onCheckedChange = { value -> vm.updateSettings { it.copy(autoUpdateComponents = value) } },
                )
            }
        }
        }

        if (section == SEC_TUNNEL) {
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
                Text("Чёрный и белый списки приложений", fontSize = 12.sp, color = ZmColors.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        SplitMode.ALL to "Все приложения",
                        SplitMode.EXCLUDE to "Чёрный список",
                        SplitMode.INCLUDE to "Белый список",
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = settings.splitMode == mode,
                            onClick = {
                                if (mode == SplitMode.INCLUDE && settings.splitApps.isEmpty()) {
                                    val base = io.github.robin19666.zapretmanager.core.BaseApps
                                        .installed(context).toSet()
                                    vm.updateSettings { it.copy(splitMode = mode, splitApps = base) }
                                    context.toast(
                                        if (base.isEmpty()) {
                                            "Базовые приложения не найдены — выберите вручную"
                                        } else {
                                            "Добавлены базовые приложения: ${base.size}"
                                        },
                                    )
                                } else {
                                    vm.updateSettings { it.copy(splitMode = mode) }
                                }
                            },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ZmColors.accent.copy(alpha = 0.18f),
                                selectedLabelColor = ZmColors.accent,
                            ),
                        )
                    }
                }
                Text(
                    when (settings.splitMode) {
                        SplitMode.EXCLUDE -> "Чёрный список: выбранные приложения идут мимо туннеля, остальные — через ускорение."
                        SplitMode.INCLUDE -> "Белый список: через ускорение идут только выбранные приложения, остальные напрямую."
                        else -> "Туннель для всех приложений, кроме самого менеджера."
                    },
                    fontSize = 10.sp,
                    color = ZmColors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (settings.splitMode != SplitMode.ALL) {
                    Spacer(Modifier.height(8.dp))
                    GhostButton(
                        text = "Выбрать приложения (${settings.splitApps.size})",
                        modifier = Modifier.fillMaxWidth(),
                    ) { showPicker = true }
                    Spacer(Modifier.height(6.dp))
                    GhostButton("Добавить базовые приложения", modifier = Modifier.fillMaxWidth()) {
                        val before = settings.splitApps.size
                        val withBase = io.github.robin19666.zapretmanager.core.BaseApps
                            .withBase(context, settings.splitApps)
                        vm.updateSettings { it.copy(splitApps = withBase) }
                        context.toast("Добавлено приложений: ${withBase.size - before}")
                    }
                    if (settings.splitMode == SplitMode.INCLUDE && settings.splitApps.isEmpty()) {
                        Text(
                            "Белый список пуст: пока ничего не выбрано, через туннель пойдут все приложения.",
                            fontSize = 10.sp,
                            color = ZmColors.warn,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        }

        if (section == SEC_POWER) {
        item {
            ZmCard {
                SectionTitle("Питание")
                val ignoring = Power.isIgnoring(context)
                KeyValueRow(
                    "Режим батареи",
                    Power.statusText(context),
                )
                Text(
                    Power.vendorHint(context),
                    fontSize = 11.sp,
                    color = ZmColors.warn,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                GhostButton(
                    text = if (ignoring) "Открыть настройки батареи" else "Разрешить работу в фоне",
                    modifier = Modifier.fillMaxWidth(),
                ) { Power.openPowerSettings(context) }
                Spacer(Modifier.height(6.dp))
                GhostButton(
                    "Открыть автозапуск приложения",
                    modifier = Modifier.fillMaxWidth(),
                ) { Power.openAutostartSettings(context) }
                Spacer(Modifier.height(6.dp))
                GhostButton(
                    "Настройки приложения в системе",
                    modifier = Modifier.fillMaxWidth(),
                ) { Power.openAppSettings(context) }
                Text(
                    "Там же пункт «Приостанавливать работу приложения, если оно не используется» — " +
                        "его нужно выключить, иначе система выгружает приложение.",
                    fontSize = 10.sp,
                    color = ZmColors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        }

        if (section == SEC_DATA) {
        item {
            ZmCard {
                SectionTitle("Хранилище")
                Text(
                    "Данные приложения: ${io.github.robin19666.zapretmanager.core.Versions.humanBytes(storageData)} · " +
                        "кэш: ${io.github.robin19666.zapretmanager.core.Versions.humanBytes(storageCache)}",
                    fontSize = 12.sp,
                )
                Text(
                    "Установленный APK заменяется при обновлении, скачанные файлы удаляются сами.",
                    fontSize = 10.sp,
                    color = ZmColors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                GhostButton("Очистить кэш", modifier = Modifier.fillMaxWidth()) {
                    scope.launch {
                        val freed = withContext(Dispatchers.IO) {
                            io.github.robin19666.zapretmanager.core.Cleanup.clearDownloads(context)
                        }
                        val sizes = withContext(Dispatchers.IO) {
                            io.github.robin19666.zapretmanager.core.Cleanup.dataSize(context) to
                                io.github.robin19666.zapretmanager.core.Cleanup.cacheSize(context)
                        }
                        storageData = sizes.first
                        storageCache = sizes.second
                        context.toast("Освобождено ${io.github.robin19666.zapretmanager.core.Versions.humanBytes(freed)}")
                    }
                }
            }
        }
        }

        if (section == SEC_DATA) {
        item {
            ZmCard {
                SectionTitle("Резервная копия")
                Text(
                    "Настройки, свои конфигурации, списки, профили сети. Секрета Telegram в архиве нет.",
                    fontSize = 11.sp,
                    color = ZmColors.textSecondary,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Экспорт", modifier = Modifier.weight(1f), enabled = !backupBusy) {
                        scope.launch {
                            backupBusy = true
                            runCatching {
                                val file = io.github.robin19666.zapretmanager.core.Backup.export(context)
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file,
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(intent, "Сохранить резервную копию")
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                                backupMessage = "Архив готов: ${file.name}"
                            }.onFailure { backupMessage = "Не удалось создать архив: ${it.message}" }
                            backupBusy = false
                        }
                    }
                    GhostButton("Импорт", modifier = Modifier.weight(1f), enabled = !backupBusy) {
                        importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                    }
                }
                if (backupMessage.isNotBlank()) {
                    Text(backupMessage, fontSize = 11.sp, color = ZmColors.textSecondary, modifier = Modifier.padding(top = 6.dp))
                }
                Text(
                    "Откатить версию старым APK нельзя: Android требует versionCode не ниже текущего.",
                    fontSize = 10.sp,
                    color = ZmColors.textSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        }

        if (section == SEC_NETWORK) {
        item {
            ZmCard {
                SectionTitle("Проверка сайта")
                Text(
                    "Пошагово DNS → TCP → TLS → HTTP с вердиктом: что именно мешает сайту " +
                        "(DNS, соединение, TLS или HTTP).",
                    fontSize = 11.sp,
                    color = ZmColors.textSecondary,
                )
                Spacer(Modifier.height(8.dp))
                GhostButton("Проверить сайт", modifier = Modifier.fillMaxWidth()) {
                    onOpenSiteCheck()
                }
            }
        }
        }

        if (section == SEC_NETWORK) {
        item {
            ZmCard {
                SectionTitle("Профили сети")
                Text(
                    "Свой конфиг под каждый Wi-Fi и оператора. При смене сети приложение само " +
                        "переключит стратегию: при выключении Wi-Fi — сразу, при обрыве — с задержкой.",
                    fontSize = 11.sp,
                    color = ZmColors.textSecondary,
                )
                Spacer(Modifier.height(8.dp))
                GhostButton("Настроить профили", modifier = Modifier.fillMaxWidth()) {
                    onOpenNetwork()
                }
            }
        }
        }

        if (section == SEC_NETWORK) {
        item {
            ZmCard {
                SectionTitle("Домены для ускорения")
                Text(
                    io.github.robin19666.zapretmanager.core.Hosts.summary(context),
                    fontSize = 12.sp,
                )
                Text(
                    "Белый список доменов: ускорение соединения только для выбранных сайтов (аналог --hostlist " +
                        "в zapret). Изменения применяются после перезапуска ускорения.",
                    fontSize = 10.sp,
                    color = ZmColors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                GhostButton("Настроить домены", modifier = Modifier.fillMaxWidth()) { onOpenHosts() }
            }
        }
        }

        if (section == SEC_TUNNEL) {
        item {
            ZmCard {
                SectionTitle("Сторож")
                SwitchRow(
                    title = "Режим сторожа",
                    subtitle = "Раз в 2 минуты проверять туннель и перезапускать ускорение при обрыве",
                    checked = settings.watchdogEnabled,
                    onCheckedChange = { value ->
                        vm.updateSettings { it.copy(watchdogEnabled = value) }
                        if (value) io.github.robin19666.zapretmanager.core.Watchdog.start(context)
                        else io.github.robin19666.zapretmanager.core.Watchdog.stop()
                    },
                )
            }
        }
        }

        if (section == SEC_NETWORK) {
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
        }

        if (section == SEC_DIAG) {
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
                        "VPN-разрешение запрашивается при первом включении ускорения.",
                        fontSize = 11.sp,
                        color = ZmColors.textSecondary,
                    )
                }
            }
        }

        item {
            ZmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Журнал", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.clearLog() }) {
                        Icon(
                            Icons.Rounded.DeleteSweep,
                            contentDescription = "Очистить журнал",
                            tint = ZmColors.textSecondary,
                        )
                    }
                    IconButton(onClick = {
                        val text = logs.takeLast(300).joinToString("\n") { "${it.tag}: ${it.message}" }
                        context.copyToClipboard("Журнал", text)
                    }) {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = "Копировать журнал",
                            tint = ZmColors.textSecondary,
                        )
                    }
                }
                if (logs.isEmpty()) {
                    Text("Пока пусто", fontSize = 12.sp, color = ZmColors.textSecondary)
                } else {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .background(ZmColors.surfaceHigh, MaterialTheme.shapes.small)
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp),
                    ) {
                        logs.takeLast(50).forEach { line ->
                            val color = when (line.level) {
                                io.github.robin19666.zapretmanager.core.LogLevel.ERROR -> ZmColors.error
                                io.github.robin19666.zapretmanager.core.LogLevel.WARN -> ZmColors.warn
                                else -> ZmColors.textPrimary
                            }
                            Text(
                                "${logTime(line.time)} ${line.tag}: ${line.message}",
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = color,
                                maxLines = 3,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        }

        if (section == SEC_ABOUT) {
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
                    "byedpi + hev-socks5-tunnel и локальный MTProto-прокси. GPLv3-компоненты.",
                    fontSize = 11.sp,
                    color = ZmColors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
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
                Spacer(Modifier.height(10.dp))
                Text(
                    "Автор графической оболочки: RoBiN19666",
                    fontSize = 12.sp,
                    color = ZmColors.textPrimary,
                )
                Text(
                    "Если приложение оказалось полезным — можно поддержать автора.",
                    fontSize = 11.sp,
                    color = ZmColors.textSecondary,
                )
                Spacer(Modifier.height(6.dp))
                Text("На хлебушек", fontSize = 11.sp, color = ZmColors.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(
                        "Boosty" to R.drawable.boosty,
                        "DonationAlerts" to R.drawable.donationalerts,
                    ).forEach { (label, icon) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                painter = painterResource(icon),
                                contentDescription = label,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable {
                                        context.openUrl(
                                            if (label == "Boosty") {
                                                "https://boosty.to/robin19666"
                                            } else {
                                                "https://www.donationalerts.com/r/robin19666"
                                            }
                                        )
                                    },
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            )
                            Text(label, fontSize = 8.sp, color = ZmColors.textSecondary)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Play Protect может ругаться на самоподписанный APK — это ложное срабатывание. " +
                        "SHA-256 релиза лежит рядом с файлом.",
                    fontSize = 10.sp,
                    color = ZmColors.textSecondary,
                )
                Spacer(Modifier.height(6.dp))
                GhostButton("Почему Play Protect ругается", modifier = Modifier.fillMaxWidth()) {
                    context.openUrl(
                        "https://github.com/${BuildConfig.SELF_REPO}/blob/main/docs/PLAY_PROTECT.md"
                    )
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
            var query by remember { mutableStateOf("") }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Поиск по названию или пакету") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ZmColors.accent,
                    unfocusedBorderColor = ZmColors.divider,
                ),
            )
            Spacer(Modifier.height(6.dp))
            val filtered = apps.filter { app ->
                query.isBlank() ||
                    app.label.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)
            }
            if (apps.isEmpty()) {
                Text("Загрузка списка…", fontSize = 12.sp, color = ZmColors.textSecondary)
            } else if (filtered.isEmpty()) {
                Text("Ничего не найдено", fontSize = 12.sp, color = ZmColors.textSecondary)
            }
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(app.packageName) }
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = app.packageName in selected,
                            onCheckedChange = { onToggle(app.packageName) },
                        )
                        if (app.icon != null) {
                            Image(
                                bitmap = app.icon,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(32.dp)
                                    .padding(end = 8.dp),
                            )
                        }
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
        .map { info ->
            val packageName = info.activityInfo.packageName
            val icon = runCatching {
                pm.getApplicationIcon(packageName).toBitmap().asImageBitmap()
            }.getOrNull()
            AppEntry(packageName, info.loadLabel(pm).toString(), icon)
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

private fun logTime(ms: Long): String =
    java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(ms))

/** Меню «Ещё»: короткие подпункты, каждый открывает свою страницу настроек. */
@Composable
private fun SettingsHub(onOpen: (String) -> Unit) {
    val rows = listOf(
        Triple(SEC_LOOK, "Оформление", "Тема, акцент, AMOLED"),
        Triple(SEC_AUTOSTART, "Автозапуск", "При загрузке и открытии приложения"),
        Triple(SEC_TUNNEL, "Туннель и сеть", "DNS, IPv6, списки приложений, сторож"),
        Triple(SEC_POWER, "Питание", "Фон, батарея, автозапуск в системе"),
        Triple(SEC_DATA, "Данные и копия", "Хранилище, резервная копия"),
        Triple(SEC_NETWORK, "Инструменты сети", "Проверка сайта, профили, домены, списки"),
        Triple(SEC_UPDATES, "Обновления", "Автопроверка и компоненты"),
        Triple(SEC_DIAG, "Диагностика и журнал", "Проверки и лог приложения"),
        Triple(SEC_ABOUT, "О программе", "Версия, ссылки, поддержка"),
    )
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                "Ещё",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        items(rows, key = { it.first }) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ZmColors.surface)
                    .clickable { onOpen(row.first) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(row.second, style = MaterialTheme.typography.titleMedium)
                    Text(row.third, fontSize = 11.sp, color = ZmColors.textSecondary)
                }
                Text("›", fontSize = 20.sp, color = ZmColors.textSecondary)
            }
        }
    }
}

@Composable
private fun SettingsPageHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Назад",
                tint = ZmColors.textSecondary,
            )
        }
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}