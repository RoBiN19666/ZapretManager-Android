package io.github.robin19666.zapretmanager.core

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ловит необработанные исключения (в том числе в процессах :native и :tg) и
 * складывает стек в файл. Без этого падение нативного сервиса видно только как
 * «не ответил за 30 секунд».
 */
object CrashLog {
    private const val FILE_NAME = "crash-last.txt"
    private const val MAX_BYTES = 64 * 1024

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? = runCatching {
        file(context).takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    fun summary(context: Context): String? {
        val text = read(context) ?: return null
        return text.lineSequence()
            .firstOrNull { it.startsWith("exception:") }
            ?.removePrefix("exception:")
            ?.trim()
            ?: text.lineSequence().firstOrNull()?.take(120)
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val text = buildString {
            appendLine("time: $stamp")
            appendLine("process: ${currentProcessName(context)}")
            appendLine("thread: ${thread.name}")
            appendLine("exception: ${error.javaClass.name}: ${error.message.orEmpty()}")
            appendLine()
            appendLine(stack.take(MAX_BYTES))
        }
        val target = file(context)
        target.parentFile?.mkdirs()
        target.writeText(text)
        LogBus.e("Сбой", "Записан отчёт о падении: ${error.javaClass.simpleName}")
    }

    private fun currentProcessName(context: Context): String {
        return runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                android.app.Application.getProcessName()
            } else {
                val pid = android.os.Process.myPid()
                File("/proc/$pid/cmdline").readText().trim('\u0000', '\n')
            }
        }.getOrDefault("unknown")
    }
}
