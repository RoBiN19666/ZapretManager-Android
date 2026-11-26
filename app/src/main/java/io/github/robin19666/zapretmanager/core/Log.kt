package io.github.robin19666.zapretmanager.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LogLine(
    val time: Long,
    val tag: String,
    val message: String,
    val level: LogLevel,
)

/**
 * Общая шина журнала: пишут движки, сервисы и апдейтер, читает интерфейс.
 * Хранится ограниченное кольцо строк, чтобы не течь памятью.
 */
object LogBus {
    private const val CAPACITY = 800
    private val _lines = MutableStateFlow<List<LogLine>>(emptyList())
    val lines: StateFlow<List<LogLine>> = _lines.asStateFlow()

    private val stamp = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    fun add(tag: String, message: String, level: LogLevel = LogLevel.INFO) {
        val line = LogLine(System.currentTimeMillis(), tag, message.trim(), level)
        val next = ArrayList<LogLine>(_lines.value.size + 1)
        next.addAll(_lines.value)
        next.add(line)
        _lines.value = if (next.size > CAPACITY) next.subList(next.size - CAPACITY, next.size).toList() else next
    }

    fun d(tag: String, message: String) = add(tag, message, LogLevel.DEBUG)
    fun i(tag: String, message: String) = add(tag, message, LogLevel.INFO)
    fun w(tag: String, message: String) = add(tag, message, LogLevel.WARN)
    fun e(tag: String, message: String) = add(tag, message, LogLevel.ERROR)

    fun clear() {
        _lines.value = emptyList()
    }

    fun exportText(): String = _lines.value.joinToString("\n") { line ->
        "${stamp.format(Date(line.time))} ${line.level.name.first()} ${line.tag}: ${line.message}"
    }
}
