package io.github.robin19666.zapretmanager.core

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import io.github.robin19666.zapretmanager.BuildConfig
import io.github.robin19666.zapretmanager.byedpi.ByeDpiVpnService
import io.github.robin19666.zapretmanager.byedpi.DpiEngine
import io.github.robin19666.zapretmanager.byedpi.Strategies
import io.github.robin19666.zapretmanager.tgproxy.TgProxyController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

enum class CheckStatus { OK, WARN, FAIL, INFO }

/** Действие, которое умеет выполнить интерфейс (открыть настройку, запустить движок). */
enum class DiagnosticFix {
    VPN_PERMISSION,
    BATTERY,
    NOTIFICATIONS,
    INSTALL_PACKAGES,
    PRIVATE_DNS,
    VPN_SETTINGS,
    START_DPI,
    STOP_DPI,
    START_TG,
    STOP_TG,
    UPDATE_COMPONENTS,
    NONE,
}

data class DiagnosticCheck(
    val id: String,
    val group: String,
    val title: String,
    val detail: String,
    val status: CheckStatus,
    val fix: DiagnosticFix = DiagnosticFix.NONE,
    val fixLabel: String = "",
)

/**
 * Диагностика устройства и приложения: разрешения, энергосбережение, движки и порты,
 * целостность нативных библиотек, доступность обновлений и сетевой путь
 * DNS → TCP → TLS → HTTP. Каждая проблема по возможности имеет кнопку исправления.
 */
object Diagnostics {
    /** Порог свободного места, ниже которого предупреждаем: столько нужно на скачивание и распаковку APK. */
    private const val CACHE_WARN_MB = 200

    fun report(context: Context, checks: List<DiagnosticCheck>): String = buildString {
        appendLine("Zapret Manager Android — диагностика")
        appendLine("app: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("abis: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine()
        checks.forEach { check ->
            appendLine("[${check.status}] ${check.group} · ${check.title}: ${check.detail}")
        }
    }

    /**
     * Полный прогон проверок. Весь набор выполняется на IO-диспетчере: часть
     * проверок делает блокирующие TCP-пробы и читает файлы, а вызывается это
     * из UI (LaunchedEffect на главном потоке) — без withContext здесь
     * приложение рисковало ANR.
     */
    suspend fun run(context: Context): List<DiagnosticCheck> = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val checks = ArrayList<DiagnosticCheck>()
        checks += permissions(app)
        checks += engines(app)
        checks += components(app)
        checks += network(app)
        checks
    }

