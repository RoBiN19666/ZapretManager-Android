package io.github.robin19666.zapretmanager.core

import android.content.Context
import java.io.File

/**
 * Списки доменов для движка byedpi.
 *
 * byedpi умеет `-H, --hosts <file>` — «применять обход только к этим доменам»
 * (белый список, как `--hostlist` в zapret на ПК). Обратного режима
 * «обход для всех, кроме списка» в byedpi нет, поэтому чёрный список доменов
 * на уровне движка недоступен — об этом честно написано в интерфейсе.
 */
object Hosts {
    private const val FILE_NAME = "hosts.txt"

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun load(context: Context): List<String> = runCatching {
        val target = file(context)
        if (!target.exists()) return emptyList()
        target.readLines()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.length <= 253 }
            .distinct()
    }.getOrDefault(emptyList())

    fun save(context: Context, domains: List<String>) {
        runCatching {
            val cleaned = domains.map { it.trim().lowercase() }
                .filter { it.isNotEmpty() && !it.contains(' ') && !it.startsWith("#") }
                .distinct()
            file(context).writeText(cleaned.joinToString("\n"))
        }
    }

    fun add(context: Context, domain: String): Boolean {
        val normalized = normalize(domain)
        if (normalized.isBlank()) return false
        val list = load(context).toMutableList()
        if (list.contains(normalized)) return false
        list += normalized
        save(context, list)
        return true
    }

    fun remove(context: Context, domain: String) {
        save(context, load(context).filterNot { it == domain })
    }

    fun normalize(raw: String): String = raw.trim().lowercase()
        .removePrefix("https://")
        .removePrefix("http://")
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore(':')
        .trim()

    /** Аргументы для byedpi: передаём файл только в режиме белого списка. */
    fun args(context: Context): List<String> {
        val settings = Settings.current()
        if (settings.hostsMode != HOSTS_ONLY) return emptyList()
        val domains = load(context)
        if (domains.isEmpty()) return emptyList()
        return listOf("-H", file(context).absolutePath)
    }

    fun summary(context: Context): String {
        val count = load(context).size
        return when (Settings.current().hostsMode) {
            HOSTS_ONLY -> if (count == 0) {
                "белый список включён, но пуст — обход работает для всех"
            } else {
                "обход только для $count доменов"
            }
            else -> "обход для всех доменов"
        }
    }

    const val HOSTS_ALL = "all"
    const val HOSTS_ONLY = "only"
}
