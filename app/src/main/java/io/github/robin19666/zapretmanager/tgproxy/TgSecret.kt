package io.github.robin19666.zapretmanager.tgproxy

import android.content.Context
import io.github.robin19666.zapretmanager.core.LogBus
import io.github.robin19666.zapretmanager.core.Settings
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Секрет MTProto-прокси: один на приложение, а не на процесс.
 *
 * Раньше секрет жил только в `SharedPreferences`, а генерировали его оба процесса
 * (`:tg` и основной) — каждый со своим кэшем настроек. После автоматического
 * перезапуска сервиса системой сервис успевал создать секрет первым, затем UI
 * с устаревшим кэшем создавал **другой** и перезаписывал настройки. В итоге
 * рантайм слушал со старым секретом, а ссылка в Telegram содержала новый:
 * прокси «работает», а все рукопожатия отклоняются.
 *
 * Теперь источник правды — файл `files/tg-secret.txt`: его читают оба процесса,
 * а создание идемпотентно (после записи значение перечитывается, поэтому
 * одновременная генерация в двух процессах сходится к одному секрету).
 */
object TgSecret {
    private const val FILE_NAME = "tg-secret.txt"
    private const val LENGTH = 32

    fun isValid(secret: String): Boolean =
        secret.length == LENGTH && secret.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }

    /** Первые 12 hex SHA-256 — ровно то, что считает Go-рантайм в `secret_fingerprint`. */
    fun fingerprint(secret: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(secret.trim().lowercase().toByteArray(Charsets.US_ASCII))
        return digest.joinToString("") { "%02x".format(it) }.take(12)
    }

    /** Секрет, с которым сейчас работает приложение (без создания нового). */
    fun peek(context: Context): String? {
        val fromFile = read(file(context))
        if (fromFile != null) return fromFile
        val fromSettings = Settings.current().tgSecret
        return fromSettings.takeIf { isValid(it) }
    }

    fun ensure(context: Context): String {
        val file = file(context)
        read(file)?.let { return publish(it) }

        val fromSettings = Settings.current().tgSecret
        if (isValid(fromSettings)) {
            write(file, fromSettings)
            return fromSettings
        }

        write(file, generate())
        // Перечитываем: если параллельно писал второй процесс, берём его значение.
        val stored = read(file) ?: generate().also { write(file, it) }
        LogBus.i("Telegram", "Сгенерирован секрет MTProto-прокси: ${fingerprint(stored)}")
        return publish(stored)
    }

    private fun publish(secret: String): String {
        if (Settings.current().tgSecret != secret) {
            Settings.update { it.copy(tgSecret = secret) }
        }
        return secret
    }

    /**
     * Принять уже существующий секрет (например, тот, с которым реально запущен
     * рантайм): Telegram у пользователя мог быть привязан именно к нему, и
     * перезапуск с «новым» секретом сломал бы рабочую ссылку.
     */
    fun adopt(context: Context, secret: String) {
        if (!isValid(secret)) return
        write(file(context), secret)
        publish(secret)
    }

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    private fun read(file: File): String? = runCatching {
        if (!file.exists()) return null
        val text = file.readText().trim().lowercase()
        if (isValid(text)) text else null
    }.getOrNull()

    private fun write(file: File, secret: String) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(secret)
            if (!tmp.renameTo(file)) {
                file.writeText(secret)
                tmp.delete()
            }
        }.onFailure { LogBus.w("Telegram", "Не удалось сохранить секрет: ${it.message}") }
    }

    private fun generate(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
