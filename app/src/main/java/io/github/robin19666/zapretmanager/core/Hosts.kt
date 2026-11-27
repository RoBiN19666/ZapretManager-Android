package io.github.robin19666.zapretmanager.core

import android.content.Context
import java.io.File

/**
 * Списки доменов для движка byedpi.
 *
 * byedpi умеет `-H, --hosts <file>` — «применять обход только к этим доменам»
 * (белый список, как `--hostlist` в zapret на ПК). Обратного режима
 * «обход для всех, кроме списка» у движка нет, поэтому чёрный список работает
 * как исключения из белого: домен из исключений не обходится, даже если он есть
 * в белом списке.
 */
object Hosts {
    const val HOSTS_ALL = "all"
    const val HOSTS_ONLY = "only"

    private const val WHITE_FILE = "hosts.txt"
    private const val BLACK_FILE = "hosts-exclude.txt"
    private const val ENGINE_FILE = "hosts-effective.txt"

    fun file(context: Context): File = File(context.filesDir, WHITE_FILE)
    fun blackFile(context: Context): File = File(context.filesDir, BLACK_FILE)

    fun load(context: Context): List<String> = read(file(context))
    fun loadBlack(context: Context): List<String> = read(blackFile(context))

    fun save(context: Context, domains: List<String>) = write(file(context), domains)
    fun saveBlack(context: Context, domains: List<String>) = write(blackFile(context), domains)

    fun add(context: Context, domain: String): Boolean {
        val normalized = normalize(domain)
        if (normalized.isBlank()) return false
        val list = load(context).toMutableList()
        if (list.contains(normalized)) return false
        list += normalized
        save(context, list)
        return true
    }

    fun addBlack(context: Context, domain: String): Boolean {
        val normalized = normalize(domain)
        if (normalized.isBlank()) return false
        val list = loadBlack(context).toMutableList()
        if (list.contains(normalized)) return false
        list += normalized
        saveBlack(context, list)
        return true
    }

    fun remove(context: Context, domain: String) = save(context, load(context) - domain)
    fun removeBlack(context: Context, domain: String) = saveBlack(context, loadBlack(context) - domain)

    /** Добавляет базовые домены (YouTube, Discord, Instagram, Telegram и т.д.). */
    fun addDefaults(context: Context): Int {
        val current = load(context).toMutableList()
        var added = 0
        defaultDomains.forEach { domain ->
            if (!current.contains(domain)) {
                current += domain
                added++
            }
        }
        if (added > 0) save(context, current)
        return added
    }

    fun normalize(raw: String): String = raw.trim().lowercase()
        .removePrefix("https://")
        .removePrefix("http://")
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore(':')
        .trim()

    /** Белый список минус исключения — именно его получает движок. */
    fun effective(context: Context): List<String> {
        val excluded = loadBlack(context).toSet()
        return load(context).filterNot { it in excluded }
    }

    /** Аргументы для byedpi: файл только в режиме белого списка. */
    fun args(context: Context): List<String> {
        if (Settings.current().hostsMode != HOSTS_ONLY) return emptyList()
        val effective = effective(context)
        if (effective.isEmpty()) {
            LogBus.w("Обход", "Белый список пуст — обход для всех доменов")
            return emptyList()
        }
        val engineFile = File(context.filesDir, ENGINE_FILE)
        engineFile.writeText(effective.joinToString("\n"))
        LogBus.i("Обход", "Белый список доменов: ${effective.size} (исключений ${loadBlack(context).size})")
        return listOf("-H", engineFile.absolutePath)
    }

    fun summary(context: Context): String {
        val white = load(context).size
        val black = loadBlack(context).size
        val effective = effective(context).size
        return when (Settings.current().hostsMode) {
            HOSTS_ONLY -> if (effective == 0) {
                "белый список пуст — обход работает для всех"
            } else {
                "обход только для $effective доменов" + if (black > 0) " (исключено $black)" else ""
            }
            else -> "обход для всех доменов" + if (white > 0 || black > 0) " · в списках: $white / $black" else ""
        }
    }

    /** Базовые домены, которые чаще всего блокируют: YouTube, соцсети, мессенджеры. */
    val defaultDomains: List<String> = listOf(
        "youtube.com", "youtu.be", "googlevideo.com", "ytimg.com", "youtube-nocookie.com",
        "ggpht.com", "googleusercontent.com", "gstatic.com", "googleapis.com",
        "instagram.com", "cdninstagram.com", "facebook.com", "fbcdn.net", "fb.com",
        "discord.com", "discord.gg", "discordapp.com", "discordapp.net",
        "telegram.org", "t.me", "web.telegram.org", "telegram.me",
        "whatsapp.com", "whatsapp.net", "signal.org",
        "twitter.com", "x.com", "twimg.com",
        "tiktok.com", "tiktokcdn.com", "muscdn.com",
        "twitch.tv", "ttvnw.net", "soundcloud.com", "spotify.com",
        "reddit.com", "redd.it", "medium.com", "patreon.com",
        "rutracker.org", "rutor.info", "nnmclub.to",
        "openai.com", "chatgpt.com", "anthropic.com",
        "github.com", "raw.githubusercontent.com",
        "vk.com", "userapi.com", "ok.ru", "mail.ru",
    )

    private fun read(target: File): List<String> = runCatching {
        if (!target.exists()) return emptyList()
        target.readLines()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.length <= 253 }
            .distinct()
    }.getOrDefault(emptyList())

    private fun write(target: File, domains: List<String>) {
        runCatching {
            val cleaned = domains.map { it.trim().lowercase() }
                .filter { it.isNotEmpty() && !it.contains(' ') && !it.startsWith("#") }
                .distinct()
            target.writeText(cleaned.joinToString("\n"))
        }
    }
}
