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
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Порядок экранов слева направо для направленных переходов.
 * Вкладки идут с шагом 10, вложенная страница — +1 (углубление).
 */
private fun screenDepth(route: String, section: String?): Int = when (route) {
    "dash" -> 0
    "strategies" -> 10
    "autotune" -> 11
    "telegram" -> 20
    "settings" -> 30 + if (section != null) 1 else 0
    "diagnostics", "lists", "sitecheck", "network", "hosts" -> 31
    else -> 30
}

private val destinations = listOf(
    ZmDestination("dash", "Обзор", NavGlyph.HOME),
    ZmDestination("strategies", "Стратегии", NavGlyph.SLIDERS),
    ZmDestination("telegram", "Telegram", NavGlyph.PLANE),
    ZmDestination("settings", "Ещё", NavGlyph.GEAR),
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
            ZmBottomBar(
                items = destinations,
                current = current,
                updateBadge = updates.appUpdate != null,
                onSelect = { route ->
                    current = route
                    if (route == "settings") settingsSection = null
                },
            )
        },
    ) { padding ->
        // Содержимое тянется до низа экрана и прокручивается под плавающим меню:
        // снизу отступ не режем, а отдаём его спискам как contentPadding.
        CompositionLocalProvider(
            // Высота панели вместе с системным отступом: её списки добавляют снизу,
            // чтобы последний элемент можно было доскроллить.
            LocalNavBarInset provides padding.calculateBottomPadding(),
        ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            // В ключ входит и подраздел «Ещё»: проваливание в пункты меню и возврат
            // тоже анимируются, а не подменяют экран мгновенно.
            AnimatedContent(
                targetState = current to settingsSection,
                transitionSpec = {
                    // Направление зависит от порядка разделов: вправо по ленте — новый
                    // экран въезжает справа, влево — слева, старый уходит в обратную сторону.
                    val from = screenDepth(initialState.first, initialState.second)
                    val to = screenDepth(targetState.first, targetState.second)
                    val direction = if (to >= from) 1 else -1
                    // Сначала старая страница уезжает и гаснет, затем проявляется новая —
                    // получается «исчезновение и появление», а не перелистывание.
                    val enter = slideInHorizontally(
                        tween(durationMillis = 320, easing = FastOutSlowInEasing),
                    ) { width -> direction * width / 2 } +
                        fadeIn(tween(durationMillis = 240, delayMillis = 80))
                    val exit = slideOutHorizontally(
                        tween(durationMillis = 260, easing = FastOutLinearInEasing),
                    ) { width -> -direction * width / 3 } +
                        fadeOut(tween(durationMillis = 150))
                    enter togetherWith exit
                },
                label = "screenTransition",
            ) { screen ->
            val route = screen.first
            val section = screen.second
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
                    section = section,
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

    // Автоприменение ссылки делает UI-процесс: в :tg SharedPreferences кэшируются
    // на процесс и не видят изменений из UI, из-за чего выключенная настройка
    // «Открывать Telegram при старте» продолжала открывать Telegram.
    var appliedForStart by rememberSaveable { mutableStateOf(0L) }
    var previousTgPhase by remember { mutableStateOf(tg.phase) }
    LaunchedEffect(tg.phase, tg.since) {
        val wasRunning = previousTgPhase == EnginePhase.RUNNING
        previousTgPhase = tg.phase
        // Реагируем только на запуск, а не на «прокси уже работал, когда мы открыли приложение».
        if (tg.phase == EnginePhase.RUNNING && !wasRunning &&
            tg.port > 0 && tg.since != appliedForStart
        ) {
            appliedForStart = tg.since
            if (settings.tgAutoApply) {
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(TgProxyController.link()),
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
    }

    // Фоновые проверки при первом входе — не чаще раза в 12 часов.
    LaunchedEffect(Unit) {
        if (settings.checkUpdatesOnStart) vm.refreshUpdatesOnLaunch()
    }
}