    private fun permissions(context: Context): List<DiagnosticCheck> {
        val result = ArrayList<DiagnosticCheck>()
        val pm = context.packageManager

        val vpnGranted = runCatching { io.github.robin19666.zapretmanager.byedpi.DpiEngine.prepareIntent(context) == null }
            .getOrDefault(false)
        result += DiagnosticCheck(
            id = "vpn_permission",
            group = "Разрешения",
            title = "VPN-разрешение",
            detail = if (vpnGranted) "выдано" else "не выдано — ускорение соединения не запустится",
            status = if (vpnGranted) CheckStatus.OK else CheckStatus.FAIL,
            fix = if (vpnGranted) DiagnosticFix.NONE else DiagnosticFix.VPN_PERMISSION,
            fixLabel = if (vpnGranted) "" else "Выдать",
        )

        val ignoringBattery = Power.isIgnoring(context)
        result += DiagnosticCheck(
            id = "battery",
            group = "Разрешения",
            title = "Энергосбережение",
            detail = if (ignoringBattery) "без ограничений" else "система может останавливать сервисы в фоне",
            status = if (ignoringBattery) CheckStatus.OK else CheckStatus.WARN,
            fix = if (ignoringBattery) DiagnosticFix.NONE else DiagnosticFix.BATTERY,
            fixLabel = if (ignoringBattery) "" else "Разрешить",
        )

        val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.checkPermission(android.Manifest.permission.POST_NOTIFICATIONS, context.packageName) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        result += DiagnosticCheck(
            id = "notifications",
            group = "Разрешения",
            title = "Уведомления",
            detail = if (notifications) "разрешены" else "без них не видно состояние движков",
            status = if (notifications) CheckStatus.OK else CheckStatus.WARN,
            fix = if (notifications) DiagnosticFix.NONE else DiagnosticFix.NOTIFICATIONS,
            fixLabel = if (notifications) "" else "Открыть",
        )

        val canInstall = UpdateManager.canInstall(context)
        result += DiagnosticCheck(
            id = "install",
            group = "Разрешения",
            title = "Установка обновлений",
            detail = if (canInstall) "разрешена" else "запрещена — самообновление не сработает",
            status = if (canInstall) CheckStatus.OK else CheckStatus.WARN,
            fix = if (canInstall) DiagnosticFix.NONE else DiagnosticFix.INSTALL_PACKAGES,
            fixLabel = if (canInstall) "" else "Разрешить",
        )

        val alwaysOn = runCatching {
            AndroidSettings.Secure.getString(context.contentResolver, "always_on_vpn_app")
        }.getOrNull()
        result += DiagnosticCheck(
            id = "always_on_vpn",
            group = "Разрешения",
            title = "Всегда включённый VPN",
            detail = when {
                alwaysOn == context.packageName -> "включён для этого приложения"
                alwaysOn.isNullOrBlank() -> "не настроен (необязательно)"
                else -> "настроен для другого приложения: $alwaysOn"
            },
            status = if (alwaysOn == context.packageName) CheckStatus.OK else CheckStatus.INFO,
            fix = DiagnosticFix.VPN_SETTINGS,
            fixLabel = "Настройки",
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val privateDns = runCatching {
                AndroidSettings.Global.getString(context.contentResolver, "private_dns_mode")
            }.getOrNull().orEmpty()
            val host = runCatching {
                AndroidSettings.Global.getString(context.contentResolver, "private_dns_specifier")
            }.getOrNull().orEmpty()
            val enabled = privateDns == "hostname"
            result += DiagnosticCheck(
                id = "private_dns",
                group = "Разрешения",
                title = "Private DNS",
                detail = when {
                    enabled && host.isNotBlank() -> "включён ($host) — часть запросов идёт мимо туннеля"
                    enabled -> "включён — часть запросов идёт мимо туннеля"
                    else -> "выключен"
                },
                status = if (enabled) CheckStatus.WARN else CheckStatus.OK,
                fix = DiagnosticFix.PRIVATE_DNS,
                fixLabel = "Открыть",
            )
        }
        return result
    }

