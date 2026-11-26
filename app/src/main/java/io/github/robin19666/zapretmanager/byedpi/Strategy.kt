package io.github.robin19666.zapretmanager.byedpi

import android.content.Context
import io.github.robin19666.zapretmanager.core.LogBus
import java.io.File

/**
 * Стратегия обхода DPI — набор аргументов byedpi (ciadpi).
 * Встроенный набор работает сразу, список из upstream заменяет его после
 * обновления компонентов.
 */
data class Strategy(
    val id: String,
    val title: String,
    val args: String,
    val origin: String = "builtin",
    val note: String = "",
)

object Strategies {
    const val FILE_NAME = "strategies.list"
    const val FAKE_SNI = "www.google.com"

    /**
     * Курируемый набор. Аргументы взяты из открытого списка ByeByeDPI
     * (romanvht/ByeByeDPI, assets/proxytest_strategies.list) и проверены на
     * совместимость с byedpi 0.16+.
     */
    val builtin: List<Strategy> = listOf(
        Strategy(
            id = "general",
            title = "Базовая (OOB)",
            args = "-o1 -a1 -r-5+se",
            note = "Стартовая стратегия: out-of-band байт и разбиение TLS-записи",
        ),
        Strategy(
            id = "oob",
            title = "OOB + split",
            args = "-d1 -d3:5:3+s -r1+s -a1",
            note = "Смещение первого сегмента и разбиение записи",
        ),
        Strategy(
            id = "split",
            title = "Множественное разбиение",
            args = "-q2 -s4 -s3+s -r3+s -s5+s -s6+s -s7+s -s9+s -Mh,d,r -a1",
            note = "Несколько точек разбиения и перемешивание регистра",
        ),
        Strategy(
            id = "multi",
            title = "Многоступенчатая",
            args = "-o1 -a1 -At,r,s,c -s1 -s5+s -s10+s -s15+s -s20+s -r1+s -a1 -At,r,s,c -s1 -s5+s -s10+s -s15+s -s20+s -a1",
            note = "Повторяющиеся наборы для упорных провайдеров",
        ),
        Strategy(
            id = "fake-tls",
            title = "Fake TLS + split",
            args = "-n {sni} -Qr -s1+sm -f-204 -a1 -At,r,s,c -d1 -s3+s -s5+s -a1 -At,r,s,c -o2 -f-43 -a1 -At,r,s,c -r6 -Mh -s1+s -s3+sm -a1",
            note = "Поддельный ClientHello с указанным SNI",
        ),
        Strategy(
            id = "tls-off",
            title = "Fake TLS (без Qr)",
            args = "-n {sni} -Qr -f-205 -a1 -At,r,s,c -s1:3:2+sm -a1 -At,r,s,c -s5:8:2+sm -a1 -At,r,s,c -d3 -q7 -r6 -Mh -f-43 -a1",
            note = "Вариант для сетей, режущих первый пакет",
        ),
        Strategy(
            id = "long-split",
            title = "Длинное разбиение",
            args = "-d1+s -s50+s -a1 -At,r,s,c -f20 -r2+s -a1 -At,r,s,c -d2 -s1+s -s5+s -s10+s -s15+s -s25+s -s35+s -s50+s -s60+s -a1",
            note = "Для медленных и «рваных» соединений",
        ),
        Strategy(
            id = "disorder",
            title = "Disorder + fake",
            args = "-o1 -a1 -At,r,s,c -f-1 -a1 -At,r,s,c -d1+sm -a1 -At,r,s,c -n {sni} -Qr -f1 -d1+sm -s2+sm -S -a1",
            note = "Отправка сегментов в обратном порядке",
        ),
    )

    private fun componentFile(context: Context): File =
        File(context.filesDir, "components/$FILE_NAME")

    fun all(context: Context): List<Strategy> {
        val downloaded = loadDownloaded(context)
        return if (downloaded.isEmpty()) builtin else downloaded
    }

    fun byId(context: Context, id: String): Strategy? =
        all(context).firstOrNull { it.id == id }

    fun resolve(context: Context, id: String): Strategy =
        byId(context, id) ?: all(context).firstOrNull() ?: builtin.first()

    /** Аргументы командной строки byedpi (без имени программы и без --ip/--port). */
    fun tokens(context: Context, strategyId: String, customArgs: String): List<String> {
        val raw = customArgs.trim().ifBlank { resolve(context, strategyId).args }
        return raw.replace("{sni}", FAKE_SNI)
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
    }

    fun loadDownloaded(context: Context): List<Strategy> {
        val file = componentFile(context)
        if (!file.exists()) return emptyList()
        return runCatching { parse(file.readText(), "upstream") }
            .onFailure { LogBus.w("Стратегии", "Список стратегий повреждён: ${it.message}") }
            .getOrDefault(emptyList())
    }

    fun parse(text: String, origin: String = "upstream"): List<Strategy> {
        val result = ArrayList<Strategy>()
        var index = 0
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            index++
            result += Strategy(
                id = "s$index",
                title = "Стратегия $index",
                args = line,
                origin = origin,
                note = summarize(line),
            )
        }
        return result
    }

    private fun summarize(args: String): String {
        val flags = args.split(Regex("\\s+")).filter { it.startsWith("-") }
        return flags.take(4).joinToString(" ") + if (flags.size > 4) " …" else ""
    }

    fun saveDownloaded(context: Context, text: String): Int {
        val parsed = parse(text)
        if (parsed.isEmpty()) throw IllegalStateException("В списке нет ни одной стратегии")
        val file = componentFile(context)
        file.parentFile?.mkdirs()
        file.writeText(text)
        LogBus.i("Стратегии", "Загружено стратегий: ${parsed.size}")
        return parsed.size
    }
}
