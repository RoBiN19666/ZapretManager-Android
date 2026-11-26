package io.github.robin19666.zapretmanager.tgproxy

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import io.github.robin19666.zapretmanager.R
import io.github.robin19666.zapretmanager.core.EnginePhase
import io.github.robin19666.zapretmanager.core.LogBus
import io.github.robin19666.zapretmanager.core.Notifications
import io.github.robin19666.zapretmanager.core.Settings
import java.io.File

/**
 * Foreground-сервис локального MTProto-прокси для Telegram.
 * Go-рантайм (libtgwsproxy.so) держит listener на 127.0.0.1:<port>;
 * системный VPN не создаётся, Telegram подключается по ссылке tg://proxy.
 */
class TgProxyService : Service() {

    companion object {
        const val ACTION_START = "io.github.robin19666.zapretmanager.tgproxy.START"
        const val ACTION_STOP = "io.github.robin19666.zapretmanager.tgproxy.STOP"
        const val ACTION_STATE = "io.github.robin19666.zapretmanager.tgproxy.STATE"
        const val EXTRA_PHASE = "phase"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_STATUS = "status"
        const val NOTIFICATION_ID = 4202

        fun start(context: Context) {
            val intent = Intent(context, TgProxyService::class.java).setAction(ACTION_START)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TgProxyService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }

        fun broadcast(context: Context, phase: EnginePhase, message: String, status: String = "") {
            val intent = Intent(ACTION_STATE)
                .setPackage(context.packageName)
                .putExtra(EXTRA_PHASE, phase.name)
                .putExtra(EXTRA_MESSAGE, message)
                .putExtra(EXTRA_STATUS, status)
            context.sendBroadcast(intent)
        }
    }

    @Volatile
    private var running = false
    @Volatile
    private var stopping = false
    private var pollThread: Thread? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startProxy()
            ACTION_STOP -> stopProxy()
            else -> if (!running) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startProxy() {
        if (running) return
        stopping = false
        startForegroundSafely("Запуск Telegram-прокси…")
        broadcast(this, EnginePhase.STARTING, "Запуск локального прокси")

        val settings = Settings.current()
        val port = settings.tgPort
        val domain = settings.tgMaskingDomain
        val route = settings.tgRoute
        val secret = TgConfig.ensureSecret()
        val tokens = TgConfig.runtimeTokens(route, domain)

        Thread({
            val lib = TgNative.get()
            if (lib == null) {
                fail("Go-рантайм недоступен: ${TgNative.unavailableReason() ?: "неизвестная ошибка"}")
                return@Thread
            }
            applyDomainPool()
            LogBus.i("Telegram", "Старт MTProto-прокси на ${TgConfig.HOST}:$port, маршрут $route")
            val result = try {
                lib.StartMtProtoProxy(TgConfig.HOST, port, secret, tokens, 1)
            } catch (t: Throwable) {
                fail("Вызов рантайма упал: ${t.message}")
                return@Thread
            }
            when (result) {
                0 -> {
                    running = true
                    broadcast(this, EnginePhase.RUNNING, "Прокси работает на порту $port", TgNative.statusText().orEmpty())
                    updateNotification("Telegram-прокси активен", "127.0.0.1:$port · ${TgConfig.routeLabel(route)}")
                    startPolling()
                    if (settings.tgAutoApply) {
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(
                                TgConfig.tgLink(port, secret, domain)
                            )).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                        }
                    }
                }
                -1 -> fail("Прокси уже запущен")
                -2 -> fail("Неверный секрет (нужно 32 hex-символа)")
                -3 -> fail("Порт $port занят другим приложением")
                else -> fail("Рантайм вернул ошибку $result: ${TgNative.statusText().orEmpty()}")
            }
        }, "zm-tg-start").start()
    }

    private fun applyDomainPool() {
        val file = File(filesDir, "components/cf-domains.txt")
        if (!file.exists()) return
        val domains = runCatching {
            file.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        }.getOrDefault(emptyList())
        if (domains.size >= 3) TgNative.applyCachedDomains(domains)
    }

    private fun startPolling() {
        if (pollThread?.isAlive == true) return
        pollThread = Thread({
            while (running && !stopping) {
                val status = TgNative.statusText()
                if (status != null) {
                    val parsed = TgConfig.parseStatus(status)
                    val outbound = parsed["outbound"].orEmpty()
                    val text = buildString {
                        append(parsed["status"].orEmpty().ifBlank { "неизвестно" })
                        if (outbound.isNotBlank()) append(" · $outbound")
                        val error = parsed["last_error"].orEmpty()
                        if (error.isNotBlank() && error != "<nil>") append(" · $error")
                    }
                    broadcast(this, EnginePhase.RUNNING, text, status)
                    updateNotification("Telegram-прокси активен", text)
                }
                try {
                    Thread.sleep(2000)
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
        }, "zm-tg-status").apply { isDaemon = true; start() }
    }

    private fun stopProxy() {
        if (stopping) return
        stopping = true
        broadcast(this, EnginePhase.STOPPING, "Остановка прокси")
        Thread({
            val lib = TgNative.get()
            val result = runCatching { lib?.StopMtProtoProxy() ?: -1 }.getOrDefault(-1)
            running = false
            LogBus.i("Telegram", "Прокси остановлен (код $result)")
            broadcast(this, EnginePhase.STOPPED, "Telegram-прокси выключен")
            handler.post {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }, "zm-tg-stop").start()
    }

    private fun fail(message: String) {
        LogBus.e("Telegram", message)
        stopping = true
        running = false
        broadcast(this, EnginePhase.ERROR, message)
        updateNotification("Telegram-прокси не запущен", message)
        handler.post {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        running = false
        stopping = true
        runCatching { TgNative.get()?.StopMtProtoProxy() }
        super.onDestroy()
    }

    private fun startForegroundSafely(text: String) {
        val notification = Notifications.serviceNotification(
            this, Notifications.CH_TG, "Telegram-прокси", text,
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            LogBus.e("Telegram", "Уведомление сервиса: ${t.message}")
            runCatching { startForeground(NOTIFICATION_ID, notification) }
        }
    }

    private fun updateNotification(title: String, text: String) {
        runCatching {
            androidx.core.app.NotificationManagerCompat.from(this).notify(
                NOTIFICATION_ID,
                Notifications.serviceNotification(this, Notifications.CH_TG, title, text),
            )
        }
    }
}
