package io.github.robin19666.zapretmanager.core

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import io.github.robin19666.zapretmanager.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Экспорт журнала в файл и отправка его через системное «Поделиться».
 *
 * Файл кладём в `files/logs` (а не в `files/exports`): туда пишутся резервные
 * копии, и [Cleanup] оставляет от них только последнюю — журналы бы удалялись
 * вместе с ними. Старые журналы подчищаем сами, держим последние [KEEP] штук.
 */
object LogExport {
    private const val KEEP = 5

    fun dir(context: Context): File = File(context.filesDir, "logs").apply { mkdirs() }

    fun fileName(now: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(now))
        return "zapret-log-$stamp.txt"
    }

    /** Заголовок с версией и устройством: по нему видно, где снят журнал. */
    fun header(context: Context, now: Long = System.currentTimeMillis()): String = buildString {
        appendLine("Zapret Manager ${BuildConfig.APP_VERSION} (${BuildConfig.VERSION_CODE})")
        appendLine("Устройство: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        appendLine("Снято: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(now))}")
        appendLine()
    }

    /**
     * Пишет журнал в файл и возвращает его. Ошибки ввода-вывода пробрасываются:
     * вызывающий код показывает сообщение пользователю.
     */
    fun write(context: Context, now: Long = System.currentTimeMillis()): File {
        val file = File(dir(context), fileName(now))
        val body = LogBus.exportText().ifBlank { "(журнал пуст)" }
        file.writeText(header(context, now) + "=== ЖУРНАЛ ===\n" + body + "\n")
        trimOld(context)
        return file
    }

    /** Оставляет последние [KEEP] файлов журнала. */
    fun trimOld(context: Context, keep: Int = KEEP): Int {
        val files = dir(context).listFiles { f -> f.isFile }?.sortedByDescending { it.lastModified() } ?: return 0
        var removed = 0
        files.drop(keep).forEach {
            if (it.delete()) removed++
        }
        return removed
    }

    fun shareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
