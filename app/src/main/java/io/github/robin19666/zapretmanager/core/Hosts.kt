package io.github.robin19666.zapretmanager.core

import android.content.Context
import java.io.File

/**
 * Белый список доменов для движка byedpi.
 *
 * byedpi умеет `-H, --hosts <file>` — «применять ускорение только к этим доменам»
 * (аналог `--hostlist` в zapret на ПК).
 */
object Hosts {
    const val HOSTS_ALL = "all"
    const val HOSTS_ONLY = "only"

    private const val WHITE_FILE = "hosts.txt"
    private const val ENGINE_FILE = "hosts-effective.txt"

    fun file(context: Context): File = File(context.filesDir, WHITE_FILE)

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
        save(context, load(context) - domain)
    }

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

    /** Аргументы для byedpi: файл только в режиме белого списка. */
    fun args(context: Context): List<String> {
        if (Settings.current().hostsMode != HOSTS_ONLY) return emptyList()
        val domains = load(context)
        if (domains.isEmpty()) {
            LogBus.w("Ускорение", "Белый список пуст — ускорение для всех доменов")
            return emptyList()
        }
        val engineFile = File(context.filesDir, ENGINE_FILE)
        engineFile.writeText(domains.joinToString("\n"))
        LogBus.i("Ускорение", "Белый список доменов: ${domains.size}")
        return listOf("-H", engineFile.absolutePath)
    }

    fun summary(context: Context): String {
        val count = load(context).size
        return when (Settings.current().hostsMode) {
            HOSTS_ONLY -> if (count == 0) {
                "белый список пуст — ускорение работает для всех"
            } else {
                "ускорение только для $count доменов"
            }
            else -> if (count == 0) "ускорение для всех доменов" else "ускорение для всех · в списке $count"
        }
    }

    /** Базовые домены, которые чаще всего требуют ускорения. */
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
}
