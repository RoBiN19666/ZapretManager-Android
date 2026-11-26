package io.github.robin19666.zapretmanager.core

import java.io.File
import java.security.MessageDigest
import java.util.Locale

object Versions {
    /** Сравнение тегов вида v1.2.3 / 1.2 / 1.11.10 с учётом числовых компонентов. */
    fun compare(a: String, b: String): Int {
        val pa = parse(a)
        val pb = parse(b)
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return if (x > y) 1 else -1
        }
        return 0
    }

    fun isNewer(remote: String, local: String): Boolean = compare(remote, local) > 0

    private fun parse(v: String): List<Long> {
        val clean = v.trim().removePrefix("v").removePrefix("V")
        val main = clean.substringBefore('-').substringBefore('+')
        return main.split('.').map { part ->
            part.takeWhile { it.isDigit() }.toLongOrNull() ?: 0L
        }
    }

    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { String.format(Locale.US, "%02x", it) }
    }

    fun sha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { String.format(Locale.US, "%02x", it) }
    }

    fun humanBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes Б"
        val units = arrayOf("КБ", "МБ", "ГБ")
        var value = bytes.toDouble()
        var unit = -1
        while (value >= 1024 && unit < units.size - 1) {
            value /= 1024
            unit++
        }
        return String.format(Locale.US, "%.1f %s", value, units[unit])
    }

    fun humanDuration(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}

/** Размер и подпись сборки — используются в «О программе» и при самообновлении. */
object BuildInfo {
    const val MIN_UPDATE_INTERVAL_MS = 60_000L
}
