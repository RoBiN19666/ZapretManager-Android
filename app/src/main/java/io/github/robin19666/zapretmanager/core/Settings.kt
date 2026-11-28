package io.github.robin19666.zapretmanager.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SplitMode { ALL, EXCLUDE, INCLUDE }

data class AppSettings(
    val strategyId: String = "general",
    val customArgs: String = "",
    val autostartDpi: Boolean = false,
    val autostartTg: Boolean = false,
    val vpnIpv6: Boolean = false,
    val vpnDns: String = "1.1.1.1",
    val splitMode: SplitMode = SplitMode.ALL,
    val splitApps: Set<String> = emptySet(),
    val tgPort: Int = 1443,
    val tgRoute: String = "cf_proxy_ws",
    val tgMaskingDomain: String = "",
    val tgAutoApply: Boolean = false,
    val tgSecret: String = "",
    val checkUpdatesOnStart: Boolean = true,
    val autoUpdateComponents: Boolean = true,
    val themeMode: String = "system",
    val powerPromptShown: Boolean = false,
    val networkAutoSwitch: Boolean = true,
    val wifiLossDelaySec: Int = 8,
    val accentColor: Long = 0xFF31D0AA,
    val amoled: Boolean = false,
    val hostsMode: String = "all",
    val watchdogEnabled: Boolean = false,
    val autostartOnLaunch: Boolean = false,
    val autostartTgOnLaunch: Boolean = false,
)

/**
 * Единое хранилище настроек с реактивным потоком.
 * Пишут и интерфейс, и сервисы, поэтому доступ идёт через объект-синглтон.
 */
