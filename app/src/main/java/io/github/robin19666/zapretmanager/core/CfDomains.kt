package io.github.robin19666.zapretmanager.core

import io.github.robin19666.zapretmanager.tgproxy.TgNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Пул доменов Cloudflare Proxy для Telegram-прокси.
 *
 * Встроенный список в Go-рантайме быстро устаревает: домены блокируются.
 * Upstream обновляет их из Flowseal раз в сутки — повторяем это поведение,
 * иначе прокси слушает локально, но не может установить исходящий маршрут.
 */
object CfDomains {
    private const val URL =
        "https://raw.githubusercontent.com/Flowseal/tg-ws-proxy/main/.github/cfproxy-domains.txt"
    private const val TTL_MS = 24 * 60 * 60 * 1000L
    private const val META_KEY = "cf_domains_checked_at"
    private const val MIN_DOMAINS = 3

    fun file(context: android.content.Context): File =
        File(context.filesDir, "components/cf-domains.txt")

    fun cached(context: android.content.Context): List<String> = runCatching {
        file(context).readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    }.getOrDefault(emptyList())

    /**
     * Применяет сохранённый список к рантайму и, если он устарел или пуст,
     * скачивает свежий. Возвращает количество доменов, доступных рантайму.
     */
    suspend fun ensureFresh(context: android.content.Context, force: Boolean = false): Int =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val meta = readMeta(app)
            val last = meta.optLong(META_KEY, 0L)
            val cached = cached(app)
            val stale = force || cached.size < MIN_DOMAINS ||
                System.currentTimeMillis() - last > TTL_MS

            if (stale) {
                val downloaded = runCatching { download(app) }.getOrNull()
                if (downloaded != null && downloaded.size >= MIN_DOMAINS) {
                    meta.put(META_KEY, System.currentTimeMillis())
                    writeMeta(app, meta)
                    LogBus.i("Telegram", "Пул CF-доменов обновлён: ${downloaded.size} шт.")
                    return@withContext downloaded.size
                }
                LogBus.w("Telegram", "Не удалось обновить пул CF-доменов, использую сохранённый")
            }

            if (cached.size >= MIN_DOMAINS) {
                TgNative.applyCachedDomains(cached)
                return@withContext cached.size
            }
            LogBus.w("Telegram", "Пул CF-доменов пуст — останется встроенный список рантайма")
            0
        }

    private fun download(context: android.content.Context): List<String> {
        val text = Net.getText("$URL?t=${System.currentTimeMillis()}")
        val domains = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .distinct()
            .toList()
        if (domains.size < MIN_DOMAINS) {
            throw IllegalStateException("Слишком короткий список доменов: ${domains.size}")
        }
        val target = file(context)
        target.parentFile?.mkdirs()
        target.writeText(domains.joinToString("\n"))
        TgNative.applyCachedDomains(domains)
        return domains
    }

    private fun metaFile(context: android.content.Context) =
        File(context.filesDir, "components/meta.json")

    private fun readMeta(context: android.content.Context): JSONObject =
        runCatching { JSONObject(metaFile(context).readText()) }.getOrDefault(JSONObject())

    private fun writeMeta(context: android.content.Context, json: JSONObject) {
        runCatching {
            metaFile(context).parentFile?.mkdirs()
            metaFile(context).writeText(json.toString(2))
        }
    }
}
