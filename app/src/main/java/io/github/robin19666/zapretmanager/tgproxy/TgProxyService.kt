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
import io.github.robin19666.zapretmanager.core.EngineMemory
import io.github.robin19666.zapretmanager.core.EnginePhase
import io.github.robin19666.zapretmanager.core.LogBus
import io.github.robin19666.zapretmanager.core.Notifications
import io.github.robin19666.zapretmanager.core.Ports
import io.github.robin19666.zapretmanager.core.Settings
import io.github.robin19666.zapretmanager.core.TgTrace
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean

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
        /** Порт, на котором реально слушает рантайм: UI и диагностика должны видеть его, а не настройки. */
        const val EXTRA_PORT = "port"
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

        fun broadcast(
            context: Context,
            phase: EnginePhase,
            message: String,
            status: String = "",
            port: Int = 0,
        ) {
            val intent = Intent(ACTION_STATE)
                .setPackage(context.packageName)
                .putExtra(EXTRA_PHASE, phase.name)
                .putExtra(EXTRA_MESSAGE, message)
                .putExtra(EXTRA_STATUS, status)
                .putExtra(EXTRA_PORT, port)
            context.sendBroadcast(intent)
        }
    }

    private data class Attempt(val name: String, val tokens: String)

    @Volatile
    private var running = false
    @Volatile
    private var stopping = false
    @Volatile
    private var intentionalStop = false
    private val starting = AtomicBoolean(false)
    @Volatile
    private var lastPhase = EnginePhase.STOPPED
    @Volatile
    private var lastMessage = "Telegram-прокси выключен"
    @Volatile
    private var lastStatus = ""
    @Volatile
    private var lastPort = 0
    /** Секрет и токены запущенного рантайма — нужны для самовосстановления. */
    @Volatile
    private var runningSecret = ""
    @Volatile
    private var runningTokens = ""
    /** Отпечаток «правильного» секрета: считается один раз при старте. */
    @Volatile
    private var expectedFingerprint = ""
    private var pollThread: Thread? = null
    private val handler = Handler(Looper.getMainLooper())
    private var queryReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_QUERY) {
                    broadcast(this@TgProxyService, lastPhase, lastMessage, lastStatus, lastPort)
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
        broadcast(this, phase, message, status, lastPort)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // Явный запуск пользователем — счётчик автоперезапусков обнуляем.
                EngineMemory.resetRestarts(this, EngineMemory.KIND_TG)
                startProxy()
            }
            ACTION_STOP -> stopProxy()
            else -> {
                if (!running && !starting.get()) {
                    // Система перезапустила сервис после убийства процесса (START_STICKY).
                    if (restoreAfterKill()) startProxy() else stopSelf()
                }
            }
        }
        return if (stopping) START_NOT_STICKY else START_STICKY
    }

    /**
     * Если прокси должен работать, а процесс убили (HyperOS, Doze), поднимаем его
     * заново. Число попыток ограничено, чтобы не зациклиться на сломанной сети.
     */
    private fun restoreAfterKill(): Boolean {
        if (!EngineMemory.tgWanted(this)) {
            LogBus.d("Telegram", "Сервис перезапущен системой, но прокси не нужен — останавливаюсь")
            return false
        }
        if (!EngineMemory.allowRestart(this, EngineMemory.KIND_TG)) {
            EngineMemory.setTgWanted(this, false)
            LogBus.w("Telegram", "Слишком много автоперезапусков — прокси оставлен выключенным")
            return false
        }
        LogBus.i("Telegram", "Система остановила прокси — восстанавливаю")
        return true
    }

    private fun startProxy() {
        if (running || !starting.compareAndSet(false, true)) return
        stopping = false
        intentionalStop = false
        startForegroundSafely("Запуск Telegram-прокси…")
        sendState(EnginePhase.STARTING, "Проверка порта")
        // Маркер сессии: если процесс :tg умрёт аварийно, следующий запуск UI это заметит.
        runCatching { markerFile().writeText(System.currentTimeMillis().toString()) }
        TgTrace.clear(this)
        TgTrace.write(this, "service: start")

        // Процесс :tg может быть переиспользован системой после stopSelf();
        // перечитываем настройки, иначе новый запуск возьмёт старые порт/маршрут.
        Settings.reload(this)
        val settings = Settings.current()
        val port = settings.tgPort
        val domain = settings.tgMaskingDomain
        val route = settings.tgRoute
        val secret = TgSecret.ensure(this)
        expectedFingerprint = TgSecret.fingerprint(secret)

        Thread({
            try {
            if (!TgNative.ensureLoaded()) {
                TgTrace.write(this, "native: FAILED ${TgNative.unavailableReason()}")
                fail("Go-рантайм недоступен: ${TgNative.unavailableReason() ?: "неизвестная ошибка"}")
                return@Thread
            }
            TgTrace.write(this, "native: loaded")
            if (!isPortFree(port)) {
                TgTrace.write(this, "port: $port busy")
                fail("Порт $port занят другим приложением — выберите другой в настройках")
                return@Thread
            }
            TgTrace.write(this, "port: $port free")

            // Сеть не должна задерживать старт: берём сохранённый пул, обновляем в фоне.
            val cachedDomains = CfDomains.cached(this)
            TgTrace.write(this, "secret fp: ${TgSecret.fingerprint(secret)}")
            TgTrace.write(this, "domains cached: ${cachedDomains.size}")
            LogBus.i(
                "Telegram",
                "Старт MTProto-прокси на ${TgConfig.HOST}:$port, маршрут $route, CF-доменов: ${cachedDomains.size}"
            )

            val attempts = buildAttempts(route, domain, cachedDomains)
            var fallbackAttempt: Attempt? = null
            var lastStatusText = ""

            attempts.forEachIndexed { index, attempt ->
                if (stopping) return@Thread
                sendState(
                    EnginePhase.STARTING,
                    "Попытка ${index + 1} из ${attempts.size}: ${attempt.name}",
                )
                TgTrace.write(this, "attempt ${index + 1}/${attempts.size} (${attempt.name}): start")
                runningSecret = secret
                runningTokens = attempt.tokens
                val code = runCatching {
                    TgNative.start(TgConfig.HOST, port, secret, attempt.tokens, 1)
                }.getOrElse { error ->
                    TgTrace.write(this, "attempt: exception ${error.javaClass.simpleName}")
                    fail("Вызов рантайма упал: ${error.javaClass.simpleName}: ${error.message}")
                    return@Thread
                }
                TgTrace.write(this, "attempt code: $code")
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

                TgTrace.write(this, "status: ${status.ifBlank { "empty" }}")
                if (status == "LISTENING_ROUTE_READY") {
                    TgTrace.write(this, "running: ${attempt.name}")
                    markRunning(attempt, statusText, port, route, secret, domain, warning = null)
                    return@Thread
                }
                runCatching { TgNative.stop() }
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
                TgNative.start(TgConfig.HOST, port, secret, attempt.tokens, 1)
            }.getOrElse { -1 }
            runningSecret = secret
            runningTokens = attempt.tokens
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
            } catch (t: Throwable) {
                fail("Ошибка запуска прокси: ${t.message ?: t.javaClass.simpleName}")
            } finally {
                starting.set(false)
            }
        }, "zm-tg-start").start()
    }

    private fun buildAttempts(
        route: String,
        domain: String,
        cachedDomains: List<String>,
    ): List<Attempt> {
        val list = ArrayList<Attempt>()
        if (route == "cf_proxy_ws" || route == "cf") {
            list += Attempt(
                "Cloudflare WS/80",
                TgConfig.runtimeTokens(route, domain, cfNoTls = true, cachedDomains = cachedDomains),
            )
            list += Attempt(
                "Cloudflare WSS/443",
                TgConfig.runtimeTokens(route, domain, cfNoTls = false, cachedDomains = cachedDomains),
            )
        } else {
            list += Attempt(
                TgConfig.routeLabel(route),
                TgConfig.runtimeTokens(route, domain, cachedDomains = cachedDomains),
            )
        }
        list += Attempt(
            "Автоматически (как в upstream)",
            TgConfig.minimalTokens(domain, cachedDomains),
        )
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
        // Порт должен реально слушать: иначе UI покажет «работает», а Telegram
        // не сможет подключиться — ровно так выглядел баг «прокси не запускается».
        if (!awaitListening(port)) {
            fail(
                "Прокси не открыл 127.0.0.1:$port. Проверьте, что порт свободен, " +
                    "и попробуйте снова или выберите другой порт в настройках."
            )
            return
        }
        running = true
        lastPort = port
        // Намерение «прокси должен работать»: система перезапустит сервис после kill.
        EngineMemory.setTgWanted(this, true)
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
        startPolling(port)
        refreshDomainsInBackground()
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

    /**
     * Лечит рассинхрон секрета: рантайм слушает со старым секретом, а приложение
     * (и ссылка в Telegram) уже с новым — тогда все рукопожатия отклоняются,
     * `actual_backend=none`, `bytes_up=0`, хотя статус «работает».
     *
     * Перезапускаем рантайм с актуальным секретом и теми же токенами маршрута,
     * не пересоздавая сервис и не меняя порт.
     */
    private fun healSecretMismatch(port: Int, runtimeFingerprint: String) {
        // Сначала пробуем сохранить рабочий секрет: Telegram у пользователя,
        // скорее всего, привязан именно к тому, с которым запущен рантайм.
        // Тогда достаточно вернуть его в настройки — ссылка снова совпадёт.
        if (TgSecret.isValid(runningSecret) && TgSecret.fingerprint(runningSecret) == runtimeFingerprint) {
            TgSecret.adopt(this, runningSecret)
            expectedFingerprint = runtimeFingerprint
            LogBus.w(
                "Telegram",
                "Секрет в настройках отличался от запущенного прокси — вернул рабочий ($runtimeFingerprint), " +
                    "ссылка в Telegram остаётся действительной",
            )
            TgTrace.write(this, "secret restored from runtime=$runtimeFingerprint")
            sendState(EnginePhase.RUNNING, "Секрет синхронизирован с запущенным прокси", safeStatus().orEmpty())
            return
        }

        val actual = TgSecret.ensure(this)
        val appFingerprint = TgSecret.fingerprint(actual)
        if (runtimeFingerprint == appFingerprint) return
        LogBus.w(
            "Telegram",
            "Секрет прокси не совпадал (рантайм $runtimeFingerprint, приложение $appFingerprint) — " +
                "перезапускаю прокси с актуальным секретом",
        )
        TgTrace.write(this, "secret mismatch runtime=$runtimeFingerprint app=$appFingerprint: restart")
        runCatching { TgNative.stop() }
        Thread.sleep(250)
        val code = runCatching {
            TgNative.start(TgConfig.HOST, port, actual, runningTokens, 1)
        }.getOrElse { -1 }
        TgTrace.write(this, "secret heal start: $code")
        if (code == 0) {
            runningSecret = actual
            expectedFingerprint = appFingerprint
            val status = safeStatus().orEmpty()
            sendState(EnginePhase.RUNNING, "Секрет обновлён — прокси перезапущен", status)
        } else {
            fail("Не удалось перезапустить прокси с актуальным секретом (код $code)")
        }
    }

    private fun startPolling(port: Int) {
        if (pollThread?.isAlive == true) return
        pollThread = Thread({
            var ticks = 0
            var portMisses = 0
            var statusMisses = 0
            var lastText = ""
            var delayMs = 2_000L
            while (running && !stopping) {
                val status = safeStatus()
                if (status == null) {
                    // Рантайм не отвечает: библиотека выгружена или процесс Go
                    // умер посреди работы. Три подряд — честно сообщаем об ошибке.
                    if (++statusMisses >= 3) {
                        fail("Рантайм Telegram-прокси не отвечает — прокси остановлен")
                        return@Thread
                    }
                } else {
                    statusMisses = 0
                    val parsed = TgConfig.parseStatus(status)
                    // Рантайм мог подняться со старым секретом (гонка двух процессов
                    // при авто-восстановлении): тогда Telegram шлёт рукопожатия с новым
                    // секретом и получает отказ — прокси «работает», но молчит.
                    // Сверяем только строки: отпечаток приложения считается один раз
                    // при старте, поэтому в цикле нет ни чтения файла, ни хеширования.
                    val runtimeFingerprint = parsed["secret_fingerprint"].orEmpty()
                    if (runtimeFingerprint.isNotEmpty() && expectedFingerprint.isNotEmpty() &&
                        runtimeFingerprint != expectedFingerprint
                    ) {
                        healSecretMismatch(port, runtimeFingerprint)
                        continue
                    }
                    val outbound = parsed["outbound"].orEmpty()
                    val text = buildString {
                        append(parsed["status"].orEmpty().ifBlank { "неизвестно" })
                        if (outbound.isNotBlank()) append(" · $outbound")
                        val error = parsed["last_error"].orEmpty()
                        if (error.isNotBlank() && error != "<nil>") append(" · $error")
                    }
                    // Работающий туннель опрашиваем реже, а состояние и уведомление
                    // обновляем только при изменении: иначе каждые 2 секунды шёл бы
                    // лишний broadcast и перерисовка уведомления.
                    delayMs = if (parsed["status"] == "LISTENING_ROUTE_READY") 5_000L else 2_000L
                    if (text != lastText) {
                        lastText = text
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
                }

                // Раз в ~30 секунд убеждаемся, что порт действительно слушает:
                // «статус говорит RUNNING, а порт закрыт» — самый частый симптом
                // молча умершего рантайма. Проверка идёт попыткой занять порт,
                // без подключения к рантайму, поэтому не создаёт ложных
                // «отклонённых рукопожатий» в его статистике.
                ticks++
                if (ticks % 6 == 0) {
                    if (Ports.isListening(port)) {
                        portMisses = 0
                    } else if (++portMisses >= 2) {
                        fail("Прокси перестал слушать 127.0.0.1:$port — запустите его заново")
                        return@Thread
                    }
                }

                try {
                    Thread.sleep(delayMs)
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
        }, "zm-tg-status").apply { isDaemon = true; start() }
    }

    /** Порт должен реально слушать: без этого Telegram не подключится. */
    private fun awaitListening(port: Int, timeoutMs: Long = 2_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (Ports.isListening(port)) return true
            if (stopping) return false
            try {
                Thread.sleep(150)
            } catch (_: InterruptedException) {
                return false
            }
        }
        return Ports.isListening(port)
    }

    /**
     * Обновляет пул CF-доменов уже после старта. Go-рантайм не трогаем: список
     * передаётся токеном `@cf_cached_domains` при следующем запуске (runtimeTokens),
     * а вызов SetCachedCFDomains на живом рантайме — лишний риск уронить прокси.
     */
    private fun refreshDomainsInBackground() {
        Thread({
            val fresh = runCatching {
                runBlocking { CfDomains.ensureFresh(applicationContext) }
            }.getOrDefault(emptyList())
            if (fresh.size >= 3) {
                TgTrace.write(this, "domains refresh: ${fresh.size}, применятся при следующем запуске")
            } else {
                TgTrace.write(this, "domains refresh: skipped")
            }
        }, "zm-tg-domains").start()
    }

    private fun isPortFree(port: Int): Boolean = runCatching {
        ServerSocket().use { socket ->
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(TgConfig.HOST, port))
        }
        true
    }.getOrDefault(false)

    private fun markerFile(): java.io.File = java.io.File(filesDir, MARKER)

    private fun clearMarker() {
        runCatching { markerFile().delete() }
    }

    private fun stopProxy() {
        if (stopping) return
        stopping = true
        intentionalStop = true
        starting.set(false)
        EngineMemory.setTgWanted(this, false)
        sendState(EnginePhase.STOPPING, "Остановка прокси")
        Thread({
            val result = runCatching { TgNative.stop() }.getOrDefault(-1)
            running = false
            clearMarker()
            LogBus.i("Telegram", "Прокси остановлен (код $result)")
            sendState(EnginePhase.STOPPED, "Telegram-прокси выключен")
            handler.post {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }, "zm-tg-stop").start()
    }

    private fun fail(message: String) {
        TgTrace.write(this, "FAIL: $message")
        LogBus.e("Telegram", message)
        stopping = true
        running = false
        intentionalStop = true
        starting.set(false)
        EngineMemory.setTgWanted(this, false)
        clearMarker()
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
        starting.set(false)
        if (intentionalStop) {
            clearMarker()
            EngineMemory.setTgWanted(this, false)
        }
        runCatching { queryReceiver?.let { unregisterReceiver(it) } }
        queryReceiver = null
        runCatching { TgNative.stop() }
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

    @android.annotation.SuppressLint("MissingPermission")
    private fun updateNotification(title: String, text: String) {
        if (!Notifications.canPost(this)) return
        runCatching {
            androidx.core.app.NotificationManagerCompat.from(this).notify(
                NOTIFICATION_ID,
                Notifications.serviceNotification(this, Notifications.CH_TG, title, text),
            )
        }
    }
}
