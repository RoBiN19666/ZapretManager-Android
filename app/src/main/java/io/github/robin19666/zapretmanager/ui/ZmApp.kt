package io.github.robin19666.zapretmanager.ui

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.sp
import io.github.robin19666.zapretmanager.byedpi.DpiEngine
import io.github.robin19666.zapretmanager.core.EnginePhase
import io.github.robin19666.zapretmanager.core.Power
import io.github.robin19666.zapretmanager.tgproxy.TgProxyController
import io.github.robin19666.zapretmanager.ui.screens.DashboardScreen
import io.github.robin19666.zapretmanager.ui.screens.AutotuneScreen
import io.github.robin19666.zapretmanager.ui.screens.DiagnosticsScreen
import io.github.robin19666.zapretmanager.ui.screens.HostsScreen
import io.github.robin19666.zapretmanager.ui.screens.NetworkProfilesScreen
import io.github.robin19666.zapretmanager.ui.screens.SettingsScreen
import io.github.robin19666.zapretmanager.ui.screens.SiteCheckScreen
import io.github.robin19666.zapretmanager.ui.screens.SiteListsScreen
import io.github.robin19666.zapretmanager.ui.screens.StrategiesScreen
import io.github.robin19666.zapretmanager.ui.screens.TelegramScreen
import io.github.robin19666.zapretmanager.ui.theme.LocalNewYear
import io.github.robin19666.zapretmanager.ui.theme.ZmColors

private data class Destination(val route: String, val label: String, val icon: ImageVector)

private val destinations = listOf(
    Destination("dash", "Обзор", Icons.Rounded.Home),
    Destination("strategies", "Стратегии", Icons.Rounded.Tune),
    Destination("telegram", "Telegram", Icons.AutoMirrored.Rounded.Send),
    Destination("settings", "Ещё", Icons.Rounded.Settings),
)