    private fun engines(context: Context): List<DiagnosticCheck> {
        val result = ArrayList<DiagnosticCheck>()
        val dpi = DpiEngine.state.value
        result += DiagnosticCheck(
            id = "dpi_state",
            group = "Движки",
            title = "Ускорение соединения",
            detail = when (dpi.phase) {
                EnginePhase.RUNNING -> "работает"
                EnginePhase.STARTING -> "запускается"
                EnginePhase.STOPPING -> "останавливается"
                EnginePhase.ERROR -> "ошибка: ${dpi.message}"
                EnginePhase.STOPPED -> "выключен"
            },
            status = when (dpi.phase) {
                EnginePhase.RUNNING -> CheckStatus.OK
                EnginePhase.ERROR -> CheckStatus.FAIL
                else -> CheckStatus.INFO
            },
            fix = if (dpi.phase == EnginePhase.RUNNING) DiagnosticFix.STOP_DPI else DiagnosticFix.START_DPI,
            fixLabel = if (dpi.phase == EnginePhase.RUNNING) "Стоп" else "Запустить",
        )

        val socksOpen = tcpOpen("127.0.0.1", ByeDpiVpnService.SOCKS_PORT)
        result += DiagnosticCheck(
            id = "dpi_port",
            group = "Движки",
            title = "SOCKS-порт byedpi (${ByeDpiVpnService.SOCKS_PORT})",
            detail = when {
                socksOpen -> "открыт"
                dpi.phase == EnginePhase.RUNNING -> "закрыт, хотя движок «работает» — перезапустите ускорение"
                else -> "закрыт (движок выключен)"
            },
            status = when {
                socksOpen -> CheckStatus.OK
                dpi.phase == EnginePhase.RUNNING -> CheckStatus.FAIL
                else -> CheckStatus.INFO
            },
            fix = if (dpi.phase == EnginePhase.RUNNING) DiagnosticFix.STOP_DPI else DiagnosticFix.START_DPI,
            fixLabel = if (dpi.phase == EnginePhase.RUNNING) "Перезапуск" else "Запустить",
        )

        val tg = TgProxyController.state.value
        result += DiagnosticCheck(
            id = "tg_state",
            group = "Движки",
            title = "Telegram-прокси",
            detail = when (tg.phase) {
                EnginePhase.RUNNING -> if (tg.routeReady) "работает, маршрут подтверждён" else
                    "слушает, но маршрут не подтверждён (${tg.runtimeStatus})"
                EnginePhase.STARTING -> "запускается"
                EnginePhase.STOPPING -> "останавливается"
                EnginePhase.ERROR -> "ошибка: ${tg.message}"
                EnginePhase.STOPPED -> "выключен"
            },
            status = when (tg.phase) {
                EnginePhase.RUNNING -> if (tg.routeReady) CheckStatus.OK else CheckStatus.WARN
                EnginePhase.ERROR -> CheckStatus.FAIL
                else -> CheckStatus.INFO
            },
            fix = if (tg.phase == EnginePhase.RUNNING) DiagnosticFix.STOP_TG else DiagnosticFix.START_TG,
            fixLabel = if (tg.phase == EnginePhase.RUNNING) "Стоп" else "Запустить",
        )

        // Пока прокси работает, доверяем его собственному порту: настройку могли
        // изменить уже после запуска, и проверка «не того» порта давала ложное
        // «закрыт, хотя прокси работает».
        val configuredPort = Settings.current().tgPort.coerceIn(1024, 65535)
        val tgPort = if (tg.phase == EnginePhase.RUNNING && tg.port > 0) tg.port else configuredPort
        val tgOpen = tcpOpen("127.0.0.1", tgPort)
        result += DiagnosticCheck(
            id = "tg_port",
            group = "Движки",
            title = "Порт Telegram-прокси ($tgPort)",
            detail = when {
                tg.phase == EnginePhase.RUNNING && tgOpen && tgPort != configuredPort ->
                    "открыт (в настройках указан $configuredPort)"
                tg.phase == EnginePhase.RUNNING && tgOpen -> "открыт"
                tg.phase == EnginePhase.RUNNING -> "закрыт, хотя прокси «работает» — перезапустите прокси"
                !tgOpen -> "свободен"
                else -> "занят другим приложением"
            },
            status = when {
                tg.phase == EnginePhase.RUNNING && tgOpen -> CheckStatus.OK
                tg.phase == EnginePhase.RUNNING -> CheckStatus.FAIL
                !tgOpen -> CheckStatus.OK
                else -> CheckStatus.WARN
            },
        )

        val libs = listOf("libtgwsproxy.so", "libzapretnative.so", "libhev-socks5-tunnel.so")
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val missing = libs.filter { !File(nativeDir, it).exists() }
        result += DiagnosticCheck(
            id = "native_libs",
            group = "Движки",
            title = "Нативные библиотеки",
            detail = if (missing.isEmpty()) "все на месте (${libs.size})" else "отсутствуют: ${missing.joinToString()}",
            status = if (missing.isEmpty()) CheckStatus.OK else CheckStatus.FAIL,
        )

        val strategies = Strategies.all(context)
        val selected = Strategies.resolve(context, Settings.current().strategyId)
        result += DiagnosticCheck(
            id = "strategies",
            group = "Движки",
            title = "Стратегии",
            detail = "${strategies.size} шт., выбрана «${selected.title}»",
            status = if (strategies.size >= 5) CheckStatus.OK else CheckStatus.WARN,
            fix = DiagnosticFix.UPDATE_COMPONENTS,
            fixLabel = if (strategies.size >= 5) "" else "Обновить",
        )

        val crash = CrashLog.read(context)
        if (crash != null) {
            val summary = CrashLog.summary(context).orEmpty()
            val ageHours = (System.currentTimeMillis() - CrashLog.file(context).lastModified()) / 3_600_000L
            result += DiagnosticCheck(
                id = "last_crash",
                group = "Движки",
                title = "Последний сбой",
                detail = "$summary (${ageHours} ч назад)",
                status = if (ageHours < 24) CheckStatus.WARN else CheckStatus.INFO,
            )
        }

        val trace = TgTrace.tail(context, 5)
        if (trace.isNotBlank()) {
            result += DiagnosticCheck(
                id = "tg_trace",
                group = "Движки",
                title = "Трассировка Telegram",
                detail = trace,
                status = CheckStatus.INFO,
            )
        }
        return result
    }

