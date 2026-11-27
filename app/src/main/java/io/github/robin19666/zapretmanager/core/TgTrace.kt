package io.github.robin19666.zapretmanager.core

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Пошаговая трассировка запуска Telegram-прокси.
 *
 * Процесс `:tg` может умереть нативно (без Java-исключения) — тогда
 * единственный след остаётся в этом файле. Пишем на каждом этапе: загрузка
 * библиотеки, проверка порта, пул доменов, попытки запуска, статус.
 */
object TgTrace {
    private const val FILE_NAME = "tg-trace.txt"
    private const val MAX_LINES = 200
    private val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    @Synchronized
    fun write(context: Context, step: String) {
        runCatching {
            val target = file(context)
            val line = "${stamp.format(Date())} $step\n"
            val existing = if (target.exists()) target.readLines().takeLast(MAX_LINES).joinToString("\n") else ""
            target.writeText(if (existing.isBlank()) line else "$existing\n$line")
        }
    }

    fun tail(context: Context, lines: Int = 6): String {
        val target = file(context)
        if (!target.exists()) return ""
        return runCatching { target.readLines().takeLast(lines).joinToString(" | ") }.getOrDefault("")
    }
}
