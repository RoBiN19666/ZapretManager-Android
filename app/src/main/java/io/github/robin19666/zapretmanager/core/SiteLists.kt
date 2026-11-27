package io.github.robin19666.zapretmanager.core

import android.content.Context
import java.io.File

data class SiteListInfo(
    val id: String,
    val title: String,
    val count: Int,
    val modified: Boolean,
)

data class SaveReport(
    val total: Int,
    val unique: Int,
    val duplicates: Int,
    val invalid: Int,
) {
    val summary: String
        get() = buildString {
            append("доменов: $unique")
            if (duplicates > 0) append(", дублей убрано: $duplicates")
            if (invalid > 0) append(", отброшено: $invalid")
        }
}

/**
 * Списки доменов для проверки сайтов и тестов стратегий.
 *
 * Файлы берутся из ByeByeDPI (`proxytest_*.sites`) и обновляются компонентами,
 * но пользователь может править их вручную: тогда рядом хранится исходная копия
 * `.upstream`, к которой можно откатиться.
 */
object SiteLists {
    data class Entry(val id: String, val title: String)

    val catalog: List<Entry> = listOf(
        Entry("youtube", "YouTube"),
        Entry("googlevideo", "Googlevideo"),
        Entry("discord", "Discord"),
        Entry("telegram", "Telegram"),
        Entry("general", "Общий"),
        Entry("social", "Соцсети"),
        Entry("cloudflare", "Cloudflare"),
        Entry("türkiye", "Türkiye"),
    )

    private fun dir(context: Context): File = File(context.filesDir, "components/sites").apply { mkdirs() }

    /** Имя файла совпадает с upstream: `proxytest_<id>.sites`. */
    fun file(context: Context, id: String): File = File(dir(context), "proxytest_$id.sites")

    fun baselineFile(context: Context, id: String): File = File(dir(context), "proxytest_$id.upstream")

    fun load(context: Context, id: String): List<String> {
        val target = file(context, id)
        if (!target.exists()) return emptyList()
        return runCatching { normalize(target.readText()).domains }.getOrDefault(emptyList())
    }

    fun baseline(context: Context, id: String): List<String> {
        val target = baselineFile(context, id)
        if (!target.exists()) return emptyList()
        return runCatching { normalize(target.readText()).domains }.getOrDefault(emptyList())
    }

    fun isModified(context: Context, id: String): Boolean {
        val current = load(context, id)
        val base = baseline(context, id)
        return current.isNotEmpty() && current != base
    }

    fun info(context: Context): List<SiteListInfo> = catalog.map { entry ->
        SiteListInfo(
            id = entry.id,
            title = entry.title,
            count = load(context, entry.id).size,
            modified = isModified(context, entry.id),
        )
    }

    /** Сохраняет введённый список: нормализует, убирает дубли и мусор. */
    fun save(context: Context, id: String, text: String): SaveReport {
        val parsed = normalize(text)
        val target = file(context, id)
        target.parentFile?.mkdirs()
        target.writeText(parsed.domains.joinToString("\n"))
        LogBus.i("Списки", "$id: сохранено ${parsed.domains.size} доменов")
        return SaveReport(
            total = parsed.total,
            unique = parsed.domains.size,
            duplicates = parsed.duplicates,
            invalid = parsed.invalid,
        )
    }

    /** Сохраняет исходную копию (вызывается при обновлении из репозитория). */
    fun saveBaseline(context: Context, id: String, text: String) {
        val target = baselineFile(context, id)
        target.parentFile?.mkdirs()
        target.writeText(normalize(text).domains.joinToString("\n"))
    }

    fun reset(context: Context, id: String): Boolean {
        val base = baselineFile(context, id)
        if (!base.exists()) {
            file(context, id).delete()
            return false
        }
        file(context, id).writeText(base.readText())
        return true
    }

    fun clear(context: Context, id: String) {
        file(context, id).delete()
    }

    /** Нормализация: без схемы/пути, нижний регистр, без пустых и дублей. */
    fun normalize(text: String): Parsed {
        val domains = ArrayList<String>()
        val seen = HashSet<String>()
        var total = 0
        var duplicates = 0
        var invalid = 0
        text.lineSequence().forEach { raw ->
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return@forEach
            total++
            var host = line
                .lowercase()
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore('/')
                .substringBefore('?')
                .trim()
                .trimEnd('.')
            if (host.startsWith("www.")) host = host.removePrefix("www.")
            val valid = host.isNotBlank() &&
                !host.contains(' ') &&
                host.contains('.') &&
                host.all { it.isLetterOrDigit() || it in ".-_*" }
            if (!valid) {
                invalid++
                return@forEach
            }
            if (!seen.add(host)) {
                duplicates++
                return@forEach
            }
            domains += host
        }
        return Parsed(domains, total, duplicates, invalid)
    }

    data class Parsed(
        val domains: List<String>,
        val total: Int,
        val duplicates: Int,
        val invalid: Int,
    )
}
