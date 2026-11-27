package io.github.robin19666.zapretmanager.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import io.github.robin19666.zapretmanager.ui.theme.ZapretManagerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ZmViewModel by viewModels()

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* уведомления не обязательны для работы */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotifications()
        setContent {
            val settings by viewModel.settings.collectAsState()
            val darkTheme = when (settings.themeMode) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            // Иконки системных панелей должны быть тёмными на светлой теме и наоборот.
            androidx.compose.runtime.SideEffect {
                val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
            androidx.compose.runtime.LaunchedEffect(settings.autostartOnLaunch) {
                if (settings.autostartOnLaunch &&
                    io.github.robin19666.zapretmanager.byedpi.DpiEngine.prepareIntent(this@MainActivity) == null &&
                    !io.github.robin19666.zapretmanager.byedpi.DpiEngine.state.value.running
                ) {
                    io.github.robin19666.zapretmanager.byedpi.DpiEngine.startNow()
                }
                if (settings.autostartTgOnLaunch &&
                    !io.github.robin19666.zapretmanager.tgproxy.TgProxyController.state.value.running
                ) {
                    io.github.robin19666.zapretmanager.tgproxy.TgProxyService.start(this@MainActivity)
                }
            }
            ZapretManagerTheme(
                darkTheme = darkTheme,
                accentColor = settings.accentColor,
                amoled = settings.amoled,
            ) {
                ZmApp(viewModel)
            }
        }
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}