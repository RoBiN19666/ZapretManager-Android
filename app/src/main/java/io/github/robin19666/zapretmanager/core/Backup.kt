package io.github.robin19666.zapretmanager.core

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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

    fun exportDir(context: Context): File = File(context.filesDir, "exports").apply { mkdirs() }

    fun suggestedName(): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        return "zm-backup-$stamp.zip"
    }

    suspend fun export(context: Context): File = withContext(Dispatchers.IO) {
        val target = File(exportDir(context), suggestedName())
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
        LogBus.i("Бэкап", "Создан архив: ${target.name} (${Versions.humanBytes(target.length())})")
        target
    }

    suspend fun import(context: Context, uri: Uri): BackupReport = withContext(Dispatchers.IO) {
        var settingsRestored = false
        var files = 0
        var skipped = 0
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext BackupReport(false, 0, 0, "не удалось открыть файл")
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when {
                        entry.name == SETTINGS_ENTRY -> {
                            val text = zip.readBytes().toString(Charsets.UTF_8)
                            Settings.update { settingsFromJson(JSONObject(text), it) }
                            settingsRestored = true
                        }
                        entry.name.startsWith(PREFIX) && !entry.isDirectory -> {
                            val relative = entry.name.removePrefix(PREFIX)
                            // Восстанавливаем только известные файлы, без выхода за пределы каталога.
                            if (relative.contains("..") || relative.startsWith("/")) {
                                skipped++
                            } else if (isKnownBackupFile(relative)) {
                                val target = File(context.filesDir, relative)
                                target.parentFile?.mkdirs()
                                target.outputStream().use { zip.copyTo(it) }
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
            BackupReport(settingsRestored, files, skipped)
        } catch (t: Throwable) {
            LogBus.e("Бэкап", "Импорт не удался: ${t.message}")
            BackupReport(settingsRestored, files, skipped, t.message ?: "ошибка чтения архива")
        }
    }

    private fun collectFiles(context: Context): List<File> {
        val files = ArrayList<File>()
        fun add(file: File) {
            if (file.isFile && file.length() > 0) files += file
        }
        add(File(context.filesDir, "components/strategies-custom.list"))
        add(File(context.filesDir, "components/meta.json"))
        add(File(context.filesDir, "components/cf-domains.txt"))
        add(File(context.filesDir, "autotune-best.txt"))
        File(context.filesDir, "components/sites").listFiles()
            ?.filter { it.name.startsWith("proxytest_") }
            ?.forEach { add(it) }
        return files
    }

    private fun isKnownBackupFile(relative: String): Boolean = when {
        relative == "components/strategies-custom.list" -> true
        relative == "components/meta.json" -> true
        relative == "components/cf-domains.txt" -> true
        relative == "autotune-best.txt" -> true
        relative.startsWith("components/sites/proxytest_") &&
            (relative.endsWith(".sites") || relative.endsWith(".upstream")) -> true
        else -> false
    }

    // ---- Настройки в JSON ---------------------------------------------------
    fun settingsToJson(s: AppSettings): JSONObject = JSONObject().apply {
        put("strategyId", s.strategyId)
        put("customArgs", s.customArgs)
        put("autostartDpi", s.autostartDpi)
        put("autostartTg", s.autostartTg)
        put("vpnIpv6", s.vpnIpv6)
        put("vpnDns", s.vpnDns)
        put("splitMode", s.splitMode.name)
        put("splitApps", JSONArray(s.splitApps.toList()))
        put("tgPort", s.tgPort)
        put("tgFrontend", s.tgFrontend)
        put("tgRoute", s.tgRoute)
        put("tgMaskingDomain", s.tgMaskingDomain)
        put("tgAutoApply", s.tgAutoApply)
        put("tgSecret", s.tgSecret)
        put("checkUpdatesOnStart", s.checkUpdatesOnStart)
        put("autoUpdateComponents", s.autoUpdateComponents)
        put("logVerbose", s.logVerbose)
        put("notifyOnStart", s.notifyOnStart)
        put("themeMode", s.themeMode)
        put("powerPromptShown", s.powerPromptShown)
    }

    fun settingsFromJson(json: JSONObject, fallback: AppSettings): AppSettings {
        val apps = ArrayList<String>()
        json.optJSONArray("splitApps")?.let { array ->
            for (i in 0 until array.length()) array.optString(i).takeIf { it.isNotBlank() }?.let(apps::add)
        }
        return AppSettings(
            strategyId = json.optString("strategyId", fallback.strategyId),
            customArgs = json.optString("customArgs", fallback.customArgs),
            autostartDpi = json.optBoolean("autostartDpi", fallback.autostartDpi),
            autostartTg = json.optBoolean("autostartTg", fallback.autostartTg),
            vpnIpv6 = json.optBoolean("vpnIpv6", fallback.vpnIpv6),
            vpnDns = json.optString("vpnDns", fallback.vpnDns),
            splitMode = runCatching { SplitMode.valueOf(json.optString("splitMode", fallback.splitMode.name)) }
                .getOrDefault(fallback.splitMode),
            splitApps = if (json.has("splitApps")) apps.toSet() else fallback.splitApps,
            tgPort = json.optInt("tgPort", fallback.tgPort),
            tgFrontend = json.optString("tgFrontend", fallback.tgFrontend),
            tgRoute = json.optString("tgRoute", fallback.tgRoute),
            tgMaskingDomain = json.optString("tgMaskingDomain", fallback.tgMaskingDomain),
            tgAutoApply = json.optBoolean("tgAutoApply", fallback.tgAutoApply),
            tgSecret = json.optString("tgSecret", fallback.tgSecret),
            checkUpdatesOnStart = json.optBoolean("checkUpdatesOnStart", fallback.checkUpdatesOnStart),
            autoUpdateComponents = json.optBoolean("autoUpdateComponents", fallback.autoUpdateComponents),
            logVerbose = json.optBoolean("logVerbose", fallback.logVerbose),
            notifyOnStart = json.optBoolean("notifyOnStart", fallback.notifyOnStart),
            themeMode = json.optString("themeMode", fallback.themeMode),
            powerPromptShown = json.optBoolean("powerPromptShown", fallback.powerPromptShown),
        )
    }
}
