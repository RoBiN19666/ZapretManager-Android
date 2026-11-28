package io.github.robin19666.zapretmanager

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.robin19666.zapretmanager.core.LogBus
import io.github.robin19666.zapretmanager.core.LogExport
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Экспорт журнала: файл, заголовок с версией и подчистка старых файлов. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LogExportTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun fileNameHasTimestamp() {
        val name = LogExport.fileName(1_700_000_000_000L)
        assertTrue("неожиданное имя: $name", Regex("zapret-log-\\d{8}-\\d{6}\\.txt").matches(name))
    }

    @Test
    fun writeCreatesFileWithHeaderAndJournal() {
        LogBus.clear()
        LogBus.i("Тест", "строка журнала")
        val file = LogExport.write(context)
        assertTrue(file.exists())
        val text = file.readText()
        assertTrue(text.contains("Zapret Manager"))
        assertTrue(text.contains("=== ЖУРНАЛ ==="))
        assertTrue(text.contains("строка журнала"))
        assertTrue(text.contains("Тест"))
    }

    @Test
    fun writeWithEmptyJournalDoesNotFail() {
        LogBus.clear()
        val file = LogExport.write(context)
        assertTrue(file.readText().contains("(журнал пуст)"))
    }

    @Test
    fun trimOldKeepsNewestFiles() {
        val dir = LogExport.dir(context)
        dir.listFiles()?.forEach { it.delete() }
        repeat(7) { index ->
            File(dir, "zapret-log-2026010$index-120000.txt").apply {
                writeText("x")
                setLastModified(1_000L * (index + 1))
            }
        }
        val removed = LogExport.trimOld(context, keep = 5)
        assertEquals(2, removed)
        assertEquals(5, dir.listFiles()?.size)
    }
}
