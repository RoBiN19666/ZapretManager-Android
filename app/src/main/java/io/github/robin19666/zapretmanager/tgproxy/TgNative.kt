package io.github.robin19666.zapretmanager.tgproxy

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import io.github.robin19666.zapretmanager.core.LogBus

/**
 * JNA-интерфейс к Go-рантайму TG WS Proxy (Regstar2/tg-ws-proxy-android,
 * native/tgwsproxy, сборка -buildmode=c-shared в libtgwsproxy.so).
 *
 * Функции возвращают *C.char, которые обязательно освобождать через FreeString.
 */
interface TgWsLibrary : Library {
    fun StartMtProtoProxy(host: String, port: Int, secret: String, runtimeConfig: String, verbose: Int): Int
    fun StopMtProtoProxy(): Int
    fun GetMtProtoProxyStatus(): Pointer?
    fun FreeString(pointer: Pointer)
    fun SetCachedCFDomains(domains: String)
    fun SetManualCFDomains(domains: String)
    fun ResetCFDomainCooldowns()
    fun UpdateMtProtoRoutePolicy(runtimeConfig: String): Int
}

object TgNative {
    private var library: TgWsLibrary? = null
    private var loadError: String? = null

    @Synchronized
    fun get(): TgWsLibrary? {
        library?.let { return it }
        if (loadError != null) return null
        return try {
            val loaded = Native.load("tgwsproxy", TgWsLibrary::class.java)
            library = loaded
            LogBus.i("Telegram", "Go-рантайм libtgwsproxy.so загружен")
            loaded
        } catch (t: Throwable) {
            loadError = t.message ?: t.javaClass.simpleName
            LogBus.e("Telegram", "Не удалось загрузить libtgwsproxy.so: $loadError")
            null
        }
    }

    fun unavailableReason(): String? = loadError

    fun statusText(): String? {
        val lib = get() ?: return null
        val pointer = lib.GetMtProtoProxyStatus() ?: return null
        return try {
            pointer.getString(0)
        } finally {
            runCatching { lib.FreeString(pointer) }
        }
    }

    /** Домены Cloudflare Proxy: минимум 3, иначе рантайм молча игнорирует список. */
    fun applyCachedDomains(domains: List<String>): Boolean {
        val lib = get() ?: return false
        val cleaned = domains.map { it.trim() }.filter { it.isNotEmpty() && !it.contains('|') }
        if (cleaned.size < 3) return false
        return runCatching {
            lib.SetCachedCFDomains(cleaned.joinToString("|"))
            LogBus.i("Telegram", "Пул CF-доменов обновлён: ${cleaned.size} шт.")
            true
        }.getOrElse {
            LogBus.w("Telegram", "Не удалось применить CF-домены: ${it.message}")
            false
        }
    }
}
