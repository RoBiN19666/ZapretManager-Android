package io.github.robin19666.zapretmanager.tgproxy

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.robin19666.zapretmanager.R
import io.github.robin19666.zapretmanager.core.CfDomains
import io.github.robin19666.zapretmanager.core.EnginePhase
import io.github.robin19666.zapretmanager.core.LogBus
import io.github.robin19666.zapretmanager.core.Notifications
import io.github.robin19666.zapretmanager.core.Settings
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * Foreground-сервис локального MTProto-прокси для Telegram.
 *
 * Go-рантайм (libtgwsproxy.so) держит listener на 127.0.0.1:<port>; системный VPN
 * не создаётся, Telegram подключается по ссылке tg://proxy.
 *
 * Маршрут подбирается по попыткам: сначала CF WS/80, затем CF WSS/443, затем
 * конфигурация «по умолчанию» (как в upstream-приложении). Так прокси не
 * остаётся в состоянии «слушает локально, но исходящий маршрут не готов».
 */
class TgProxyService : Service() {

    companion object {
        const val ACTION_START = "io.github.robin19666.zapretmanager.tgproxy.START"
        const val ACTION_STOP = "io.github.robin19666.zapretmanager.tgproxy.STOP"
        const val ACTION_STATE = "io.github.robin19666.zapretmanager.tgproxy.STATE"
        const val ACTION_QUERY = "io.github.robin19666.zapretmanager.tgproxy.QUERY"
        const val EXTRA_PHASE = "phase"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_STATUS = "status"
        const val NOTIFICATION_ID = 4202
        private const val ROUTE_READY_TIMEOUT_MS = 4_500L
        private const val MARKER = "tg_session.marker"

        fun start(context: Context) {
            val intent = Intent(context, TgProxyService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TgProxyService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }

        /** Просит работающий сервис повторно опубликовать состояние. */
        fun requestState(context: Context) {
            runCatching {
                context.sendBroadcast(Intent(ACTION_QUERY).setPackage(context.packageName))
            }
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

    private data class Attempt(val name: String, val tokens: String)

    @Volatile
    private var running = false
    @Volatile
    private var stopping = false
    @Volatile
    private var lastPhase = EnginePhase.STOPPED
    @Volatile
    private var lastMessage = "Telegram-прокси выключен"
    @Volatile
    private var lastStatus = ""
    private var pollThread: Thread? = null
    private val handler = Handler(Looper.getMainLooper())
    private var queryReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_QUERY) {
                    broadcast(this@TgProxyService, lastPhase, lastMessage, lastStatus)
                }
            }
        }
        queryReceiver = receiver
        runCatching {
            ContextCompat.registerReceiver(
                this, receiver, IntentFilter(ACTION_QUERY), ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun sendState(phase: EnginePhase, message: String, status: String = "") {
        lastPhase = phase
        lastMessage = message
        lastStatus = status
        broadcast(this, phase, message, status)
    }

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
        sendState(EnginePhase.STARTING, "Проверка порта и пула доменов")
        // Маркер сессии: если процесс :tg умрёт аварийно, следующий запуск UI это заметит.
        runCatching {
            java.io.File(filesDir, MARKER).writeText(System.currentTimeMillis().toString())
        }

        val settings = Settings.current()
        val port = settings.tgPort
        val domain = settings.tgMaskingDomain
        val route = settings.tgRoute
        val secret = TgConfig.ensureSecret()

        Thread({
            val lib = TgNative.get()
            if (lib == null) {
                fail("Go-рантайм недоступен: ${TgNative.unavailableReason() ?: "неизвестная ошибка"}")
                return@Thread
            }
            if (!isPortFree(port)) {
                fail("Порт $port занят другим приложением — выберите другой в настройках")
                return@Thread
            }

            val appContext = applicationContext
            val domainCount = runCatching { runBlocking { CfDomains.ensureFresh(appContext) } }
                .getOrDefault(0)
            LogBus.i(
                "Telegram",
                "Старт MTProto-прокси на ${TgConfig.HOST}:$port, маршрут $route, CF-доменов: $domainCount"
            )

            val attempts = buildAttempts(route, domain)
            var fallbackAttempt: Attempt? = null
            var lastStatusText = ""

            attempts.forEachIndexed { index, attempt ->
                if (stopping) return@Thread
                sendState(
                    EnginePhase.STARTING,
                    "Попытка ${index + 1} из ${attempts.size}: ${attempt.name}",
                )
                val code = runCatching {
                    lib.StartMtProtoProxy(TgConfig.HOST, port, secret, attempt.tokens, 1)
                }.getOrElse { error ->
                    fail("Вызов рантайма упал: ${error.javaClass.simpleName}: ${error.message}")
                    return@Thread
                }
                if (code != 0) {
                    LogBus.w("Telegram", "Вариант «${attempt.name}» не запустился: код $code")
                    if (code == -3) {
                        fail("Порт $port занят другим приложением")
                        return@Thread
                    }
                    if (code == -2) {
                        fail("Рантайм отклонил секрет — сбросьте секрет в настройках Telegram")
                        return@Thread
                    }
                    return@forEachIndexed
                }

                if (fallbackAttempt == null) fallbackAttempt = attempt
                val statusText = awaitRouteReady()
                lastStatusText = statusText
                val parsed = TgConfig.parseStatus(statusText)
                val status = parsed["status"].orEmpty()
                LogBus.i(
                    "Telegram",
                    "Вариант «${attempt.name}»: status=$status outbound=${parsed["outbound"].orEmpty()} " +
                        "reason=${parsed["route_reason"].orEmpty()} error=${parsed["last_error"].orEmpty()}",
                )

                if (status == "LISTENING_ROUTE_READY") {
                    markRunning(attempt, statusText, port, route, secret, domain, warning = null)
                    return@Thread
                }
                runCatching { lib.StopMtProtoProxy() }
                Thread.sleep(250)
            }

            // Ни один вариант не подтвердил маршрут: поднимаем первый рабочий listener,
            // чтобы Telegram мог попробовать подключиться, и честно предупреждаем.
            val attempt = fallbackAttempt
            if (attempt == null) {
                fail("Прокси не запустился: ${lastStatusText.ifBlank { "нет статуса от рантайма" }}")
                return@Thread
            }
            val code = runCatching {
                lib.StartMtProtoProxy(TgConfig.HOST, port, secret, attempt.tokens, 1)
            }.getOrElse { -1 }
            if (code != 0) {
                fail("Прокси не запустился (код $code): ${safeStatus().orEmpty()}")
                return@Thread
            }
            Thread.sleep(600)
            val statusText = safeStatus().orEmpty()
            markRunning(
                attempt = attempt,
                statusText = statusText,
                port = port,
                route = route,
                secret = secret,
                domain = domain,
                warning = "Маршрут не подтверждён — Telegram может не подключиться",
            )
        }, "zm-tg-start").start()
    }

    private fun buildAttempts(route: String, domain: String): List<Attempt> {
        val list = ArrayList<Attempt>()
        if (route == "cf_proxy_ws" || route == "cf") {
            list += Attempt("Cloudflare WS/80", TgConfig.runtimeTokens(route, domain, cfNoTls = true))
            list += Attempt("Cloudflare WSS/443", TgConfig.runtimeTokens(route, domain, cfNoTls = false))
        } else {
            list += Attempt(TgConfig.routeLabel(route), TgConfig.runtimeTokens(route, domain))
        }
        list += Attempt("Автоматически (как в upstream)", TgConfig.minimalTokens(domain))
        return list
    }

    /** Безопасное чтение статуса: падение нативного вызова не должно убивать процесс. */
    private fun safeStatus(): String? = runCatching { TgNative.statusText() }.getOrNull()

    private fun awaitRouteReady(): String {
        val deadline = System.currentTimeMillis() + ROUTE_READY_TIMEOUT_MS
        var status = ""
        while (System.currentTimeMillis() < deadline && !stopping) {
            status = safeStatus().orEmpty()
            val parsed = TgConfig.parseStatus(status)
            when {
                parsed["status"] == "LISTENING_ROUTE_READY" -> return status
                parsed["status"].orEmpty().startsWith("FAILED") -> return status
            }
            try {
                Thread.sleep(300)
            } catch (_: InterruptedException) {
                return status
            }
        }
        return status
    }

    private fun markRunning(
        attempt: Attempt,
        statusText: String,
        port: Int,
        route: String,
        secret: String,
        domain: String,
        warning: String?,
    ) {
        running = true
        val parsed = TgConfig.parseStatus(statusText)
        val outbound = parsed["outbound"].orEmpty()
        val message = warning ?: buildString {
            append("Прокси работает на порту $port")
            if (outbound.isNotBlank()) append(" · $outbound")
            append(" · ${attempt.name}")
        }
        sendState(EnginePhase.RUNNING, message, statusText)
        updateNotification(
            if (warning == null) "Telegram-прокси активен" else "Telegram-прокси: маршрут не подтверждён",
            "$message · ${TgConfig.routeLabel(route)}",
        )
        startPolling()
        if (warning != null) LogBus.w("Telegram", warning)
        if (Settings.current().tgAutoApply) {
            runCatching {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    android.net.Uri.parse(TgConfig.tgLink(port, secret, domain)),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }
    }

    private fun startPolling() {
        if (pollThread?.isAlive == true) return
        pollThread = Thread({
            while (running && !stopping) {
                val status = safeStatus()
                if (status != null) {
                    val parsed = TgConfig.parseStatus(status)
                    val outbound = parsed["outbound"].orEmpty()
                    val text = buildString {
                        append(parsed["status"].orEmpty().ifBlank { "неизвестно" })
                        if (outbound.isNotBlank()) append(" · $outbound")
                        val error = parsed["last_error"].orEmpty()
                        if (error.isNotBlank() && error != "<nil>") append(" · $error")
                    }
                    sendState(EnginePhase.RUNNING, text, status)
                    updateNotification(
                        if (parsed["status"] == "LISTENING_ROUTE_READY") {
                            "Telegram-прокси активен"
                        } else {
                            "Telegram-прокси: маршрут не подтверждён"
                        },
                        text,
                    )
                }
                try {
                    Thread.sleep(2000)
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
        }, "zm-tg-status").apply { isDaemon = true; start() }
    }

    private fun isPortFree(port: Int): Boolean = runCatching {
        ServerSocket().use { socket ->
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(TgConfig.HOST, port))
        }
        true
    }.getOrDefault(false)

    private fun stopProxy() {
        if (stopping) return
        stopping = true
        sendState(EnginePhase.STOPPING, "Остановка прокси")
        Thread({
            val lib = TgNative.get()
            val result = runCatching { lib?.StopMtProtoProxy() ?: -1 }.getOrDefault(-1)
            running = false
            LogBus.i("Telegram", "Прокси остановлен (код $result)")
            sendState(EnginePhase.STOPPED, "Telegram-прокси выключен")
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
        sendState(EnginePhase.ERROR, message)
        updateNotification("Telegram-прокси не запущен", message)
        handler.post {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        running = false
        stopping = true
        runCatching { queryReceiver?.let { unregisterReceiver(it) } }
        queryReceiver = null
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
