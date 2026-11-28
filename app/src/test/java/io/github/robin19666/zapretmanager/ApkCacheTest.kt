package io.github.robin19666.zapretmanager

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.robin19666.zapretmanager.core.ApkUpdater
import io.github.robin19666.zapretmanager.core.EngineMemory
import io.github.robin19666.zapretmanager.core.Release
import io.github.robin19666.zapretmanager.core.ReleaseAsset
import io.github.robin19666.zapretmanager.core.UpdateInfo
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Скачанный APK не должен теряться, если установку отменили: по нему кнопка
 * предлагает «Установить» вместо повторной загрузки.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApkCacheTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun info(name: String, size: Long): UpdateInfo {
        val asset = ReleaseAsset(name, "https://example.invalid/$name", size)
        val release = Release(
            tag = "v9.9.9",
            name = "test",
            body = "",
            htmlUrl = "",
            publishedAt = "",
            prerelease = false,
            assets = listOf(asset),
        )
        return UpdateInfo(release, asset, "9.9.9", "")
    }

    @Test
    fun downloadedReturnsExistingApk() {
        val updater = ApkUpdater(context)
        val payload = ByteArray(64) { 1 }
        val file = File(updater.downloadDir(), "app-test.apk")
        file.writeBytes(payload)

        val found = updater.downloaded(info("app-test.apk", payload.size.toLong()))
        assertNotNull(found)
        assertEquals(file.absolutePath, found?.absolutePath)
    }

    @Test
    fun downloadedIgnoresSizeMismatch() {
        val updater = ApkUpdater(context)
        val file = File(updater.downloadDir(), "app-mismatch.apk")
        file.writeBytes(ByteArray(10))
        assertNull(updater.downloaded(info("app-mismatch.apk", 999)))
    }

    @Test
    fun downloadedRejectsUnsafeName() {
        val updater = ApkUpdater(context)
        assertNull(updater.downloaded(info("../escape.apk", 0)))
    }

    @Test
    fun startedAtSurvivesProcessRestart() {
        EngineMemory.clearStartedAt(context, EngineMemory.KIND_TG)
        assertEquals(0L, EngineMemory.startedAt(context, EngineMemory.KIND_TG))

        val started = 1_700_000_000_000L
        EngineMemory.setStartedAt(context, EngineMemory.KIND_TG, started)
        assertEquals(started, EngineMemory.startedAt(context, EngineMemory.KIND_TG))

        EngineMemory.clearStartedAt(context, EngineMemory.KIND_TG)
        assertEquals(0L, EngineMemory.startedAt(context, EngineMemory.KIND_TG))
    }
}
