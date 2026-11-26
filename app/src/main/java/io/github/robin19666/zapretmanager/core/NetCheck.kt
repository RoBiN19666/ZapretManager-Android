package io.github.robin19666.zapretmanager.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

data class SiteCheck(
    val name: String,
    val host: String,
    val ok: Boolean,
    val millis: Long,
    val detail: String,
)

/**
 * Быстрая проверка доступности сервисов.
 *
 * Если обход DPI включён, запросы идут через SOCKS5-порт byedpi — то есть
 * ровно тем путём, которым ходит обычный трафик, с применением desync.
 * Без туннеля проверяется прямое соединение.
 */
object NetCheck {
    private data class Target(val name: String, val url: String, val host: String)

    private val targets = listOf(
        Target("YouTube", "https://www.youtube.com/generate_204", "youtube.com"),
        Target("Discord", "https://discord.com/api/v9/gateway", "discord.com"),
        Target("Telegram", "https://web.telegram.org/", "telegram.org"),
        Target("Cloudflare", "https://cp.cloudflare.com/generate_204", "cloudflare.com"),
    )

    suspend fun run(socksPort: Int? = null, timeoutMs: Int = 7_000): List<SiteCheck> = withContext(Dispatchers.IO) {
        val proxy = if (socksPort != null && socksPort > 0) {
            Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
        } else {
            Proxy.NO_PROXY
        }
        targets.map { target -> check(target, proxy, timeoutMs) }
    }

    private fun check(target: Target, proxy: Proxy, timeoutMs: Int = 7_000): SiteCheck {
        val started = System.currentTimeMillis()
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(target.url).openConnection(proxy) as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("User-Agent", "ZapretManager-Android")
                setRequestProperty("Cache-Control", "no-cache")
            }
            val code = connection.responseCode
            val elapsed = System.currentTimeMillis() - started
            SiteCheck(
                name = target.name,
                host = target.host,
                ok = code in 200..499,
                millis = elapsed,
                detail = "HTTP $code",
            )
        } catch (t: Throwable) {
            SiteCheck(
                name = target.name,
                host = target.host,
                ok = false,
                millis = System.currentTimeMillis() - started,
                detail = t.message?.take(50) ?: t.javaClass.simpleName,
            )
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    /** TCP-проверка локального прокси Telegram. */
    suspend fun checkTgProxy(port: Int): SiteCheck = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        try {
            java.net.Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), 1500)
            }
            SiteCheck("Telegram-прокси", "127.0.0.1:$port", true, System.currentTimeMillis() - started, "порт открыт")
        } catch (t: Throwable) {
            SiteCheck(
                "Telegram-прокси",
                "127.0.0.1:$port",
                false,
                System.currentTimeMillis() - started,
                t.message?.take(50) ?: "недоступен",
            )
        }
    }
}
