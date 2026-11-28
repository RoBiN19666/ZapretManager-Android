package io.github.robin19666.zapretmanager.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class HttpException(val code: Int, message: String) : Exception(message)

/**
 * Минимальный HTTP-клиент без внешних зависимостей: редиректы, прогресс загрузки,
 * таймауты. Для APK доступна многопоточная загрузка (несколько Range-запросов):
 * на части Wi-Fi-провайдеров одно соединение к CDN GitHub искусственно замедляют,
 * а параллельные потоки дают нормальную скорость.
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
     * Скачивание файла с прогрессом. При обрыве делает до трёх попыток и продолжает
     * с места обрыва (HTTP Range), а не начинает заново — важно на медленном Wi-Fi.
     */
    fun download(
        url: String,
        target: File,
        token: String? = null,
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null,
        timeoutMs: Int = 60_000,
    ): File {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".part")
        var lastError: Throwable? = null
        for (attempt in 1..3) {
            try {
                var resumeFrom = if (temp.exists()) temp.length() else 0L
                var connection = open(url, token, timeoutMs, rangeFrom = resumeFrom.takeIf { it > 0 })
                try {
                    // Сервер мог проигнорировать Range и ответить 200 целиком.
                    // Дописывать такой ответ в конец .part нельзя: файл склеится
                    // из двух разных начал и станет мусором (для APK это ловит
                    // SHA-256, но для остальных загрузок — нет).
                    if (resumeFrom > 0 && connection.responseCode != HttpURLConnection.HTTP_PARTIAL) {
                        LogBus.w("Сеть", "Сервер не поддержал Range — скачиваю файл заново")
                        connection.disconnect()
                        temp.delete()
                        resumeFrom = 0L
                        connection = open(url, token, timeoutMs)
                    }
                    val length = connection.contentLengthLong
                    val total = if (length > 0) length + resumeFrom else 0L
                    var downloaded = resumeFrom
                    connection.inputStream.use { input ->
                        FileOutputStream(temp, resumeFrom > 0).use { output ->
                            val buffer = ByteArray(128 * 1024)
                            var lastReport = 0L
                            while (true) {
                                if (isCancelled?.invoke() == true) throw InterruptedException("Загрузка отменена")
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                val now = System.currentTimeMillis()
                                if (onProgress != null && (now - lastReport > 150 || (total > 0 && downloaded >= total))) {
                                    lastReport = now
                                    onProgress(downloaded, if (total > 0) total else downloaded)
                                }
                            }
                        }
                    }
                    if (total > 0 && temp.length() != total) {
                        throw IOException("Получено ${temp.length()} из $total байт")
                    }
                    if (target.exists()) target.delete()
                    if (!temp.renameTo(target)) throw IllegalStateException("Не удалось сохранить ${target.name}")
                    return target
                } finally {
                    connection.disconnect()
                }
            } catch (t: Throwable) {
                lastError = t
                if (isCancelled?.invoke() == true) break
                LogBus.w("Сеть", "Загрузка, попытка $attempt не удалась: ${t.message}")
                Thread.sleep(1_200L * attempt)
            }
        }
        temp.delete()
        throw lastError ?: IOException("Загрузка не удалась")
    }

    /**
     * Многопоточная загрузка большого файла (по умолчанию 4 соединения).
     * Если сервер не поддерживает Range или потоки падают — откатывается к [download].
     */
    fun downloadMulti(
        url: String,
        target: File,
        token: String? = null,
        connections: Int = 4,
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null,
        timeoutMs: Int = 60_000,
    ): File {
        val total = runCatching { contentLength(url, token) }.getOrDefault(0L)
        if (total <= 0 || connections < 2) {
            return download(url, target, token, onProgress, isCancelled, timeoutMs)
        }
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".multi")
        val downloaded = AtomicLong(0)
        // Отчёты о прогрессе приходят из нескольких потоков: без CAS время
        // последнего отчёта «прыгало» и прогресс дёргался.
        val lastReport = AtomicLong(0)
        fun report() {
            val now = System.currentTimeMillis()
            val previous = lastReport.get()
            if (onProgress != null && now - previous > 150 && lastReport.compareAndSet(previous, now)) {
                onProgress(downloaded.get(), total)
            }
        }

        try {
            RandomAccessFile(temp, "rw").use { raf -> raf.setLength(total) }
            val chunk = (total + connections - 1) / connections
            val tasks = (0 until connections).map { index ->
                Callable {
                    val start = index * chunk
                    val end = minOf(total - 1, start + chunk - 1)
                    if (start > end) return@Callable
                    var position = start
                    while (position <= end) {
                        if (isCancelled?.invoke() == true) throw InterruptedException("Загрузка отменена")
                        val connection = open(url, token, timeoutMs, rangeFrom = position, rangeTo = end)
                        try {
                            // 200 вместо 206 означает, что Range проигнорирован и в
                            // поток пойдёт весь файл: куски затрут друг друга.
                            if (connection.responseCode != HttpURLConnection.HTTP_PARTIAL) {
                                throw IOException("Сервер не поддержал Range (HTTP ${connection.responseCode})")
                            }
                            connection.inputStream.use { input ->
                                RandomAccessFile(temp, "rw").use { raf ->
                                    raf.seek(position)
                                    val buffer = ByteArray(128 * 1024)
                                    while (position <= end) {
                                        val want = minOf(buffer.size.toLong(), end - position + 1).toInt()
                                        val read = input.read(buffer, 0, want)
                                        if (read <= 0) break
                                        raf.write(buffer, 0, read)
                                        position += read
                                        downloaded.addAndGet(read.toLong())
                                        report()
                                    }
                                }
                            }
                        } finally {
                            connection.disconnect()
                        }
                    }
                }
            }
            val pool = Executors.newFixedThreadPool(connections)
            try {
                val futures = pool.invokeAll(tasks)
                futures.forEach { it.get() }
            } finally {
                pool.shutdownNow()
            }
            if (temp.length() != total) throw IOException("Размер файла не совпал")
            onProgress?.invoke(downloaded.get(), total)
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) throw IllegalStateException("Не удалось сохранить ${target.name}")
            LogBus.i("Сеть", "Многопоточная загрузка завершена: ${Versions.humanBytes(total)}")
            return target
        } catch (t: Throwable) {
            temp.delete()
            LogBus.w("Сеть", "Многопоточная загрузка не удалась (${t.message}), пробую обычную")
            return download(url, target, token, onProgress, isCancelled, timeoutMs)
        }
    }

    private fun contentLength(url: String, token: String?): Long {
        val connection = open(url, token, 15_000, rangeFrom = null)
        try {
            return connection.contentLengthLong
        } finally {
            connection.disconnect()
        }
    }

    private fun open(
        url: String,
        token: String?,
        timeoutMs: Int,
        rangeFrom: Long? = null,
        rangeTo: Long? = null,
    ): HttpURLConnection {
        var current = URL(url)
        if (!current.protocol.equals("https", ignoreCase = true)) {
            throw IllegalArgumentException("Разрешены только HTTPS-запросы: $url")
        }
        var redirects = 0
        while (true) {
            val connection = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = false
                // Без gzip: APK уже сжатый архив, а лишняя распаковка на лету замедляет загрузку.
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "*/*")
                if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
                if (rangeFrom != null) {
                    val header = if (rangeTo != null) "bytes=$rangeFrom-$rangeTo" else "bytes=$rangeFrom-"
                    setRequestProperty("Range", header)
                }
            }
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                    ?: throw HttpException(code, "Редирект без Location")
                connection.disconnect()
                val next = URL(current, location)
                if (!next.protocol.equals("https", ignoreCase = true)) {
                    throw HttpException(code, "Небезопасный редирект на ${next.protocol}")
                }
                current = next
                redirects++
                if (redirects > 8) throw HttpException(code, "Слишком много редиректов")
                continue
            }
            if (code !in 200..299) {
                val body = runCatching {
                    connection.errorStream?.readBytes()?.toString(Charsets.UTF_8) ?: ""
                }.getOrDefault("")
                val message = when (code) {
                    403 -> "GitHub ограничил запросы (403)"
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
