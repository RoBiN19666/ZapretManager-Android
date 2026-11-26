package io.github.robin19666.zapretmanager.byedpi

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.core.app.ServiceCompat
import io.github.robin19666.zapretmanager.R
import io.github.robin19666.zapretmanager.core.EnginePhase
import io.github.robin19666.zapretmanager.core.LogBus
import io.github.robin19666.zapretmanager.core.Notifications
import io.github.robin19666.zapretmanager.core.Settings
import io.github.robin19666.zapretmanager.core.SplitMode
import java.io.FileDescriptor
import java.net.InetSocketAddress
import java.net.Socket

/**
 * VpnService-обход DPI: tun → hev-socks5-tunnel → byedpi (desync) → интернет.
 *
 * Живёт в отдельном процессе :native, потому что byedpi — это программа с
 * блокирующим main() и без корректного завершения. Остановка = завершение
 * процесса; UI-процесс при этом не страдает.
 */
class ByeDpiVpnService : VpnService() {

    companion object {
        const val ACTION_START = "io.github.robin19666.zapretmanager.byedpi.START"
        const val ACTION_STOP = "io.github.robin19666.zapretmanager.byedpi.STOP"
        const val ACTION_STATE = "io.github.robin19666.zapretmanager.byedpi.STATE"
        const val EXTRA_PHASE = "phase"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_EXTRA = "extra"
        const val NOTIFICATION_ID = 4201
        const val SOCKS_HOST = "127.0.0.1"
        const val SOCKS_PORT = 1080
        private const val SOCKS_READY_TIMEOUT_MS = 12_000L

        fun start(context: Context) {
            val intent = Intent(context, ByeDpiVpnService::class.java).setAction(ACTION_START)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ByeDpiVpnService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }

        fun broadcast(context: Context, phase: EnginePhase, message: String, extra: String = "") {
            val intent = Intent(ACTION_STATE)
                .setPackage(context.packageName)
                .putExtra(EXTRA_PHASE, phase.name)
                .putExtra(EXTRA_MESSAGE, message)
                .putExtra(EXTRA_EXTRA, extra)
            context.sendBroadcast(intent)
        }
    }

