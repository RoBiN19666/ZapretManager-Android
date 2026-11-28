package io.github.robin19666.zapretmanager.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

enum class SiteVerdict { OK, DNS, TCP, TLS, HTTP, UNKNOWN }

data class SiteStep(
    val name: String,
    val ok: Boolean,
    val millis: Long,
    val detail: String,
)

data class SiteCheckReport(
    val host: String,
    val steps: List<SiteStep>,
    val verdict: SiteVerdict,
    val address: String = "",
    val viaSocks: Boolean = false,
) {
    fun verdictText(): String = when (verdict) {
        SiteVerdict.OK -> "Сайт открывается"
        SiteVerdict.DNS -> "Похоже на проблему DNS"
        SiteVerdict.TCP -> "Соединение не устанавливается (похоже на ограничение провайдера)"
        SiteVerdict.TLS -> "TLS не проходит — соединение обрывается на рукопожатии"
        SiteVerdict.HTTP -> "Соединение есть, но HTTP-ответ не приходит"
        SiteVerdict.UNKNOWN -> "Не удалось определить причину"
    }

    fun hint(): String = when (verdict) {
        SiteVerdict.OK -> "Если другие сайты не открываются, проверьте выбранную стратегию."
        SiteVerdict.DNS -> "Попробуйте другой DNS в настройках или включите ускорение соединения."
        SiteVerdict.TCP -> "Включите ускорение соединения и подберите стратегию (тест стратегий или автоподбор)."
        SiteVerdict.TLS -> "Смените стратегию: помогут fake TLS, split или disorder."
        SiteVerdict.HTTP -> "Проверьте прокси/VPN и повторите; возможно, помогает TCP-fallback."
        SiteVerdict.UNKNOWN -> "Проверьте сеть и повторите проверку."
    }

    fun report(): String = buildString {
        appendLine("Проверка сайта: $host")
        appendLine("через SOCKS byedpi: ${if (viaSocks) "да" else "нет"}")
        address.takeIf { it.isNotBlank() }?.let { appendLine("адрес: $it") }
        steps.forEach { step ->
            appendLine("[${if (step.ok) "OK" else "FAIL"}] ${step.name} (${step.millis} мс): ${step.detail}")
        }
        appendLine("вердикт: ${verdictText()} — ${hint()}")
    }
}

/**
 * Пошаговая проверка сайта: DNS → TCP:443 → TLS → HTTP.
 * Если ускорение соединения включён, TCP/TLS/HTTP идут через SOCKS-порт byedpi —
 * то есть тем же путём, что и обычный трафик.
 */
object SiteCheckEngine {
    suspend fun check(rawHost: String, socksPort: Int? = null): SiteCheckReport = withContext(Dispatchers.IO) {
        val host = normalizeHost(rawHost)
        if (host.isBlank()) {
            return@withContext SiteCheckReport(
                host = rawHost,
                steps = emptyList(),
                verdict = SiteVerdict.UNKNOWN,
            )
        }
        val proxy = if (socksPort != null && socksPort > 0) {
            Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
        } else {
            Proxy.NO_PROXY
        }
        val steps = ArrayList<SiteStep>()

        val dnsStarted = System.currentTimeMillis()
        val address = runCatching {
            withTimeoutOrNull(5_000) { InetAddress.getByName(host) }
        }.getOrNull()
        steps += SiteStep(
            name = "DNS",
            ok = address != null,
            millis = System.currentTimeMillis() - dnsStarted,
            detail = address?.hostAddress ?: "имя не разрешилось",
        )

        val tcpStarted = System.currentTimeMillis()
        val socket = runCatching {
            val plain = Socket(proxy)
            plain.connect(InetSocketAddress(host, 443), 6_000)
            plain
        }.getOrNull()
        steps += SiteStep(
            name = "TCP 443",
            ok = socket != null,
            millis = System.currentTimeMillis() - tcpStarted,
            detail = if (socket != null) "соединение установлено" else "подключиться не удалось",
        )

        var tlsSocket: SSLSocket? = null
        val tlsStarted = System.currentTimeMillis()
        val tlsOk = runCatching {
            val base = socket ?: error("нет TCP-соединения")
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val ssl = factory.createSocket(base, host, 443, true) as SSLSocket
            ssl.soTimeout = 6_000
            ssl.sslParameters = ssl.sslParameters.apply {
                // Проверяем не только цепочку, но и соответствие сертификата host.
                endpointIdentificationAlgorithm = "HTTPS"
            }
            // Запоминаем сокет до рукопожатия: если startHandshake() бросит,
            // иначе он останется незакрытым.
            tlsSocket = ssl
            ssl.startHandshake()
            true
        }.getOrDefault(false)
        steps += SiteStep(
            name = "TLS",
            ok = tlsOk,
            millis = System.currentTimeMillis() - tlsStarted,
            detail = if (tlsOk) "рукопожатие прошло" else "рукопожатие не прошло",
        )

        runCatching { tlsSocket?.close() }
        runCatching { socket?.close() }

        val httpStarted = System.currentTimeMillis()
        val httpOk = runCatching {
            withTimeoutOrNull(8_000) {
                val connection = URL("https://$host/").openConnection(proxy) as HttpURLConnection
                connection.connectTimeout = 6_000
                connection.readTimeout = 6_000
                connection.instanceFollowRedirects = false
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "ZapretManager-Android")
                try {
                    connection.responseCode in 200..499
                } finally {
                    connection.disconnect()
                }
            } ?: false
        }.getOrDefault(false)
        steps += SiteStep(
            name = "HTTP",
            ok = httpOk,
            millis = System.currentTimeMillis() - httpStarted,
            detail = if (httpOk) "ответ получен" else "ответа нет",
        )

        val verdict = classify(
            dns = address != null,
            tcp = socket != null,
            tls = tlsOk,
            http = httpOk,
        )
        SiteCheckReport(
            host = host,
            steps = steps,
            verdict = verdict,
            address = address?.hostAddress.orEmpty(),
            viaSocks = proxy != Proxy.NO_PROXY,
        )
    }

    fun classify(dns: Boolean, tcp: Boolean, tls: Boolean, http: Boolean): SiteVerdict = when {
        !dns -> SiteVerdict.DNS
        !tcp -> SiteVerdict.TCP
        !tls -> SiteVerdict.TLS
        !http -> SiteVerdict.HTTP
        else -> SiteVerdict.OK
    }

    fun normalizeHost(raw: String): String {
        return raw.trim()
            .lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore(':')
            .removePrefix("www.")
            .trim()
    }
}
