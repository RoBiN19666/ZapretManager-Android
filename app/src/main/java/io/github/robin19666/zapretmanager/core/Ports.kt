package io.github.robin19666.zapretmanager.core

import java.net.InetSocketAddress
import java.net.Socket

/**
 * Проверка, слушает ли кто-то порт на локальном интерфейсе.
 *
 * Локальные порты — единственный честный признак того, что движок жив: сервисы
 * живут в отдельных процессах и могут умереть молча, а UI-процесс продолжит
 * показывать последнее полученное состояние. Поэтому `DpiEngine`,
 * `TgProxyController` и сервис Telegram периодически проверяют порт.
 */
object Ports {
    fun isOpen(port: Int, timeoutMs: Int = 400, host: String = "127.0.0.1"): Boolean {
        if (port <= 0) return false
        return runCatching {
            Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
            true
        }.getOrDefault(false)
    }
}
