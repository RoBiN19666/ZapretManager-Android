package io.github.robin19666.zapretmanager.core

import android.content.Context
import java.io.File

/**
 * Журнал «настройки до перебора».
 *
 * Тест стратегий и автоподбор поднимают движок на каждом кандидате и поэтому
 * временно переписывают `strategyId` / `customArgs` в настройках. Штатно они
 * возвращаются в `finally`, но если процесс убьют посреди перебора (нехватка
 * памяти, свайп из недавних, падение в нативном коде), пользователь останется
 * с чужими аргументами и «сломанным» ускорением.
 *
 * Поэтому исходные значения кладутся в файл до старта: при следующем запуске
 * приложения [restoreIfNeeded] вернёт их обратно.
 */
object TuningJournal {
    private const val FILE_NAME = "tuning-backup.txt"

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun save(context: Context, strategyId: String, customArgs: String) {
        runCatching {
            file(context).writeText(strategyId + "\n" + customArgs)
        }.onFailure {
            LogBus.w("Автоподбор", "Не удалось сохранить исходные настройки: ${it.message}")
        }
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    /**
     * Возвращает настройки, если прошлый перебор не завершился.
     * Вызывается из [io.github.robin19666.zapretmanager.App] в основном процессе.
     */
    fun restoreIfNeeded(context: Context): Boolean {
        val file = file(context)
        if (!file.isFile) return false
        val text = runCatching { file.readText() }.getOrNull()
        file.delete()
        if (text == null) return false
        val strategyId = text.substringBefore('\n')
        val customArgs = text.substringAfter('\n', "")
        if (strategyId.isBlank()) return false
        Settings.update { it.copy(strategyId = strategyId, customArgs = customArgs) }
        LogBus.w("Автоподбор", "Прошлый перебор прервался — возвращена прежняя стратегия «$strategyId»")
        return true
    }
}
