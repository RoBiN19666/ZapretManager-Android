package io.github.robin19666.zapretmanager.ui

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.sp
import io.github.robin19666.zapretmanager.byedpi.DpiEngine
import io.github.robin19666.zapretmanager.core.EnginePhase
import io.github.robin19666.zapretmanager.core.Power
import io.github.robin19666.zapretmanager.tgproxy.TgProxyController
import io.github.robin19666.zapretmanager.ui.screens.DashboardScreen
import io.github.robin19666.zapretmanager.ui.screens.AutotuneScreen
import io.github.robin19666.zapretmanager.ui.screens.DiagnosticsScreen
import io.github.robin19666.zapretmanager.ui.screens.SettingsScreen
import io.github.robin19666.zapretmanager.ui.screens.StrategiesScreen
import io.github.robin19666.zapretmanager.ui.screens.TelegramScreen
import io.github.robin19666.zapretmanager.ui.screens.UpdatesScreen
import io.github.robin19666.zapretmanager.ui.theme.ZmColors

private data class Destination(val route: String, val label: String, val icon: ImageVector)

private val destinations = listOf(
    Destination("dash", "Обзор", Icons.Rounded.Home),
    Destination("strategies", "Стратегии", Icons.Rounded.Tune),
    Destination("telegram", "Telegram", Icons.Rounded.Send),
    Destination("updates", "Релизы", Icons.Rounded.SystemUpdate),
    Destination("settings", "Ещё", Icons.Rounded.Settings),
)

@Composable
fun ZmApp(vm: ZmViewModel) {
    val context = LocalContext.current
    var current by rememberSaveable { mutableStateOf("dash") }

    val dpi by vm.dpi.collectAsState()
    val tg by vm.tg.collectAsState()
    val settings by vm.settings.collectAsState()
    val updates by vm.updates.collectAsState()
    val strategies by vm.strategies.collectAsState()
    val logs by vm.logs.collectAsState()

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            DpiEngine.startNow()
        } else {
            context.toast("Без разрешения VPN обход не включить")
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
                Text(
                    "Android ограничивает фоновые приложения и может остановить обход DPI или " +
                        "Telegram-прокси при выключенном экране. Чтобы соединение не рвалось, " +
                        "разрешите приложению работу без ограничений: Настройки → Батарея → " +
                        "«Без ограничений».\n\nЭто не влияет на другие приложения.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Power.open(context)
                    vm.updateSettings { it.copy(powerPromptShown = true) }
                    showPowerDialog = false
                }) { Text("Открыть настройки") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.updateSettings { it.copy(powerPromptShown = true) }
                    showPowerDialog = false
                }) { Text("Позже") }
            },
        )
    }

    Scaffold(
        containerColor = ZmColors.background,
        bottomBar = {
            NavigationBar(containerColor = ZmColors.surface) {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = current == destination.route,
                        onClick = { current = destination.route },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
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
            when (current) {
                "dash" -> DashboardScreen(
                    vm = vm,
                    dpiPhase = dpi.phase,
                    tgPhase = tg.phase,
                    settings = settings,
                    tgStatus = tg,
                    logs = logs,
                    appUpdateVersion = updates.appUpdate?.version,
                    onToggleDpi = toggleDpi,
                    onToggleTg = toggleTg,
                    onNavigate = { current = it },
                )
                "strategies" -> StrategiesScreen(
                    vm = vm,
                    strategies = strategies,
                    settings = settings,
                    onOpenAutotune = { current = "autotune" },
                )
                "telegram" -> TelegramScreen(vm, tg, settings)
                "updates" -> UpdatesScreen(vm, updates)
                "autotune" -> AutotuneScreen(vm) { current = "strategies" }
                "settings" -> SettingsScreen(
                    vm = vm,
                    settings = settings,
                    onOpenDiagnostics = { current = "diagnostics" },
                )
                "diagnostics" -> DiagnosticsScreen(vm) { current = "settings" }
            }
        }
    }

    // Фоновые проверки при первом входе.
    LaunchedEffect(Unit) {
        if (settings.checkUpdatesOnStart) vm.refreshUpdates()
    }
}