package io.github.robin19666.zapretmanager.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.core.content.FileProvider
import io.github.robin19666.zapretmanager.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class UpdateInfo(
    val release: Release,
    val asset: ReleaseAsset,
    val version: String,
    val notes: String,
)

/**
 * Самообновление менеджера: релизы нашего репозитория.
 * Проверяются версия, SHA-256 (если опубликован), имя пакета и подпись APK.
 */
class ApkUpdater(private val context: Context) {

    private val token: String? get() = Settings.current().let { if (it.githubToken.isBlank()) null else it.githubToken }

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val release = try {
            GitHub.latestRelease(BuildConfig.SELF_REPO, token)
        } catch (e: HttpException) {
            throw e
        }
        if (release == null) {
            if (token == null) {
                throw IllegalStateException(
                    "Релизы недоступны без токена — возможно, репозиторий приватный. " +
                        "Добавьте токен GitHub в настройках."
                )
            }
            return@withContext null
        }
        val version = release.version
        if (!Versions.isNewer(version, BuildConfig.VERSION_NAME)) return@withContext null
        val asset = pickAsset(release) ?: run {
            LogBus.w("Обновление", "В релизе ${release.tag} нет APK для этого устройства")
            null
        } ?: return@withContext null
        UpdateInfo(release, asset, version, release.body)
    }

    private fun pickAsset(release: Release): ReleaseAsset? {
        val abis = Build.SUPPORTED_ABIS.toList()
        val apks = release.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        for (abi in abis) {
            apks.firstOrNull { it.name.contains(abi, ignoreCase = true) }?.let { return it }
        }
        apks.firstOrNull { it.name.contains("universal", ignoreCase = true) }?.let { return it }
        apks.firstOrNull { it.name.contains("arm64", ignoreCase = true) }?.let { return it }
        return apks.firstOrNull()
    }

    fun downloadDir(): File = File(context.cacheDir, "updates").apply { mkdirs() }

    suspend fun download(
        info: UpdateInfo,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val target = File(downloadDir(), info.asset.name)
        LogBus.i("Обновление", "Скачивание ${info.asset.name} (${Versions.humanBytes(info.asset.size)})")
        Net.download(info.asset.url, target, token, onProgress = { d, t -> onProgress(d, t) })
        verifyChecksum(info, target)
        verifyPackage(target, info)
        target
    }

    private fun verifyChecksum(info: UpdateInfo, apk: File) {
        val shaAsset = info.release.assets.firstOrNull { it.name.equals("${info.asset.name}.sha256", true) }
            ?: info.release.assets.firstOrNull { it.name.endsWith(".sha256", true) }
        if (shaAsset == null) {
            LogBus.w("Обновление", "SHA-256 в релизе не опубликован — проверяю только подпись")
            return
        }
        val expected = Net.getText(shaAsset.url, token).trim().substringBefore(' ').lowercase()
        val actual = Versions.sha256(apk).lowercase()
        if (expected.isNotBlank() && expected != actual) {
            apk.delete()
            throw IllegalStateException("Контрольная сумма не совпала: ожидалось $expected, получено $actual")
        }
        LogBus.i("Обновление", "SHA-256 совпал")
    }

    @Suppress("DEPRECATION")
    private fun verifyPackage(apk: File, info: UpdateInfo) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: throw IllegalStateException("Скачанный файл не является APK")
        val archivePackage = archive.packageName
        val installedPackage = context.packageName
        val expectedPackage = if (installedPackage.endsWith(".debug")) {
            installedPackage.removeSuffix(".debug")
        } else {
            installedPackage
        }
        if (archivePackage != installedPackage && archivePackage != expectedPackage) {
            apk.delete()
            throw IllegalStateException("APK относится к другому приложению: $archivePackage")
        }
        val archiveSigners = signaturesOf(archive)
        val installedSigners = signaturesOf(
            pm.getPackageInfo(installedPackage, flags)
        )
        if (archiveSigners.isNotEmpty() && installedSigners.isNotEmpty() &&
            archiveSigners.intersect(installedSigners).isEmpty()
        ) {
            apk.delete()
            throw IllegalStateException("Подпись APK не совпадает с установленным приложением")
        }
        val installedVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(installedPackage, 0).longVersionCode
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(installedPackage, 0).versionCode.toLong()
        }
        val archiveVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archive.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            archive.versionCode.toLong()
        }
        if (archiveVersionCode <= installedVersionCode) {
            apk.delete()
            throw IllegalStateException("Версия в APK не выше установленной (versionCode $archiveVersionCode)")
        }
        LogBus.i("Обновление", "APK проверен: $archivePackage, versionCode $archiveVersionCode")
    }

    @Suppress("DEPRECATION")
    private fun signaturesOf(info: android.content.pm.PackageInfo): Set<String> {
        val certs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return emptySet()
            if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        } else {
            info.signatures
        } ?: return emptySet()
        return certs.map { Versions.sha256(it.toByteArray()) }.toSet()
    }

    fun canInstall(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.packageManager.canRequestPackageInstalls()
    } else {
        true
    }

    fun openInstallPermission(): Intent =
        Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Установка через PackageInstaller — системный диалог подтверждения остаётся за пользователем. */
    suspend fun install(apk: File): Boolean = withContext(Dispatchers.IO) {
        if (!canInstall()) {
            LogBus.w("Обновление", "Нет разрешения на установку неизвестных приложений")
            return@withContext false
        }
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val intent = Intent(context, InstallResultReceiver::class.java)
                val pending = android.app.PendingIntent.getBroadcast(
                    context, sessionId, intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
                )
                session.commit(pending.intentSender)
            }
            LogBus.i("Обновление", "Системный установщик запущен")
            true
        } catch (t: Throwable) {
            LogBus.e("Обновление", "Не удалось запустить установку: ${t.message}")
            false
        }
    }

    /** Резервный путь, если PackageInstaller недоступен. */
    fun installViaIntent(apk: File): Boolean {
        return runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrElse {
            LogBus.e("Обновление", "Не удалось открыть установщик: ${it.message}")
            false
        }
    }
}
