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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import io.github.robin19666.zapretmanager.byedpi.DpiEngine
import io.github.robin19666.zapretmanager.core.EnginePhase
import io.github.robin19666.zapretmanager.tgproxy.TgProxyController
import io.github.robin19666.zapretmanager.ui.screens.DashboardScreen
import io.github.robin19666.zapretmanager.ui.screens.SettingsScreen
import io.github.robin19666.zapretmanager.ui.screens.StrategiesScreen
import io.github.robin19666.zapretmanager.ui.screens.TelegramScreen
import io.github.robin19666.zapretmanager.ui.screens.UpdatesScreen
import io.github.robin19666.zapretmanager.ui.theme.ZmAccent
import io.github.robin19666.zapretmanager.ui.theme.ZmBackground
import io.github.robin19666.zapretmanager.ui.theme.ZmSurface

private data class Destination(val route: String, val label: String, val icon: ImageVector)

private val destinations = listOf(
    Destination("dash", "Обзор", Icons.Rounded.Home),
    Destination("strategies", "Стратегии", Icons.Rounded.Tune),
    Destination("telegram", "Telegram", Icons.Rounded.Send),
    Destination("updates", "Обновления", Icons.Rounded.SystemUpdate),
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

    Scaffold(
        containerColor = ZmBackground,
        bottomBar = {
            NavigationBar(containerColor = ZmSurface) {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = current == destination.route,
                        onClick = { current = destination.route },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ZmAccent,
                            selectedTextColor = ZmAccent,
                            indicatorColor = ZmAccent.copy(alpha = 0.12f),
                            unselectedIconColor = io.github.robin19666.zapretmanager.ui.theme.ZmTextSecondary,
                            unselectedTextColor = io.github.robin19666.zapretmanager.ui.theme.ZmTextSecondary,
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
                "strategies" -> StrategiesScreen(vm, strategies, settings)
                "telegram" -> TelegramScreen(vm, tg, settings)
                "updates" -> UpdatesScreen(vm, updates)
                "settings" -> SettingsScreen(vm, settings)
            }
        }
    }

    // Фоновые проверки при первом входе.
    LaunchedEffect(Unit) {
        if (settings.checkUpdatesOnStart) vm.refreshUpdates()
    }
}
