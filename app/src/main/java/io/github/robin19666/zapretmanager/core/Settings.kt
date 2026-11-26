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
    val tgFrontend: String = "mtproto",
    val tgRoute: String = "cf_proxy_ws",
    val tgMaskingDomain: String = "",
    val tgAutoApply: Boolean = false,
    val tgSecret: String = "",
    val checkUpdatesOnStart: Boolean = true,
    val autoUpdateComponents: Boolean = true,
    val logVerbose: Boolean = false,
    val notifyOnStart: Boolean = true,
    val themeMode: String = "system",
    val powerPromptShown: Boolean = false,
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

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        _state.value = read()
    }

    fun current(): AppSettings = _state.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        _state.value = next
        write(next)
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
            tgFrontend = prefs.getString("tgFrontend", d.tgFrontend) ?: d.tgFrontend,
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
            logVerbose = prefs.getBoolean("logVerbose", d.logVerbose),
            notifyOnStart = prefs.getBoolean("notifyOnStart", d.notifyOnStart),
            powerPromptShown = prefs.getBoolean("powerPromptShown", d.powerPromptShown),
        )
    }

    private fun write(s: AppSettings) {
        if (!::prefs.isInitialized) return
        prefs.edit()
            .putString("strategyId", s.strategyId)
            .putString("customArgs", s.customArgs)
            .putBoolean("autostartDpi", s.autostartDpi)
            .putBoolean("autostartTg", s.autostartTg)
            .putBoolean("vpnIpv6", s.vpnIpv6)
            .putString("vpnDns", s.vpnDns)
            .putString("splitMode", s.splitMode.name)
            .putStringSet("splitApps", s.splitApps)
            .putInt("tgPort", s.tgPort)
            .putString("tgFrontend", s.tgFrontend)
            .putString("tgRoute", s.tgRoute)
            .putString("tgMaskingDomain", s.tgMaskingDomain)
            .putBoolean("tgAutoApply", s.tgAutoApply)
            .putString("tgSecret", s.tgSecret)
            .putString("themeMode", s.themeMode)
            .putBoolean("checkUpdatesOnStart", s.checkUpdatesOnStart)
            .putBoolean("autoUpdateComponents", s.autoUpdateComponents)
            .putBoolean("logVerbose", s.logVerbose)
            .putBoolean("notifyOnStart", s.notifyOnStart)
            .putBoolean("themeDark", s.themeMode == "dark")
            .putBoolean("powerPromptShown", s.powerPromptShown)
            .apply()
    }
}
