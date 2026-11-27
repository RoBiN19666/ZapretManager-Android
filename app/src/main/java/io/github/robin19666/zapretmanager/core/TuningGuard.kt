package io.github.robin19666.zapretmanager.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Один «эксклюзивный» режим перебора на всё приложение.
 *
 * Тест стратегий и глубокий автоподбор по очереди поднимают/опускают VpnService.
 * Если они работают одновременно (или в это же время сторож/наблюдатель сети
 * перезапускает движок), результаты и пользовательские настройки портятся.
 */
object TuningGuard {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    @Synchronized
    fun tryAcquire(): Boolean {
        if (_active.value) return false
        _active.value = true
        return true
    }

    fun release() {
        _active.value = false
    }
}
