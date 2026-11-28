package io.github.robin19666.zapretmanager.core

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupReport(
    val settings: Boolean,
    val files: Int,
    val skipped: Int,
    val error: String = "",
) {
    val summary: String
        get() = buildString {
            if (error.isNotBlank()) {
                append("Ошибка: $error")
                return@buildString
            }
            append(if (settings) "настройки восстановлены" else "настройки не найдены")
            append(", файлов: $files")
            if (skipped > 0) append(", пропущено: $skipped")
        }
}

/**
 * Резервная копия настроек, своих стратегий, списков сайтов и сохранённого
 * результата автоподбора. Один zip-файл, который можно отправить в мессенджер
 * или сохранить в облако.
 */
object Backup {
    private const val SETTINGS_ENTRY = "settings.json"
    private const val PREFIX = "files/"
    private const val FORMAT_VERSION = 2

    // Защита от zip-bomb: архив создаёт само приложение, но импортировать
    // можно любой файл, поэтому ограничиваем количество и размер записей.
    private const val MAX_ENTRIES = 512
    private const val MAX_SETTINGS_BYTES = 1L * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 8L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 64L * 1024 * 1024

    fun exportDir(context: Context): File = File(context.filesDir, "exports").apply { mkdirs() }

    fun suggestedName(): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        return "zm-backup-$stamp.zip"
    }

    suspend fun export(context: Context): File = withContext(Dispatchers.IO) {
        val target = File(exportDir(context), suggestedName())
        try {
            ZipOutputStream(target.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(SETTINGS_ENTRY))
                zip.write(settingsToJson(Settings.current()).toString(2).toByteArray())
                zip.closeEntry()
                collectFiles(context).forEach { file ->
                    zip.putNextEntry(ZipEntry(PREFIX + file.relativeTo(context.filesDir).path.replace('\\', '/')))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } catch (t: Throwable) {
            // Не оставляем недописанный архив: его легко принять за валидный бэкап.
            target.delete()
            LogBus.e("Бэкап", "Не удалось создать архив: ${t.message}")
            throw t
        }
        LogBus.i("Бэкап", "Создан архив: ${target.name} (${Versions.humanBytes(target.length())})")
        target
    }

    suspend fun import(context: Context, uri: Uri): BackupReport = withContext(Dispatchers.IO) {
        var settingsRestored = false
        var files = 0
        var skipped = 0
        var entries = 0
        var totalBytes = 0L
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext BackupReport(false, 0, 0, "не удалось открыть файл")
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    if (entries > MAX_ENTRIES) {
                        throw IOException("Слишком много файлов в архиве (больше $MAX_ENTRIES)")
                    }
                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }
                    when {
                        entry.name == SETTINGS_ENTRY -> {
                            val text = readLimited(zip, MAX_SETTINGS_BYTES).toString(Charsets.UTF_8)
                            totalBytes += text.toByteArray(Charsets.UTF_8).size
                            checkTotal(totalBytes)
                            val json = JSONObject(text)
                            checkFormat(json)
                            Settings.update { settingsFromJson(json, it) }
                            settingsRestored = true
                        }
                        entry.name.startsWith(PREFIX) -> {
                            val relative = entry.name.removePrefix(PREFIX)
                            // Восстанавливаем только известные файлы, без выхода за пределы каталога.
                            if (relative.contains("..") || relative.startsWith("/")) {
                                skipped++
                            } else if (isKnownBackupFile(relative)) {
                                val target = File(context.filesDir, relative)
                                target.parentFile?.mkdirs()
                                val copied = target.outputStream().use { output ->
                                    copyLimited(zip, output, MAX_ENTRY_BYTES)
                                }
                                totalBytes += copied
                                checkTotal(totalBytes)
                                files++
                            } else {
                                skipped++
                            }
                        }
                        else -> skipped++
                    }
                    zip.closeEntry()
                }
            }
            LogBus.i("Бэкап", "Импорт: $settingsRestored, файлов $files, пропущено $skipped")
            if (settingsRestored) {
                LogBus.i("Бэкап", "Секрет Telegram-прокси не импортируется — примените ссылку заново")
            }
            BackupReport(settingsRestored, files, skipped)
        } catch (t: Throwable) {
            LogBus.e("Бэкап", "Импорт не удался: ${t.message}")
            BackupReport(settingsRestored, files, skipped, t.message ?: "ошибка чтения архива")
        }
    }

    private fun checkTotal(total: Long) {
        if (total > MAX_TOTAL_BYTES) {
            throw IOException("Архив слишком большой после распаковки (больше ${MAX_TOTAL_BYTES / 1024 / 1024} МБ)")
        }
    }

    /**
     * Архив от более новой версии приложения не разбираем: формат может
     * содержать поля с другим смыслом, и «тихое» частичное восстановление
     * опаснее честной ошибки. Архивы 1.x поля `format` не содержат.
     */
    internal fun checkFormat(json: JSONObject) {
        if (!json.has("format")) {
            LogBus.w("Бэкап", "Архив без версии формата — считаю его архивом 1.x")
            return
        }
        val format = json.optInt("format", FORMAT_VERSION)
        if (format > FORMAT_VERSION) {
            throw IOException("Архив создан более новой версией приложения (формат $format)")
        }
    }

    private fun readLimited(input: InputStream, max: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > max) throw IOException("Файл в архиве слишком большой")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun copyLimited(input: InputStream, output: java.io.OutputStream, max: Long): Long {
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > max) throw IOException("Файл в архиве слишком большой")
            output.write(buffer, 0, read)
        }
        return total
    }

    private fun collectFiles(context: Context): List<File> {
        val files = ArrayList<File>()
        fun add(file: File) {
            if (file.isFile && file.length() > 0) files += file
        }
        add(File(context.filesDir, "components/strategies-custom.list"))
        add(File(context.filesDir, "custom-presets.json"))
        add(File(context.filesDir, "components/meta.json"))
        add(File(context.filesDir, "components/cf-domains.txt"))
        add(File(context.filesDir, "autotune-best.txt"))
        add(File(context.filesDir, "hosts.txt"))
        add(File(context.filesDir, "network-profiles.json"))
        File(context.filesDir, "components/sites").listFiles()
            ?.filter { it.name.startsWith("proxytest_") }
            ?.forEach { add(it) }
        return files
    }

    private fun isKnownBackupFile(relative: String): Boolean = when {
        relative == "components/strategies-custom.list" -> true
        relative == "custom-presets.json" -> true
        relative == "components/meta.json" -> true
        relative == "components/cf-domains.txt" -> true
        relative == "autotune-best.txt" -> true
        relative == "hosts.txt" -> true
        relative == "network-profiles.json" -> true
        relative.startsWith("components/sites/proxytest_") &&
            (relative.endsWith(".sites") || relative.endsWith(".upstream")) -> true
        else -> false
    }

    // ---- Настройки в JSON ---------------------------------------------------
    /**
     * Секрет MTProto-прокси (`tgSecret`) в архив не попадает: файл бэкапа — обычный
     * zip, который пользователи пересылают в мессенджерах, а секрет даёт доступ
     * к их прокси. При восстановлении секрет генерируется заново, ссылку
     * `tg://proxy` нужно применить в Telegram повторно.
     */
    fun settingsToJson(s: AppSettings): JSONObject = JSONObject().apply {
        put("format", FORMAT_VERSION)
        put("strategyId", s.strategyId)
        put("customArgs", s.customArgs)
        put("autostartDpi", s.autostartDpi)
        put("autostartTg", s.autostartTg)
        put("autostartOnLaunch", s.autostartOnLaunch)
        put("autostartTgOnLaunch", s.autostartTgOnLaunch)
        put("vpnIpv6", s.vpnIpv6)
        put("vpnDns", s.vpnDns)
        put("splitMode", s.splitMode.name)
        put("splitApps", JSONArray(s.splitApps.toList()))
        put("tgPort", s.tgPort)
        put("tgRoute", s.tgRoute)
        put("tgMaskingDomain", s.tgMaskingDomain)
        put("tgAutoApply", s.tgAutoApply)
        put("checkUpdatesOnStart", s.checkUpdatesOnStart)
        put("autoUpdateComponents", s.autoUpdateComponents)
        put("themeMode", s.themeMode)
        put("powerPromptShown", s.powerPromptShown)
        put("networkAutoSwitch", s.networkAutoSwitch)
        put("wifiLossDelaySec", s.wifiLossDelaySec)
        put("accentColor", s.accentColor)
        put("backgroundTint", s.backgroundTint)
        put("amoled", s.amoled)
        put("hostsMode", s.hostsMode)
        put("watchdogEnabled", s.watchdogEnabled)
    }

    fun settingsFromJson(json: JSONObject, fallback: AppSettings): AppSettings {
        val apps = ArrayList<String>()
        json.optJSONArray("splitApps")?.let { array ->
            for (i in 0 until array.length()) array.optString(i).takeIf { it.isNotBlank() }?.let(apps::add)
        }
        fun str(key: String, def: String): String =
            if (json.has(key)) json.optString(key, def) else def
        fun bool(key: String, def: Boolean): Boolean =
            if (json.has(key)) json.optBoolean(key, def) else def
        fun int(key: String, def: Int): Int =
            if (json.has(key)) json.optInt(key, def) else def
        fun long(key: String, def: Long): Long =
            if (json.has(key)) json.optLong(key, def) else def

        return AppSettings(
            strategyId = str("strategyId", fallback.strategyId),
            customArgs = str("customArgs", fallback.customArgs),
            autostartDpi = bool("autostartDpi", fallback.autostartDpi),
            autostartTg = bool("autostartTg", fallback.autostartTg),
            vpnIpv6 = bool("vpnIpv6", fallback.vpnIpv6),
            vpnDns = str("vpnDns", fallback.vpnDns),
            splitMode = runCatching {
                SplitMode.valueOf(str("splitMode", fallback.splitMode.name))
            }.getOrDefault(fallback.splitMode),
            splitApps = if (json.has("splitApps")) apps.toSet() else fallback.splitApps,
            tgPort = int("tgPort", fallback.tgPort).coerceIn(1024, 65535),
            tgRoute = str("tgRoute", fallback.tgRoute),
            tgMaskingDomain = str("tgMaskingDomain", fallback.tgMaskingDomain),
            tgAutoApply = bool("tgAutoApply", fallback.tgAutoApply),
            // Секрет не импортируется: он уникален для устройства и не должен
            // приезжать из архива, который мог утечь. Если своего секрета нет,
            // TgConfig.ensureSecret(context) сгенерирует новый при первом запуске прокси.
            tgSecret = fallback.tgSecret,
            checkUpdatesOnStart = bool("checkUpdatesOnStart", fallback.checkUpdatesOnStart),
            autoUpdateComponents = bool("autoUpdateComponents", fallback.autoUpdateComponents),
            themeMode = str("themeMode", fallback.themeMode),
            powerPromptShown = bool("powerPromptShown", fallback.powerPromptShown),
            networkAutoSwitch = bool("networkAutoSwitch", fallback.networkAutoSwitch),
            wifiLossDelaySec = int("wifiLossDelaySec", fallback.wifiLossDelaySec).coerceIn(3, 60),
            accentColor = long("accentColor", fallback.accentColor),
            backgroundTint = long("backgroundTint", fallback.backgroundTint),
            amoled = bool("amoled", fallback.amoled),
            hostsMode = str("hostsMode", fallback.hostsMode),
            watchdogEnabled = bool("watchdogEnabled", fallback.watchdogEnabled),
            autostartOnLaunch = bool("autostartOnLaunch", fallback.autostartOnLaunch),
            autostartTgOnLaunch = bool("autostartTgOnLaunch", fallback.autostartTgOnLaunch),
        )
    }
}