    private suspend fun components(context: Context): List<DiagnosticCheck> = withContext(Dispatchers.IO) {
        val result = ArrayList<DiagnosticCheck>()

        val githubOk = runCatching {
            withTimeoutOrNull(8_000) { Net.getJson("https://api.github.com/repos/${BuildConfig.SELF_REPO}") }
        }.getOrNull() != null
        result += DiagnosticCheck(
            id = "github_api",
            group = "Обновления",
            title = "GitHub API",
            detail = if (githubOk) "доступен" else "недоступен — проверки обновлений не работают",
            status = if (githubOk) CheckStatus.OK else CheckStatus.WARN,
        )

        val rawOk = runCatching {
            withTimeoutOrNull(8_000) {
                Net.getText("https://raw.githubusercontent.com/${BuildConfig.BYEDPI_REPO}/master/README.md")
            }
        }.getOrNull() != null
        result += DiagnosticCheck(
            id = "raw_github",
            group = "Обновления",
            title = "raw.githubusercontent.com",
            detail = if (rawOk) "доступен — стратегии и списки обновятся" else "недоступен (ограничение или сеть)",
            status = if (rawOk) CheckStatus.OK else CheckStatus.WARN,
        )

        val cfFile = CfDomains.file(context)
        val cfCount = CfDomains.cached(context).size
        val cfAgeHours = if (cfFile.exists()) {
            (System.currentTimeMillis() - cfFile.lastModified()) / 3_600_000L
        } else {
            -1
        }
        result += DiagnosticCheck(
            id = "cf_domains",
            group = "Обновления",
            title = "Пул CF-доменов",
            detail = when {
                cfCount < 3 -> "не загружен — используется встроенный список"
                cfAgeHours >= 0 -> "$cfCount шт., обновлён ${cfAgeHours} ч назад"
                else -> "$cfCount шт."
            },
            status = if (cfCount >= 3) CheckStatus.OK else CheckStatus.WARN,
            fix = DiagnosticFix.UPDATE_COMPONENTS,
            fixLabel = if (cfCount >= 3) "" else "Обновить",
        )

        val latest = runCatching { withTimeoutOrNull(8_000) { GitHub.latestRelease(BuildConfig.SELF_REPO) } }.getOrNull()
        val updateAvailable = latest != null && Versions.isNewer(latest.version, BuildConfig.VERSION_NAME)
        result += DiagnosticCheck(
            id = "app_update",
            group = "Обновления",
            title = "Версия приложения",
            detail = when {
                latest == null -> "не удалось узнать последнюю версию"
                updateAvailable -> "доступна ${latest.tag}, установлена ${BuildConfig.VERSION_NAME}"
                else -> "актуальна (${BuildConfig.VERSION_NAME})"
            },
            status = when {
                latest == null -> CheckStatus.WARN
                updateAvailable -> CheckStatus.WARN
                else -> CheckStatus.OK
            },
            fix = if (updateAvailable) DiagnosticFix.UPDATE_COMPONENTS else DiagnosticFix.NONE,
            fixLabel = if (updateAvailable) "Открыть" else "",
        )

        val freeBytes = context.filesDir.usableSpace
        result += DiagnosticCheck(
            id = "storage",
            group = "Обновления",
            title = "Свободное место",
            detail = Versions.humanBytes(freeBytes.coerceAtLeast(0)),
            status = if (freeBytes > CACHE_WARN_MB * 1024L * 1024) CheckStatus.OK else CheckStatus.WARN,
        )
        return@withContext result
    }

