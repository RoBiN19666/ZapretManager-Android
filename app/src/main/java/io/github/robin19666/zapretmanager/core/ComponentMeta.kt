package io.github.robin19666.zapretmanager.core

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * `files/components/meta.json` — установленные версии компонентов.
 *
 * Файл правят два владельца (`UpdateManager` и `CfDomains`), причём второй —
 * ещё и из процесса `:tg`. Прямое чтение-изменение-запись теряло поля: например,
 * обновление стратегий затирало метку времени пула CF-доменов. Здесь правка
 * идёт через один мьютекс, а `.tmp`-файл защищает от обрезанного JSON при
 * аварийном завершении.
 */
object ComponentMeta {
    const val FILE_PATH = "components/meta.json"

    private val lock = Any()

    private fun file(context: Context): File = File(context.filesDir, FILE_PATH)

    fun read(context: Context): JSONObject = synchronized(lock) { readLocked(context) }

    private fun readLocked(context: Context): JSONObject =
        runCatching { JSONObject(file(context).readText()) }.getOrDefault(JSONObject())

    /** Атомарная правка: чтение, изменение и запись под одним замком. */
    fun update(context: Context, block: (JSONObject) -> Unit) {
        synchronized(lock) {
            val json = readLocked(context)
            block(json)
            runCatching {
                val target = file(context)
                target.parentFile?.mkdirs()
                val temp = File(target.parentFile, "${target.name}.tmp")
                temp.writeText(json.toString(2))
                if (!temp.renameTo(target)) {
                    target.writeText(json.toString(2))
                    temp.delete()
                }
            }.onFailure {
                LogBus.w("Компоненты", "Не удалось сохранить meta.json: ${it.message}")
            }
        }
    }
}
