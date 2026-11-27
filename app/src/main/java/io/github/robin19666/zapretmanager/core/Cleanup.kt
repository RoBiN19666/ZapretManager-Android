package io.github.robin19666.zapretmanager.core

import android.content.Context
import java.io.File

/**
 * Уборка мусора: скачанные APK, старые резервные копии и кэш.
 *
 * Сам установленный APK Android заменяет при обновлении — две версии одновременно
 * не хранятся. А вот скачанный файл обновления раньше оставался в кэше (≈20 МБ за раз),
 * поэтому его удаляем автоматически после установки и при следующем запуске.
 */
object Cleanup {
    private const val DOWNLOAD_DIR = "updates"
    private const val MAX_EXPORTS = 1

    fun downloadsDir(context: Context): File = File(context.cacheDir, DOWNLOAD_DIR)

    /** Удаляет скачанные APK старше [olderThanMs] (по умолчанию — сутки). */
    fun purgeDownloads(context: Context, olderThanMs: Long = 24 * 60 * 60 * 1000L): Int {
        val dir = downloadsDir(context)
        if (!dir.exists()) return 0
        val deadline = System.currentTimeMillis() - olderThanMs
        var removed = 0
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < deadline) {
                if (runCatching { file.delete() }.getOrDefault(false)) removed++
            }
        }
        if (removed > 0) LogBus.d("Хранилище", "Удалено скачанных APK: $removed")
        return removed
    }

    /** Удаляет все скачанные APK (кнопка «Очистить кэш»). */
    fun clearDownloads(context: Context): Long {
        val dir = downloadsDir(context)
        if (!dir.exists()) return 0L
        var freed = 0L
        dir.listFiles()?.forEach { file ->
            if (file.isFile) {
                val size = file.length()
                if (runCatching { file.delete() }.getOrDefault(false)) freed += size
            }
        }
        LogBus.i("Хранилище", "Очистка кэша обновлений: ${Versions.humanBytes(freed)}")
        return freed
    }

    /** Оставляет только [keep] последних резервных копий, остальные удаляет. */
    fun trimExports(context: Context, keep: Int = MAX_EXPORTS): Int {
        val dir = File(context.filesDir, "exports")
        if (!dir.exists()) return 0
        val files = dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }
            ?: return 0
        var removed = 0
        files.drop(keep).forEach { file ->
            if (runCatching { file.delete() }.getOrDefault(false)) removed++
        }
        return removed
    }

    fun downloadsSize(context: Context): Long = dirSize(downloadsDir(context))

    fun cacheSize(context: Context): Long = dirSize(context.cacheDir)

    fun dataSize(context: Context): Long = dirSize(context.filesDir)

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkBottomUp()
            .filter { it.isFile }
            .sumOf { runCatching { it.length() }.getOrDefault(0L) }
    }
}
