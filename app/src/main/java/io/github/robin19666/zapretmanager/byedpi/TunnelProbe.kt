package io.github.robin19666.zapretmanager.byedpi

import kotlinx.coroutines.delay
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Проверка, что SOCKS-порт byedpi реально слушает.
 *
 * Состояние движка приходит широковещательным сообщением и может оказаться
 * «запоздалым» от предыдущего запуска. Тест стратегий и автоподбор обязаны
 * убедиться, что туннель поднялся на самом деле, иначе проверки мгновенно
 * падают (0/4 за 0 мс).
 */
object TunnelProbe {
    fun socksOpen(port: Int = ByeDpiVpnService.SOCKS_PORT, timeoutMs: Int = 700): Boolean =
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ByeDpiVpnService.SOCKS_HOST, port), timeoutMs)
            }
            true
        }.getOrDefault(false)

    suspend fun awaitOpen(
        port: Int = ByeDpiVpnService.SOCKS_PORT,
        timeoutMs: Long = 8_000,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (socksOpen(port, 500)) return true
            delay(250)
        }
        return false
    }
}
