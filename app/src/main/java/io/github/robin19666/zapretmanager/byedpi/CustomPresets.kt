package io.github.robin19666.zapretmanager.byedpi

import android.content.Context
import io.github.robin19666.zapretmanager.core.LogBus
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.random.Random

/** Именованная конфигурация аргументов byedpi, сохранённая пользователем. */
data class CustomPreset(
    val id: String,
    val name: String,
    val args: String,
)

/**
 * Свои конфигурации: имя + аргументы.
 *
 * Раньше можно было сохранить только одну строку «своих аргументов» — без имени,
 * без списка и без удаления. Теперь это именованные пресеты в
 * `files/custom-presets.json`: они попадают в общий список стратегий (источник
 * «свои»), участвуют в тесте стратегий и могут быть удалены по одному.
 */
object CustomPresets {
    private const val FILE_NAME = "custom-presets.json"
    private const val MAX_PRESETS = 200

    private val lock = Any()

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun all(context: Context): List<CustomPreset> = synchronized(lock) {
        readLocked(context)
    }

    /**
     * Сохраняет новую конфигурацию или обновляет существующую с тем же именем
     * (регистр и лишние пробелы не важны). Пустое имя заменяется на подсказку
     * из самих аргументов.
     */
    fun save(context: Context, name: String, args: String): CustomPreset? {
        val cleanArgs = args.trim()
        if (cleanArgs.isEmpty()) return null
        val presetName = name.trim().ifBlank { defaultName(cleanArgs) }
        synchronized(lock) {
            val current = readLocked(context)
            val next = upsert(current, presetName, cleanArgs, newId())
            if (next.size == current.size && current.none { it.name.equals(presetName, ignoreCase = true) }) {
                // Новая запись не добавилась: упёрлись в лимит.
                LogBus.w("Стратегии", "Слишком много своих конфигураций (максимум $MAX_PRESETS)")
                return null
            }
            writeLocked(context, next)
            return next.firstOrNull { it.name.equals(presetName, ignoreCase = true) }
        }
    }

    fun delete(context: Context, id: String): Boolean = synchronized(lock) {
        val current = readLocked(context)
        val next = remove(current, id)
        if (next.size == current.size) return false
        writeLocked(context, next)
        LogBus.i("Стратегии", "Своя конфигурация удалена")
        true
    }

    fun rename(context: Context, id: String, name: String): Boolean {
        val clean = name.trim()
        if (clean.isEmpty()) return false
        synchronized(lock) {
            val current = readLocked(context)
            if (current.none { it.id == id }) return false
            if (current.any { it.id != id && it.name.equals(clean, ignoreCase = true) }) return false
            writeLocked(context, current.map { if (it.id == id) it.copy(name = clean) else it })
            return true
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            runCatching { file(context).delete() }
        }
    }

    // ---- Хранение -----------------------------------------------------------

    private fun readLocked(context: Context): List<CustomPreset> {
        val target = file(context)
        if (!target.isFile) return emptyList()
        return runCatching { parse(target.readText()) }
            .onFailure { LogBus.w("Стратегии", "Список своих конфигураций повреждён: ${it.message}") }
            .getOrDefault(emptyList())
    }

    private fun writeLocked(context: Context, presets: List<CustomPreset>) {
        runCatching {
            val target = file(context)
            target.parentFile?.mkdirs()
            val text = serialize(presets)
            val temp = File(target.parentFile, "${target.name}.tmp")
            temp.writeText(text)
            if (!temp.renameTo(target)) {
                target.writeText(text)
                temp.delete()
            }
        }.onFailure {
            LogBus.e("Стратегии", "Не удалось сохранить свои конфигурации: ${it.message}")
        }
    }

    // ---- Чистая часть (покрыта тестами) -------------------------------------

    internal fun newId(): String =
        "p-" + System.currentTimeMillis().toString(36) + "-" + Random.nextInt(0x10000).toString(16)

    /** Подсказка имени, если пользователь его не ввёл: первые флаги команды. */
    internal fun defaultName(args: String): String {
        val compact = args.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(3)
            .joinToString(" ")
        return if (compact.length <= 32) compact else compact.take(31) + "…"
    }

    internal fun parse(text: String): List<CustomPreset> {
        if (text.isBlank()) return emptyList()
        val array = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        val result = ArrayList<CustomPreset>(array.length())
        val seenIds = HashSet<String>()
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            val args = json.optString("args").trim()
            if (args.isEmpty()) continue
            val name = json.optString("name").trim().ifBlank { defaultName(args) }
            var id = json.optString("id").trim()
            if (id.isEmpty() || !seenIds.add(id)) {
                id = newId() + "-$index"
                seenIds.add(id)
            }
            result += CustomPreset(id = id, name = name, args = args)
        }
        return result
    }

    internal fun serialize(presets: List<CustomPreset>): String {
        val array = JSONArray()
        presets.forEach { preset ->
            array.put(
                JSONObject()
                    .put("id", preset.id)
                    .put("name", preset.name)
                    .put("args", preset.args),
            )
        }
        return array.toString(2)
    }

    /** Обновляет запись с тем же именем или добавляет новую (не больше [MAX_PRESETS]). */
    internal fun upsert(
        presets: List<CustomPreset>,
        name: String,
        args: String,
        id: String,
    ): List<CustomPreset> {
        val cleanName = name.trim()
        val cleanArgs = args.trim()
        val index = presets.indexOfFirst { it.name.equals(cleanName, ignoreCase = true) }
        return when {
            index >= 0 -> presets.toMutableList().also {
                it[index] = it[index].copy(name = cleanName, args = cleanArgs)
            }
            presets.size >= MAX_PRESETS -> presets
            else -> presets + CustomPreset(id = id, name = cleanName, args = cleanArgs)
        }
    }

    internal fun remove(presets: List<CustomPreset>, id: String): List<CustomPreset> =
        presets.filterNot { it.id == id }
}
