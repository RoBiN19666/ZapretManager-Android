package io.github.robin19666.zapretmanager.tgproxy

import io.github.robin19666.zapretmanager.core.Settings

/**
 * Правила формирования конфигурации и ссылки Telegram-прокси.
 * Секрет — 32 hex-символа (16 байт), как требует Go-рантайм; префиксы dd/ee
 * добавляются только в ссылке.
 */
object TgConfig {
    const val HOST = "127.0.0.1"

    /** Секрет прокси: единый для всех процессов, хранится в `files/tg-secret.txt`. */
    fun ensureSecret(context: android.content.Context): String = TgSecret.ensure(context)

    /**
     * Строка токенов рантайма: маршруты, CF-прокси, fake TLS.
     * Набор флагов повторяет то, что формирует upstream-приложение, чтобы
     * рантайм не выключал CF-прокси из-за «явной» политики маршрутов.
     */
    fun runtimeTokens(
        route: String,
        fakeTlsDomain: String,
        allowFallback: Boolean = true,
        cfNoTls: Boolean = true,
        cachedDomains: List<String> = emptyList(),
    ): String {
        val tokens = ArrayList<String>()
        val cf = route == "cf_proxy_ws" || route == "cf"
        val direct = route == "direct_ws" || route == "direct"
        val tcp = route == "tcp_fallback" || route == "tcp"
        val awg = route == "awg_warp" || route == "awg"
        val worker = route == "cf_worker_ws" || route == "worker"

        val mode = when {
            cf && !allowFallback -> "cf_only"
            cf -> "cf_first"
            worker -> "worker_first"
            tcp -> "tcp_only"
            else -> "direct_only"
        }
        tokens += "@connection_mode=$mode"
        tokens += "@cfproxy=${if (cf) 1 else 0}"
        tokens += "@cfproxy_priority=${if (cf && allowFallback) 1 else 0}"
        tokens += "@cfproxy_only=${if (cf && !allowFallback) 1 else 0}"
        if (cf && cfNoTls) tokens += "@cfproxy_no_tls=1"

        tokens += "@route_order=$route"
        tokens += "@route_direct_ws=${if (direct) 1 else 0}"
        tokens += "@route_worker_ws=${if (worker) 1 else 0}"
        tokens += "@route_cf_proxy_ws=${if (cf) 1 else 0}"
        tokens += "@route_awg_warp=${if (awg) 1 else 0}"
        tokens += "@route_tcp_fallback=${if (tcp) 1 else 0}"
        tokens += "@preferred_route=$route"
        tokens += "@route_fallback=${if (allowFallback) 1 else 0}"

        tokens += "@worker_enabled=0"
        tokens += "@auto_strategy=balanced"
        appendCachedDomains(tokens, cachedDomains)
        if (fakeTlsDomain.isNotBlank()) {
            tokens += "@mtproto_fake_tls_domain=$fakeTlsDomain"
        }
        return tokens.joinToString(",")
    }

    /**
     * Минимальный конфиг, с которым upstream-приложение запускает прокси
     * «по умолчанию»: маршруты выбирает сам рантайм (CF + встроенный пул).
     */
    fun minimalTokens(
        fakeTlsDomain: String,
        cachedDomains: List<String> = emptyList(),
    ): String {
        val tokens = ArrayList<String>()
        if (fakeTlsDomain.isNotBlank()) {
            tokens += "@mtproto_fake_tls_domain=$fakeTlsDomain"
        }
        appendCachedDomains(tokens, cachedDomains)
        return tokens.joinToString(",")
    }

    /** Пул CF-доменов передаётся рантайму токеном — без отдельного JNI-вызова на старте. */
    private fun appendCachedDomains(tokens: MutableList<String>, domains: List<String>) {
        val cleaned = domains.map { it.trim() }
            .filter { it.isNotEmpty() && !it.contains('|') && !it.contains(',') }
            .distinct()
        if (cleaned.size >= 3) {
            tokens += "@cf_cached_domains=${cleaned.joinToString("|")}"
        }
    }

    /** Секрет в формате ссылки: dd<hex> либо ee<hex><hex(domain)>. */
    fun linkSecret(secret: String, fakeTlsDomain: String): String {
        val base = secret.lowercase()
        if (fakeTlsDomain.isBlank()) return "dd$base"
        val domainHex = fakeTlsDomain.toByteArray(Charsets.US_ASCII).joinToString("") { "%02x".format(it) }
        return "ee$base$domainHex"
    }

    fun tgLink(port: Int, secret: String, fakeTlsDomain: String): String =
        "tg://proxy?server=$HOST&port=$port&secret=${linkSecret(secret, fakeTlsDomain)}"

    fun tmeLink(port: Int, secret: String, fakeTlsDomain: String): String =
        "https://t.me/proxy?server=$HOST&port=$port&secret=${linkSecret(secret, fakeTlsDomain)}"

    fun socksLink(port: Int): String = "https://t.me/socks?server=$HOST&port=$port"

    fun routeLabel(route: String): String = when (route) {
        "cf_proxy_ws" -> "Cloudflare Proxy (WS)"
        "direct_ws" -> "Прямой WebSocket"
        "cf_worker_ws" -> "Cloudflare Worker"
        "awg_warp" -> "AmneziaWG / WARP"
        "tcp_fallback" -> "Прямой TCP"
        else -> route
    }

    /** Разбор строки статуса Go-рантайма: "status=...;outbound=...;...". */
    fun parseStatus(text: String?): Map<String, String> {
        if (text.isNullOrBlank()) return emptyMap()
        return text.split(';').mapNotNull { part ->
            val index = part.indexOf('=')
            if (index <= 0) null else part.substring(0, index).trim() to part.substring(index + 1).trim()
        }.toMap()
    }
}