    private var tun: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    @Volatile
    private var stopping = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startEngine()
            ACTION_STOP -> {
                stopEngine()
                return START_NOT_STICKY
            }
            else -> {
                if (tun == null) {
                    LogBus.d("Обход", "Сервис перезапущен системой без действия — останавливаюсь")
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startEngine() {
        if (worker != null) {
            LogBus.d("Обход", "Движок уже запускается/работает")
            return
        }
        stopping = false
        startForegroundSafely("Запуск обхода DPI…")
        broadcast(this, EnginePhase.STARTING, "Подготовка туннеля")

        worker = Thread({ runEngine() }, "zm-dpi-engine").apply { start() }
    }

    private fun runEngine() {
        val settings = Settings.current()
        try {
            if (!Native.ensureLoaded()) {
                fail("Нативный движок недоступен: ${Native.unavailableReason() ?: "неизвестная ошибка"}")
                return
            }
            val strategy = Strategies.resolve(this, settings.strategyId)
            val tokens = Strategies.tokens(this, settings.strategyId, settings.customArgs)
            val argv = ArrayList<String>()
            argv += "ciadpi"
            argv += "--ip"
            argv += SOCKS_HOST
            argv += "--port"
            argv += SOCKS_PORT.toString()
            argv += tokens
            LogBus.i("Обход", "Стратегия: ${strategy.title} → ${argv.joinToString(" ")}")

            val descriptor = establishTunnel(settings) ?: run {
                fail("Android не выдал VPN-разрешение")
                return
            }
            tun = descriptor

            val result = Native.startByedpi(argv.toTypedArray())
            if (result != 0) {
                fail("byedpi не запустился (код $result): ${Native.lastError()}")
                return
            }
            if (!awaitSocks()) {
                fail("byedpi не открыл SOCKS5-порт $SOCKS_PORT за ${SOCKS_READY_TIMEOUT_MS / 1000} с: ${Native.lastError()}")
                return
            }

            val yaml = buildString {
                appendLine("tunnel:")
                appendLine("  mtu: 8500")
                appendLine("misc:")
                appendLine("  task-stack-size: 81920")
                appendLine("socks5:")
                appendLine("  address: $SOCKS_HOST")
                appendLine("  port: $SOCKS_PORT")
                appendLine("  udp: udp")
            }
            val tunnelResult = Native.startTunnel(yaml, descriptor.fd)
            if (tunnelResult != 0) {
                fail("tun2socks не запустился (код $tunnelResult): ${Native.lastError()}")
                return
            }

            updateNotification("Обход DPI активен", strategy.title)
            broadcast(this, EnginePhase.RUNNING, "Обход DPI активен", strategy.title)
            LogBus.i("Обход", "Туннель поднят, стратегия «${strategy.title}»")

            // Контроль живости: если byedpi внезапно завершился — сообщаем наверх.
            while (!stopping) {
                Thread.sleep(1500)
                if (!Native.byedpiRunning()) {
                    if (!stopping) fail("byedpi завершился: ${Native.lastError()}")
                    break
                }
            }
        } catch (t: Throwable) {
            if (!stopping) fail(t.message ?: t.javaClass.simpleName)
        }
    }

    private fun establishTunnel(settings: io.github.robin19666.zapretmanager.core.AppSettings): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .addAddress("10.10.10.10", 32)
            .addRoute("0.0.0.0", 0)
        if (settings.vpnIpv6) {
            runCatching {
                builder.addAddress("fd00::1", 128)
                builder.addRoute("::", 0)
            }
        }
        settings.vpnDns.split(',', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { dns -> runCatching { builder.addDnsServer(dns) } }

        when (settings.splitMode) {
            SplitMode.ALL -> builder.addDisallowedApplication(packageName)
            SplitMode.EXCLUDE -> {
                settings.splitApps.forEach { pkg -> runCatching { builder.addDisallowedApplication(pkg) } }
                runCatching { builder.addDisallowedApplication(packageName) }
            }
            SplitMode.INCLUDE -> settings.splitApps.forEach { pkg ->
                runCatching { builder.addAllowedApplication(pkg) }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }
        builder.setConfigureIntent(Notifications.contentIntent(this))
        return builder.establish()
    }

    private fun awaitSocks(): Boolean {
        val deadline = System.currentTimeMillis() + SOCKS_READY_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (stopping) return false
            if (!Native.byedpiRunning()) return false
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(SOCKS_HOST, SOCKS_PORT), 250)
                    return true
                }
            } catch (_: Exception) {
                Thread.sleep(120)
            }
        }
        return false
    }

    private fun fail(message: String) {
        LogBus.e("Обход", message)
        stopping = true
        updateNotification("Обход DPI остановлен", message)
        broadcast(this, EnginePhase.ERROR, message)
        cleanup()
        scheduleExit()
    }

    private fun stopEngine() {
        if (stopping) return
        stopping = true
        broadcast(this, EnginePhase.STOPPING, "Остановка обхода")
        LogBus.i("Обход", "Остановка туннеля")
        cleanup()
        broadcast(this, EnginePhase.STOPPED, "Обход DPI выключен")
        scheduleExit()
    }

    private fun cleanup() {
        runCatching { Native.stopTunnel() }
        runCatching { Native.stopByedpi() }
        runCatching { tun?.close() }
        tun = null
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
    }

    /** Процесс :native после остановки byedpi переиспользовать нельзя — выходим целиком. */
    private fun scheduleExit() {
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching { stopSelf() }
            Process.killProcess(Process.myPid())
        }, 350)
    }

    override fun onRevoke() {
        LogBus.w("Обход", "Система отозвала VPN-разрешение")
        stopEngine()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopping = true
        runCatching { Native.stopTunnel() }
        runCatching { tun?.close() }
        tun = null
        super.onDestroy()
    }

    private fun startForegroundSafely(text: String) {
        val notification = Notifications.serviceNotification(
            this,
            Notifications.CH_TUNNEL,
            "Обход DPI",
            text,
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            LogBus.e("Обход", "Не удалось показать уведомление сервиса: ${t.message}")
            runCatching { startForeground(NOTIFICATION_ID, notification) }
        }
    }

    private fun updateNotification(title: String, text: String) {
        runCatching {
            val notification = Notifications.serviceNotification(this, Notifications.CH_TUNNEL, title, text)
            androidx.core.app.NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }
    }
}