@Composable
fun ZmApp(vm: ZmViewModel) {
    val context = LocalContext.current
    var current by rememberSaveable { mutableStateOf("dash") }
    // Подраздел «Ещё», куда пришли по ссылке (например, с баннера обновления).
    var settingsSection by rememberSaveable { mutableStateOf<String?>(null) }

    val dpi by vm.dpi.collectAsState()
    val tg by vm.tg.collectAsState()
    val settings by vm.settings.collectAsState()
    val updates by vm.updates.collectAsState()
    val strategies by vm.strategies.collectAsState()

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            DpiEngine.startNow()
        } else {
            context.toast("Без разрешения VPN ускорение не включить")
        }
    }

    val toggleDpi: () -> Unit = {
        if (dpi.running || dpi.busy) {
            DpiEngine.stop()
        } else {
            val intent = VpnService.prepare(context)
            if (intent != null) vpnLauncher.launch(intent) else DpiEngine.startNow()
        }
    }
    val toggleTg: () -> Unit = {
        if (tg.running || tg.busy) TgProxyController.stop() else TgProxyController.startNow()
    }

    // Диалог энергосбережения: без него система усыпляет сервисы туннеля.
    var showPowerDialog by rememberSaveable {
        mutableStateOf(!Power.isIgnoring(context) && !settings.powerPromptShown)
    }
    if (showPowerDialog) {
        AlertDialog(
            onDismissRequest = {
                showPowerDialog = false
                vm.updateSettings { it.copy(powerPromptShown = true) }
            },
            title = { Text("Разрешите работу в фоне") },
            text = {
                Column {
                    if (LocalNewYear.current) NewYearGarland(compact = true)
                    Text(
                        "Android ограничивает фоновые приложения и может остановить ускорение соединения или " +
                            "Telegram-прокси при выключенном экране.\n\n" +
                            Power.vendorHint(context) +
                            "\n\nЭто не влияет на другие приложения.",
                    )
                }
            },
            confirmButton = {
                androidx.compose.foundation.layout.Row {
                    TextButton(onClick = {
                        Power.openAutostartSettings(context)
                        vm.updateSettings { it.copy(powerPromptShown = true) }
                        showPowerDialog = false
                    }) { Text("Автозапуск") }
                    TextButton(onClick = {
                        Power.openPowerSettings(context)
                        vm.updateSettings { it.copy(powerPromptShown = true) }
                        showPowerDialog = false
                    }) { Text("Батарея") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.updateSettings { it.copy(powerPromptShown = true) }
                    showPowerDialog = false
                }) { Text("Позже") }
            },
        )
    }

    val backTarget = when (current) {
        "autotune" -> "strategies"
        "diagnostics", "lists", "sitecheck", "network", "hosts" -> "settings"
        else -> null
    }
    BackHandler(enabled = backTarget != null) {
        backTarget?.let { current = it }
    }

    val newYear = LocalNewYear.current
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = ZmColors.background,
        topBar = {
            if (newYear) {
                Column {
                    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                    NewYearGarland()
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = ZmColors.surface) {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = current == destination.route,
                        onClick = {
                            current = destination.route
                            if (destination.route == "settings") settingsSection = null
                        },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (destination.route == "settings" && updates.appUpdate != null) {
                                        Badge()
                                    }
                                },
                            ) {
                                Icon(destination.icon, contentDescription = destination.label)
                            }
                        },
                        label = {
                            Text(
                                destination.label,
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ZmColors.accent,
                            selectedTextColor = ZmColors.accent,
                            indicatorColor = ZmColors.accent.copy(alpha = 0.12f),
                            unselectedIconColor = ZmColors.textSecondary,
                            unselectedTextColor = ZmColors.textSecondary,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    // Мягкий сдвиг вбок + проявление: переключение вкладок не «прыгает».
                    (fadeIn(tween(200)) + slideInHorizontally(tween(220)) { it / 14 })
                        .togetherWith(fadeOut(tween(140)))
                },
                label = "screenTransition",
            ) { route ->
            when (route) {
                "dash" -> DashboardScreen(
                    dpiPhase = dpi.phase,
                    dpiState = dpi,
                    tgPhase = tg.phase,
                    settings = settings,
                    tgStatus = tg,
                    appUpdateVersion = updates.appUpdate?.version,
                    onToggleDpi = toggleDpi,
                    onToggleTg = toggleTg,
                    onNavigate = { route ->
                        if (route == "updates") {
                            settingsSection = "updates"
                            current = "settings"
                        } else {
                            current = route
                        }
                    },
                )
                "strategies" -> StrategiesScreen(
                    vm = vm,
                    strategies = strategies,
                    settings = settings,
                    onOpenAutotune = { current = "autotune" },
                )
                "telegram" -> TelegramScreen(vm, tg, settings)
                "autotune" -> AutotuneScreen(vm) { current = "strategies" }
                "settings" -> SettingsScreen(
                    vm = vm,
                    settings = settings,
                    updates = updates,
                    section = settingsSection,
                    onSectionChange = { settingsSection = it },
                    onOpenDiagnostics = { current = "diagnostics" },
                    onOpenLists = { current = "lists" },
                    onOpenSiteCheck = { current = "sitecheck" },
                    onOpenNetwork = { current = "network" },
                    onOpenHosts = { current = "hosts" },
                )
                "diagnostics" -> DiagnosticsScreen(vm) { current = "settings" }
                "lists" -> SiteListsScreen { current = "settings" }
                "sitecheck" -> SiteCheckScreen { current = "settings" }
                "network" -> NetworkProfilesScreen(vm) { current = "settings" }
                "hosts" -> HostsScreen(vm) { current = "settings" }
            }
            }
        }
    }
    // Снег поверх всего окна, включая меню: он копится на его верхней кромке.
    // Касания не перехватывает — Canvas без обработчиков.
    if (newYear) NewYearSnow()
    }

    // Пока открыт системный установщик, поверх приложения висит окно
    // «Идёт обновление…» с притемнённым фоном: понятно, что установка уже началась.
    if (updates.installerOpen) {
        Dialog(onDismissRequest = { vm.dismissInstallerNotice() }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ZmColors.surface, RoundedCornerShape(20.dp))
                    .padding(18.dp),
            ) {
                if (LocalNewYear.current) NewYearGarland(compact = true)
                Text("Идёт обновление…", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Открыт системный установщик — подтвердите установку. После обновления " +
                        "приложение откроется заново.",
                    fontSize = 12.sp,
                    color = ZmColors.textSecondary,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
                PrimaryButton("Понятно", modifier = Modifier.fillMaxWidth()) {
                    vm.dismissInstallerNotice()
                }
            }
        }
    }

    // Фоновые проверки при первом входе — не чаще раза в 12 часов.
    LaunchedEffect(Unit) {
        if (settings.checkUpdatesOnStart) vm.refreshUpdatesOnLaunch()
    }
}