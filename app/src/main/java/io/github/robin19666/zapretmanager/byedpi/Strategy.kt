package io.github.robin19666.zapretmanager.byedpi

import android.content.Context
import io.github.robin19666.zapretmanager.core.LogBus
import java.io.File

/**
 * Стратегия ускорения соединения — набор аргументов byedpi (ciadpi).
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
    const val CUSTOM_FILE = "strategies-custom.list"
    const val FAKE_SNI = "www.google.com"
    const val MIN_DOWNLOADED = 5

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

    /**
     * Наборы из документации самого byedpi (README и dist/linux/byedpi.conf).
     * Автор движка рекомендует их как стартовые — независимо от ByeByeDPI.
     */
    val authorPresets: List<Strategy> = listOf(
        Strategy(
            id = "author-default",
            title = "Авторский набор byedpi",
            args = "--split 1 --disorder 3+s --mod-http=h,d --auto=torst --tlsrec 1+s",
            origin = "author",
            note = "Конфиг из dist/linux/byedpi.conf",
        ),
        Strategy(
            id = "author-disorder",
            title = "Disorder 1 + auto=torst",
            args = "--disorder 1 --auto=torst --tlsrec 1+s",
            origin = "author",
            note = "Пример из README byedpi",
        ),
        Strategy(
            id = "author-fake",
            title = "Fake -1, TTL 8",
            args = "--fake -1 --ttl 8",
            origin = "author",
            note = "Пример из README byedpi",
        ),
        Strategy(
            id = "author-oob",
            title = "OOB 1 + auto=torst",
            args = "--oob 1 --auto=torst",
            origin = "author",
            note = "OOB-байт для первого сегмента",
        ),
        Strategy(
            id = "author-split-disorder",
            title = "Split 1 + disorder 3+s",
            args = "--split 1 --disorder 3+s --mod-http=h,d",
            origin = "author",
            note = "Без TLS-модификаций — для HTTP и MTProto",
        ),
    )

    private fun componentFile(context: Context): File =
        File(context.filesDir, "components/$FILE_NAME")

    private fun customFile(context: Context): File =
        File(context.filesDir, "components/$CUSTOM_FILE")

    /**
     * Все доступные стратегии: встроенные, авторские byedpi, список ByeByeDPI и
     * свои (импортированные). Дубликаты по аргументам отбрасываются, чтобы один
     * и тот же набор не появлялся дважды.
     */
    fun all(context: Context): List<Strategy> {
        val result = ArrayList<Strategy>()
        val seen = HashSet<String>()
        fun add(list: List<Strategy>) {
            list.forEach { strategy ->
                if (seen.add(strategy.args.trim())) result += strategy
            }
        }
        add(builtin)
        add(authorPresets)
        add(loadDownloaded(context))
        add(loadImported(context))
        return result
    }

    fun byId(context: Context, id: String): Strategy? =
        all(context).firstOrNull { it.id == id }

    fun resolve(context: Context, id: String): Strategy =
        byId(context, id) ?: all(context).firstOrNull() ?: builtin.first()

    /** Человекочитаемый источник стратегии — для бейджа в списке. */
    fun originLabel(origin: String): String = when (origin) {
        "builtin" -> "встроенная"
        "author" -> "byedpi"
        "custom" -> "свои"
        else -> "ByeByeDPI"
    }

    fun counts(context: Context): Map<String, Int> {
        val list = all(context)
        return list.groupingBy { originLabel(it.origin) }.eachCount()
    }

    /**
     * Аргументы командной строки byedpi (без имени программы и без --ip/--port).
     *
     * Список стратегий приходит из сети (ByeByeDPI), поэтому аргументы,
     * меняющие адрес/порт прослушивания или уводящие процесс в daemon,
     * отбрасываются. Финальные --ip/--port всё равно добавляются в сервисе.
     */
    fun tokens(context: Context, strategyId: String, customArgs: String): List<String> {
        val raw = customArgs.trim().ifBlank { resolve(context, strategyId).args }
        val parsed = raw.replace("{sni}", FAKE_SNI)
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        val safe = parsed.filterNot { token ->
            token == "-i" || token == "--ip" || token.startsWith("--ip=") ||
                token == "-p" || token == "--port" || token.startsWith("--port=") ||
                token == "-D" || token == "--daemon" ||
                token == "-w" || token == "--pidfile"
        }
        if (safe.size != parsed.size) {
            LogBus.w("Стратегии", "Отброшены аргументы, меняющие bind/daemon: ${parsed - safe.toSet()}")
        }
        return safe
    }

    fun loadDownloaded(context: Context): List<Strategy> {
        val file = componentFile(context)
        if (!file.exists()) return emptyList()
        return runCatching { parse(file.readText(), "upstream", "s") }
            .onFailure { LogBus.w("Стратегии", "Список стратегий повреждён: ${it.message}") }
            .getOrDefault(emptyList())
            .takeIf { it.size >= MIN_DOWNLOADED }
            ?: run {
                LogBus.w("Стратегии", "Скачанный список слишком короткий — использую встроенный")
                emptyList()
            }
    }

    fun loadImported(context: Context): List<Strategy> {
        val file = customFile(context)
        if (!file.exists()) return emptyList()
        return runCatching { parse(file.readText(), "custom", "c") }
            .onFailure { LogBus.w("Стратегии", "Свой список повреждён: ${it.message}") }
            .getOrDefault(emptyList())
    }

    fun parse(text: String, origin: String = "upstream", idPrefix: String = "s"): List<Strategy> {
        val result = ArrayList<Strategy>()
        var index = 0
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            // Стратегия byedpi всегда начинается с флага: так отсекается HTML и мусор
            // при неудачном скачивании списка.
            if (!line.startsWith("-")) return@forEach
            index++
            val flags = line.split(Regex("\\s+")).filter { it.startsWith("-") }
            val label = flags.take(3).joinToString(" ").ifBlank { "набор аргументов" }
            result += Strategy(
                id = "$idPrefix$index-${line.hashCode().toUInt().toString(16).take(4)}",
                title = "$index. $label",
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
        if (parsed.size < MIN_DOWNLOADED) {
            throw IllegalStateException("В списке только ${parsed.size} стратегий — похоже, это не тот файл")
        }
        val file = componentFile(context)
        file.parentFile?.mkdirs()
        file.writeText(text)
        LogBus.i("Стратегии", "Загружено стратегий: ${parsed.size}")
        return parsed.size
    }

    /** Свои стратегии: по одной строке аргументов, как в списке ByeByeDPI. */
    fun saveImported(context: Context, text: String): Int {
        val parsed = parse(text, "custom", "c")
        if (parsed.isEmpty()) {
            throw IllegalStateException("Не найдено ни одной строки с аргументами — они должны начинаться с «-»")
        }
        val file = customFile(context)
        file.parentFile?.mkdirs()
        file.writeText(parsed.joinToString("\n") { it.args })
        LogBus.i("Стратегии", "Импортировано своих стратегий: ${parsed.size}")
        return parsed.size
    }

    fun clearImported(context: Context) {
        customFile(context).delete()
        LogBus.i("Стратегии", "Свой список стратегий очищен")
    }
}
