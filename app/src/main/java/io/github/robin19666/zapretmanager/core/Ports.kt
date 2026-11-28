package io.github.robin19666.zapretmanager.core

import java.net.InetSocketAddress
import java.net.ServerSocket
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
    /**
     * Соединение с портом. Подходит для разовой проверки чужого порта, но для
     * регулярного опроса своего рантайма не годится: каждое подключение без
     * данных рантайм считает отклонённым рукопожатием (`handshake_rejected`),
     * а в статусе это выглядит как «клиенты ломятся, а их отбивают».
     */
    fun isOpen(port: Int, timeoutMs: Int = 400, host: String = "127.0.0.1"): Boolean {
        if (port <= 0) return false
        return runCatching {
            Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
            true
        }.getOrDefault(false)
    }

    /**
     * Проверка «порт занят» без подключения: пробуем занять его сами.
     *
     * `BindException` означает, что порт уже слушают (наш рантайм). Такой опрос
     * не создаёт соединений и не шумит в статистике рантайма, поэтому именно он
     * используется для периодических проверок живости.
     */
    fun isListening(port: Int): Boolean {
        if (port <= 0) return false
        return try {
            ServerSocket().use { socket ->
                socket.bind(InetSocketAddress("127.0.0.1", port), 1)
            }
            false
        } catch (_: java.net.BindException) {
            true
        } catch (_: Exception) {
            false
        }
    }
}
