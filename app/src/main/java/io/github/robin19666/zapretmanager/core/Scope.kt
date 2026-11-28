package io.github.robin19666.zapretmanager.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Общий фон приложения: обновления, проверки, отложенные действия. */
object ZmScope {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun launch(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (c: CancellationException) {
                // Отмена — не ошибка: пробрасываем, чтобы корутина завершилась штатно.
                throw c
            } catch (t: Throwable) {
                LogBus.e("Фон", t.message ?: "ошибка")
            }
        }
    }
}