object Settings {
    private const val FILE = "zm_prefs"
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(AppSettings())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    /**
     * Настройки меняются из UI, сторожа, наблюдателя сети и сервисов — это
     * классический read-modify-write, который без блокировки теряет записи.
     */
    private val lock = Any()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        reload(context)
    }

    /**
     * Перечитывает настройки из SharedPreferences. Нужно сервисам в отдельных
     * процессах (:tg), которые могут быть переиспользованы системой после
     * остановки и не должны работать со старым снимком настроек.
     */
    fun reload(context: Context) {
        synchronized(lock) {
            prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            _state.value = read()
        }
    }

    fun current(): AppSettings = _state.value

    fun update(transform: (AppSettings) -> AppSettings) {
        synchronized(lock) {
            val next = transform(_state.value)
            _state.value = next
            write(next)
        }
    }

    private fun read(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            strategyId = prefs.getString("strategyId", d.strategyId) ?: d.strategyId,
            customArgs = prefs.getString("customArgs", d.customArgs) ?: d.customArgs,
            autostartDpi = prefs.getBoolean("autostartDpi", d.autostartDpi),
            autostartTg = prefs.getBoolean("autostartTg", d.autostartTg),
            vpnIpv6 = prefs.getBoolean("vpnIpv6", d.vpnIpv6),
            vpnDns = prefs.getString("vpnDns", d.vpnDns) ?: d.vpnDns,
            splitMode = runCatching { SplitMode.valueOf(prefs.getString("splitMode", d.splitMode.name)!!) }
                .getOrDefault(d.splitMode),
            splitApps = prefs.getStringSet("splitApps", emptySet())?.toSet() ?: emptySet(),
            tgPort = prefs.getInt("tgPort", d.tgPort),
            tgRoute = prefs.getString("tgRoute", d.tgRoute) ?: d.tgRoute,
            tgMaskingDomain = prefs.getString("tgMaskingDomain", d.tgMaskingDomain) ?: d.tgMaskingDomain,
            tgAutoApply = prefs.getBoolean("tgAutoApply", d.tgAutoApply),
            tgSecret = prefs.getString("tgSecret", d.tgSecret) ?: d.tgSecret,
            themeMode = prefs.getString("themeMode", null)
                ?: if (prefs.contains("themeDark")) {
                    if (prefs.getBoolean("themeDark", true)) "dark" else "light"
                } else {
                    d.themeMode
                },
            checkUpdatesOnStart = prefs.getBoolean("checkUpdatesOnStart", d.checkUpdatesOnStart),
            autoUpdateComponents = prefs.getBoolean("autoUpdateComponents", d.autoUpdateComponents),
            powerPromptShown = prefs.getBoolean("powerPromptShown", d.powerPromptShown),
            networkAutoSwitch = prefs.getBoolean("networkAutoSwitch", d.networkAutoSwitch),
            wifiLossDelaySec = prefs.getInt("wifiLossDelaySec", d.wifiLossDelaySec),
            accentColor = prefs.getLong("accentColor", d.accentColor),
            amoled = prefs.getBoolean("amoled", d.amoled),
            hostsMode = prefs.getString("hostsMode", d.hostsMode) ?: d.hostsMode,
            watchdogEnabled = prefs.getBoolean("watchdogEnabled", d.watchdogEnabled),
            autostartOnLaunch = prefs.getBoolean("autostartOnLaunch", d.autostartOnLaunch),
            autostartTgOnLaunch = prefs.getBoolean("autostartTgOnLaunch", d.autostartTgOnLaunch),
        )
    }

    /**
     * Запись идёт двумя пачками:
     *
     * * **движковые** ключи (стратегия, VPN, сплит-туннель, Telegram) читают
     *   процессы `:native` и `:tg` из своего экземпляра SharedPreferences,
     *   поэтому они сохраняются через `commit()` — асинхронная запись могла бы
     *   не успеть до старта сервиса;
     * * **интерфейсные** ключи (тема, акцент, автозапуски, сторож, обновления)
     *   читает только основной процесс, там достаточно `apply()`: он сразу
     *   обновляет in-memory карту, а на диск пишет в фоне и не блокирует
     *   главный поток на каждом тапе по чипу/свитчу.
     */
    @Suppress("ApplySharedPref") // commit() намеренно: ключи читают процессы :native и :tg
    private fun write(s: AppSettings) {
        if (!::prefs.isInitialized) return

        prefs.edit()
            // Движковые ключи — синхронно, их читают :native и :tg.
            .putString("strategyId", s.strategyId)
            .putString("customArgs", s.customArgs)
            .putBoolean("vpnIpv6", s.vpnIpv6)
            .putString("vpnDns", s.vpnDns)
            .putString("splitMode", s.splitMode.name)
            .putStringSet("splitApps", s.splitApps)
            .putString("hostsMode", s.hostsMode)
            .putInt("tgPort", s.tgPort)
            .putString("tgRoute", s.tgRoute)
            .putString("tgMaskingDomain", s.tgMaskingDomain)
            .putBoolean("tgAutoApply", s.tgAutoApply)
            .putString("tgSecret", s.tgSecret)
            .commit()

        prefs.edit()
            // Интерфейсные и «основной процесс» ключи — асинхронно.
            .putBoolean("autostartDpi", s.autostartDpi)
            .putBoolean("autostartTg", s.autostartTg)
            .putString("themeMode", s.themeMode)
            .putBoolean("themeDark", s.themeMode == "dark")
            .putBoolean("checkUpdatesOnStart", s.checkUpdatesOnStart)
            .putBoolean("autoUpdateComponents", s.autoUpdateComponents)
            .putBoolean("powerPromptShown", s.powerPromptShown)
            .putBoolean("networkAutoSwitch", s.networkAutoSwitch)
            .putInt("wifiLossDelaySec", s.wifiLossDelaySec)
            .putLong("accentColor", s.accentColor)
            .putBoolean("amoled", s.amoled)
            .putBoolean("watchdogEnabled", s.watchdogEnabled)
            .putBoolean("autostartOnLaunch", s.autostartOnLaunch)
            .putBoolean("autostartTgOnLaunch", s.autostartTgOnLaunch)
            .apply()
    }
}
