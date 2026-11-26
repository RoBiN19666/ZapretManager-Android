package io.github.robin19666.zapretmanager.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class HttpException(val code: Int, message: String) : Exception(message)

/**
 * Минимальный HTTP-клиент без внешних зависимостей: редиректы, прогресс
 * загрузки, таймауты. Достаточно для GitHub API и raw-файлов.
 */
object Net {
    private const val UA = "ZapretManager-Android"

    fun getText(url: String, token: String? = null, timeoutMs: Int = 20_000): String {
        val connection = open(url, token, timeoutMs)
        try {
            return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
    }

    fun getJson(url: String, token: String? = null): JSONObject = JSONObject(getText(url, token))

    fun getJsonArray(url: String, token: String? = null): JSONArray = JSONArray(getText(url, token))

    fun getBytes(url: String, token: String? = null, timeoutMs: Int = 30_000): ByteArray {
        val connection = open(url, token, timeoutMs)
        try {
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Скачивание файла с прогрессом. При обрыве частичный файл удаляется,
     * поэтому повторная попытка начинается с чистого листа.
     */
    fun download(
        url: String,
        target: File,
        token: String? = null,
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null,
        timeoutMs: Int = 60_000,
    ): File {
        val connection = open(url, token, timeoutMs)
        val total = connection.contentLengthLong
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".part")
        var downloaded = 0L
        try {
            connection.inputStream.use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var lastReport = 0L
                    while (true) {
                        if (isCancelled?.invoke() == true) throw InterruptedException("Загрузка отменена")
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (onProgress != null && (now - lastReport > 120 || (total > 0 && downloaded >= total))) {
                            lastReport = now
                            onProgress(downloaded, total)
                        }
                    }
                }
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) throw IllegalStateException("Не удалось сохранить ${target.name}")
            return target
        } catch (t: Throwable) {
            temp.delete()
            throw t
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, token: String?, timeoutMs: Int): HttpURLConnection {
        var current = URL(url)
        var redirects = 0
        while (true) {
            val connection = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "*/*")
                if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
            }
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location") ?: throw HttpException(code, "Редирект без Location")
                connection.disconnect()
                current = URL(current, location)
                redirects++
                if (redirects > 8) throw HttpException(code, "Слишком много редиректов")
                continue
            }
            if (code !in 200..299) {
                val body = runCatching { connection.errorStream?.readBytes()?.toString(Charsets.UTF_8) ?: "" }
                    .getOrDefault("")
                val message = when (code) {
                    403 -> "GitHub ограничил запросы (403). Добавьте токен в настройках."
                    404 -> "Не найдено (404)"
                    429 -> "Слишком много запросов (429)"
                    else -> "HTTP $code: ${body.take(300)}"
                }
                connection.disconnect()
                throw HttpException(code, message)
            }
            return connection
        }
    }
}
