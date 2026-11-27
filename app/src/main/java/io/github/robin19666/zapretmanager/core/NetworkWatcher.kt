package io.github.robin19666.zapretmanager.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import io.github.robin19666.zapretmanager.byedpi.DpiEngine
import io.github.robin19666.zapretmanager.tgproxy.TgProxyController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class SwitchAction { NONE, IMMEDIATE, DELAYED }

/**
 * Правила переключения конфига при смене сети — чистая логика без Android API,
 * чтобы её можно было покрыть unit-тестами.
 */
object NetworkSwitchPolicy {
    fun decide(
        previousKey: String?,
        previousKind: String?,
        next: NetworkSnapshot,
    ): SwitchAction {
        if (next.kind == NetworkSnapshot.KIND_NONE) return SwitchAction.NONE
        if (previousKey == null) return SwitchAction.NONE
        if (previousKey == next.matchKey) return SwitchAction.NONE
        if (next.isWifi) return SwitchAction.IMMEDIATE
        if (next.isMobile && previousKind == NetworkSnapshot.KIND_WIFI) {
            // Wi-Fi ещё включён — вероятно, кратковременный обрыв: ждём, чтобы не дёргать конфиг.
            return if (next.wifiEnabled) SwitchAction.DELAYED else SwitchAction.IMMEDIATE
        }
        return SwitchAction.IMMEDIATE
    }
}

/**
 * Следит за сменой сети и применяет профиль.
 *
 * Правила:
 *  • Wi-Fi пропал, но остаётся включённым (обрыв/переподключение) — переключаемся с задержкой,
 *    чтобы не дёргать конфиг туда-сюда;
 *  • Wi-Fi выключили — переключаемся на мобильный профиль сразу;
 *  • Wi-Fi вернулся — сразу возвращаем Wi-Fi-профиль;
 *  • смена оператора или переход Wi-Fi → Wi-Fi — сразу.
 */
object NetworkWatcher {
    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var lastSnapshot: NetworkSnapshot? = null
    private var pendingSwitch: Runnable? = null

    fun start(context: Context) {
        if (callback != null) return
        val app = context.applicationContext
        appContext = app
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = handle()
            override fun onLost(network: Network) = handle()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = handle()
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }
            .onSuccess {
                callback = cb
                // Не переключаем сразу при старте приложения: сначала запоминаем сеть.
                lastSnapshot = NetworkInfo.current(app)
                LogBus.d("Сеть", "Наблюдение запущено: ${lastSnapshot?.label}")
            }
            .onFailure { LogBus.w("Сеть", "Не удалось подписаться на смену сети: ${it.message}") }
    }

    fun stop() {
        val cm = appContext?.getSystemService(ConnectivityManager::class.java)
        callback?.let { runCatching { cm?.unregisterNetworkCallback(it) } }
        callback = null
        cancelPending()
        lastSnapshot = null
    }

    fun currentSnapshot(): NetworkSnapshot {
        val app = appContext ?: return NetworkSnapshot.NONE
        return NetworkInfo.current(app)
    }

    private fun handle() {
        val app = appContext ?: return
        val next = NetworkInfo.current(app)
        val previous = lastSnapshot
        val action = decide(previous?.matchKey, previous?.kind, next)
        lastSnapshot = next
        when (action) {
            SwitchAction.NONE -> Unit
            SwitchAction.IMMEDIATE -> {
                cancelPending()
                applyFor(app, next)
            }
            SwitchAction.DELAYED -> {
                cancelPending()
                val delayMs = Settings.current().wifiLossDelaySec.coerceIn(3, 60) * 1000L
                LogBus.i(
                    "Сеть",
                    "Wi-Fi пропал, но включён — переключусь через ${delayMs / 1000} с, если не вернётся",
                )
                val runnable = Runnable { applyFor(app, next) }
                pendingSwitch = runnable
                handler.postDelayed(runnable, delayMs)
            }
        }
    }

    private fun cancelPending() {
        pendingSwitch?.let { handler.removeCallbacks(it) }
        pendingSwitch = null
    }

    /** Чистая функция решения — покрыта unit-тестами. */
    internal fun decide(
        previousKey: String?,
        previousKind: String?,
        next: NetworkSnapshot,
    ): SwitchAction = NetworkSwitchPolicy.decide(previousKey, previousKind, next)

    private fun applyFor(context: Context, snapshot: NetworkSnapshot) {
        val settings = Settings.current()
        if (!settings.networkAutoSwitch) {
            LogBus.d("Сеть", "Автопереключение выключено")
            return
        }
        val profile = NetworkProfiles.match(NetworkProfiles.all(context), snapshot) ?: run {
            LogBus.i("Сеть", "${snapshot.label}: профиль не задан, конфиг не меняю")
            return
        }

        val sameStrategy = settings.strategyId == profile.strategyId &&
            settings.customArgs == profile.customArgs
        val sameTg = settings.tgPort == profile.tgPort &&
            settings.tgRoute == profile.tgRoute &&
            settings.tgMaskingDomain == profile.tgMaskingDomain
        if (sameStrategy && sameTg) {
            LogBus.d("Сеть", "${snapshot.label}: профиль «${profile.label}» уже активен")
            return
        }

        Settings.update {
            it.copy(
                strategyId = profile.strategyId,
                customArgs = profile.customArgs,
                tgPort = profile.tgPort,
                tgRoute = profile.tgRoute,
                tgMaskingDomain = profile.tgMaskingDomain,
            )
        }
        LogBus.i("Сеть", "${snapshot.label}: применён профиль «${profile.label}»")

        if (DpiEngine.state.value.running || DpiEngine.state.value.phase == EnginePhase.STARTING) {
            if (!sameStrategy) restartDpi()
        }
        if (TgProxyController.state.value.running && !sameTg) {
            TgProxyController.restart()
        }
    }

    private fun restartDpi() {
        ZmScope.launch {
            DpiEngine.stop()
            val deadline = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < deadline &&
                DpiEngine.state.value.phase != EnginePhase.STOPPED &&
                DpiEngine.state.value.phase != EnginePhase.ERROR
            ) {
                delay(200)
            }
            DpiEngine.startNow()
        }
    }
}
