package io.github.robin19666.zapretmanager.byedpi

import io.github.robin19666.zapretmanager.core.LogBus

/**
 * JNI-обёртка над libzapretnative.so (byedpi + hev-socks5-tunnel).
 * Библиотека грузится лениво: без неё интерфейс продолжает работать и честно
 * сообщает причину, почему ускорение недоступен.
 */
object Native {
    private var loaded = false
    private var loadError: String? = null

    @Synchronized
    fun ensureLoaded(): Boolean {
        if (loaded) return true
        if (loadError != null) return false
        return try {
            System.loadLibrary("zapretnative")
            loaded = true
            LogBus.i("Движок", "Нативная библиотека загружена: ${runCatching { buildInfo() }.getOrDefault("?")}")
            true
        } catch (t: Throwable) {
            loadError = t.message ?: t.javaClass.simpleName
            LogBus.e("Движок", "Не удалось загрузить libzapretnative.so: $loadError")
            false
        }
    }

    fun unavailableReason(): String? = loadError

    external fun startByedpi(args: Array<String>): Int
    external fun stopByedpi(): Int
    external fun byedpiRunning(): Boolean
    external fun tunnelRunning(): Boolean
    external fun startTunnel(config: String, fd: Int): Int
    external fun stopTunnel()
    external fun lastError(): String
    external fun buildInfo(): String
}