    private suspend fun network(context: Context): List<DiagnosticCheck> = withContext(Dispatchers.IO) {
        val result = ArrayList<DiagnosticCheck>()

        val vpnActive = runCatching {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            cm?.allNetworks?.any { network ->
                cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            } ?: false
        }.getOrDefault(false)
        result += DiagnosticCheck(
            id = "vpn_active",
            group = "Сеть",
            title = "Активный VPN",
            detail = if (vpnActive) "есть VPN-интерфейс (наш ускорение или сторонний)" else "VPN не активен",
            status = CheckStatus.INFO,
        )

        val dns = runCatching {
            withTimeoutOrNull(5_000) { InetAddress.getByName("www.youtube.com") }
        }.getOrNull()
        result += DiagnosticCheck(
            id = "dns",
            group = "Сеть",
            title = "DNS",
            detail = dns?.hostAddress?.let { "www.youtube.com → $it" } ?: "не удалось разрешить имя",
            status = if (dns != null) CheckStatus.OK else CheckStatus.FAIL,
        )

        val tcp443 = tcpOpen("www.youtube.com", 443)
        result += DiagnosticCheck(
            id = "tcp443",
            group = "Сеть",
            title = "TCP 443",
            detail = if (tcp443) "соединение с youtube.com:443 есть" else "не удалось подключиться",
            status = if (tcp443) CheckStatus.OK else CheckStatus.FAIL,
        )

        val tls = tlsHandshake("www.youtube.com", 443)
        result += DiagnosticCheck(
            id = "tls",
            group = "Сеть",
            title = "TLS",
            detail = if (tls) "рукопожатие прошло" else "рукопожатие не прошло (возможно ограничение)",
            status = if (tls) CheckStatus.OK else CheckStatus.WARN,
        )

        val http = runCatching {
            withTimeoutOrNull(8_000) { Net.getText("https://cp.cloudflare.com/generate_204") }
        }.getOrNull() != null
        result += DiagnosticCheck(
            id = "http",
            group = "Сеть",
            title = "HTTP",
            detail = if (http) "cp.cloudflare.com/generate_204 отвечает" else "HTTP-запрос не прошёл",
            status = if (http) CheckStatus.OK else CheckStatus.WARN,
        )

        val ipv6 = runCatching {
            withTimeoutOrNull(4_000) { InetAddress.getByName("ipv6.google.com") }
        }.getOrNull() != null
        result += DiagnosticCheck(
            id = "ipv6",
            group = "Сеть",
            title = "IPv6",
            detail = if (ipv6) "доступен" else "недоступен (тоннель работает по IPv4)",
            status = CheckStatus.INFO,
        )
        return@withContext result
    }

    private fun tcpOpen(host: String, port: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 2_000)
        }
        true
    }.getOrDefault(false)

    private fun tlsHandshake(host: String, port: Int): Boolean = runCatching {
        (SSLSocketFactory.getDefault().createSocket(host, port) as SSLSocket).use { socket ->
            socket.soTimeout = 4_000
            socket.sslParameters = socket.sslParameters.apply {
                // Без этого проверяется только цепочка сертификатов, но не hostname.
                endpointIdentificationAlgorithm = "HTTPS"
            }
            socket.startHandshake()
        }
        true
    }.getOrDefault(false)
}
