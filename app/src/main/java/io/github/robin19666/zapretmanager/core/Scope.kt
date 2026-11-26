package io.github.robin19666.zapretmanager.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Общий фон приложения: обновления, проверки, отложенные действия. */
object ZmScope {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun launch(block: suspend () -> Unit) {
        scope.launch { runCatching { block() }.onFailure { LogBus.e("Фон", it.message ?: "ошибка") } }
    }
}
