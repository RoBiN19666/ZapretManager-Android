package io.github.robin19666.zapretmanager.tgproxy

import io.github.robin19666.zapretmanager.byedpi.Native
import io.github.robin19666.zapretmanager.core.LogBus

/**
 * Мост к Go-рантайму TG WS Proxy (`libtgwsproxy.so`, сборка c-shared).
 *
 * Вызовы идут через наш JNI-мост в `libzapretnative.so` (dlopen + dlsym).
 * JNA не используется: на Android 15/16 с 16-КБ страницами прослойка
 * libffi/libjnidispatch роняла процесс `:tg` без Java-исключения.
 */
object TgNative {
    private var loaded = false
    private var loadError: String? = null

    @Synchronized
    fun ensureLoaded(): Boolean {
        if (loaded) return true
        if (loadError != null) return false
        if (!Native.ensureLoaded()) {
            loadError = "libzapretnative: ${Native.unavailableReason() ?: "не загрузилась"}"
            return false
        }
        val result = runCatching { nativeEnsure() }.getOrElse {
            loadError = "${it.javaClass.simpleName}: ${it.message}"
            return false
        }
        if (result != 0) {
            loadError = lastErrorNative().ifBlank { "код $result" }
            LogBus.e("Telegram", "Go-рантайм не загрузился: $loadError")
            return false
        }
        loaded = true
        LogBus.i("Telegram", "Go-рантайм libtgwsproxy.so загружен (JNI)")
        return true
    }

    fun unavailableReason(): String? = loadError

    /** Строка статуса рантайма или null, если библиотека недоступна. */
    fun statusText(): String? {
        if (!ensureLoaded()) return null
        return runCatching { statusNative() }.getOrNull()
    }

    fun start(host: String, port: Int, secret: String, config: String, verbose: Int = 1): Int {
        if (!ensureLoaded()) return -1
        return runCatching { startProxyNative(host, port, secret, config, verbose) }
            .onFailure { loadError = "${it.javaClass.simpleName}: ${it.message}" }
            .getOrDefault(-1)
    }

    fun stop(): Int {
        if (!loaded) return -1
        return runCatching { stopProxyNative() }.getOrDefault(-1)
    }

    /** Домены Cloudflare Proxy: рантайм применяет список при следующем запуске. */
    fun applyCachedDomains(domains: List<String>): Boolean {
        if (!ensureLoaded()) return false
        val cleaned = domains.map { it.trim() }.filter { it.isNotEmpty() && !it.contains('|') }
        if (cleaned.size < 3) return false
        return runCatching {
            setCachedDomainsNative(cleaned.joinToString("|"))
            LogBus.i("Telegram", "Пул CF-доменов передан рантайму: ${cleaned.size} шт.")
            true
        }.getOrElse {
            LogBus.w("Telegram", "Не удалось применить CF-домены: ${it.message}")
            false
        }
    }

    private external fun nativeEnsure(): Int
    private external fun startProxyNative(
        host: String,
        port: Int,
        secret: String,
        config: String,
        verbose: Int,
    ): Int
    private external fun stopProxyNative(): Int
    private external fun statusNative(): String?
    private external fun setCachedDomainsNative(domains: String)
    private external fun lastErrorNative(): String
}
