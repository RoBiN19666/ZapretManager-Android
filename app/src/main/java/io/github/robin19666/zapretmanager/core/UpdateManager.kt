package io.github.robin19666.zapretmanager.core

import android.content.Context
import io.github.robin19666.zapretmanager.BuildConfig
import io.github.robin19666.zapretmanager.byedpi.Strategies
import io.github.robin19666.zapretmanager.tgproxy.TgNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

enum class ComponentStatus { UNKNOWN, CHECKING, UP_TO_DATE, UPDATE_AVAILABLE, UPDATED, ERROR }

data class ComponentInfo(
    val id: String,
    val title: String,
    val description: String,
    val localVersion: String = "—",
    val remoteVersion: String? = null,
    val status: ComponentStatus = ComponentStatus.UNKNOWN,
    val note: String = "",
    val builtin: Boolean = false,
)

data class UpdateUiState(
    val components: List<ComponentInfo> = emptyList(),
    val appUpdate: UpdateInfo? = null,
    val checking: Boolean = false,
    val updating: Boolean = false,
    val progress: Float = -1f,
    val progressText: String = "",
    val message: String = "",
    val lastCheck: Long = 0L,
)

/**
 * Автообновление компонентов из оригинальных репозиториев:
 *  • стратегии byedpi и списки сайтов — romanvht/ByeByeDPI;
 *  • пул Cloudflare-доменов — Flowseal/tg-ws-proxy;
 *  • сам APK менеджера — RoBiN19666/ZapretManager-Android.
 */
object UpdateManager {
    private const val STRATEGIES_PATH = "app/src/main/assets/proxytest_strategies.list"
    private const val SITES_DIR = "app/src/main/assets"
    private const val SITES_PATH = "app/src/main/assets"
    private const val CF_DOMAINS_URL =
        "https://raw.githubusercontent.com/Flowseal/tg-ws-proxy/main/.github/cfproxy-domains.txt"

    private val sites = listOf(
        "proxytest_youtube.sites" to "Сайты YouTube",
        "proxytest_googlevideo.sites" to "Сайты Googlevideo",
        "proxytest_discord.sites" to "Сайты Discord",
        "proxytest_telegram.sites" to "Сайты Telegram",
        "proxytest_general.sites" to "Общий список сайтов",
    )

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private fun metaFile(context: Context) = File(context.filesDir, "components/meta.json")
    private fun componentDir(context: Context) = File(context.filesDir, "components").apply { mkdirs() }

    private fun readMeta(context: Context): JSONObject {
        val file = metaFile(context)
        return runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())
    }

    private fun writeMeta(context: Context, json: JSONObject) {
        runCatching {
            metaFile(context).parentFile?.mkdirs()
            metaFile(context).writeText(json.toString(2))
        }
    }

    private fun token(): String? = Settings.current().let { if (it.githubToken.isBlank()) null else it.githubToken }

    private fun localStrategiesVersion(context: Context): String {
        val meta = readMeta(context).optJSONObject("strategies")
        return meta?.optString("version").orEmpty().ifBlank { "встроенные" }
    }

    private fun baseComponents(context: Context): List<ComponentInfo> {
        val meta = readMeta(context)
        val list = ArrayList<ComponentInfo>()

        list += ComponentInfo(
            id = "strategies",
            title = "Стратегии byedpi",
            description = "Список стратегий из ByeByeDPI (${Strategies.all(context).size} шт.)",
            localVersion = localStrategiesVersion(context),
            status = ComponentStatus.UNKNOWN,
        )
        list += ComponentInfo(
            id = "sites",
            title = "Списки сайтов для теста",
            description = "${sites.size} файлов доменов из ByeByeDPI",
            localVersion = meta.optJSONObject("sites")?.optString("version").orEmpty().ifBlank { "встроенные" },
            status = ComponentStatus.UNKNOWN,
        )
        list += ComponentInfo(
            id = "cf_domains",
            title = "Пул CF-доменов Telegram",
            description = "Домены Cloudflare Proxy из Flowseal/tg-ws-proxy",
            localVersion = meta.optJSONObject("cf_domains")?.optString("version").orEmpty().ifBlank { "встроенный" },
            status = ComponentStatus.UNKNOWN,
        )
        list += ComponentInfo(
            id = "byedpi_engine",
            title = "Движок обхода (byedpi)",
            description = "Собирается вместе с приложением из hufrea/byedpi",
            localVersion = "встроен",
            status = ComponentStatus.UP_TO_DATE,
            builtin = true,
            note = "Обновляется вместе с приложением",
        )
        list += ComponentInfo(
            id = "tgws_engine",
            title = "Движок Telegram (Go)",
            description = "libtgwsproxy из Regstar2/tg-ws-proxy-android",
            localVersion = "встроен",
            status = ComponentStatus.UP_TO_DATE,
            builtin = true,
            note = "Обновляется вместе с приложением",
        )
        return list
    }

    fun refresh(context: Context, force: Boolean = false) {
        if (_state.value.checking) return
        val app = context.applicationContext
        _state.value = _state.value.copy(checking = true, message = "", components = baseComponents(app))
        ZmScope.launch {
            val token = token()
            val updated = baseComponents(app).toMutableList()

            // Стратегии и сайты: версия = SHA коммита, изменившего файл.
            val strategiesCommit = GitHub.pathCommit(BuildConfig.BYEDPI_REPO, STRATEGIES_PATH, token)
            updated[0] = updated[0].let { component ->
                val remote = strategiesCommit?.first?.take(7)
                val local = component.localVersion
                component.copy(
                    remoteVersion = remote,
                    status = when {
                        remote == null -> ComponentStatus.UNKNOWN
                        local == remote -> ComponentStatus.UP_TO_DATE
                        else -> ComponentStatus.UPDATE_AVAILABLE
                    },
                )
            }
            val sitesCommit = GitHub.pathCommit(BuildConfig.BYEDPI_REPO, SITES_PATH, token)
            updated[1] = updated[1].let { component ->
                val remote = sitesCommit?.first?.take(7)
                component.copy(
                    remoteVersion = remote,
                    status = when {
                        remote == null -> ComponentStatus.UNKNOWN
                        component.localVersion == remote -> ComponentStatus.UP_TO_DATE
                        else -> ComponentStatus.UPDATE_AVAILABLE
                    },
                )
            }
            // CF-домены: версия = SHA-256 содержимого.
            val cfRemote = runCatching {
                val text = Net.getText("$CF_DOMAINS_URL?t=${System.currentTimeMillis()}", token)
                val file = File(componentDir(app), "cf-domains.remote")
                file.writeText(text)
                val count = text.lineSequence().count { it.isNotBlank() && !it.startsWith("#") }
                "${text.hashCode().toUInt().toString(16)} ($count доменов)" to file
            }.getOrNull()
            updated[2] = updated[2].let { component ->
                val remote = cfRemote?.first
                component.copy(
                    remoteVersion = remote,
                    status = when {
                        remote == null -> ComponentStatus.ERROR
                        component.localVersion == remote -> ComponentStatus.UP_TO_DATE
                        else -> ComponentStatus.UPDATE_AVAILABLE
                    },
                )
            }

            _state.value = _state.value.copy(
                components = updated,
                checking = false,
                message = "Проверка завершена",
                lastCheck = System.currentTimeMillis(),
            )

            if (Settings.current().checkUpdatesOnStart || force) {
                checkAppUpdate(app)
            }

            // Автообновление компонентов: не чаще раза в 6 часов.
            val meta = readMeta(app)
            val lastAuto = meta.optLong("last_auto_update", 0L)
            val available = updated.filter { it.status == ComponentStatus.UPDATE_AVAILABLE && !it.builtin }
            if (Settings.current().autoUpdateComponents &&
                available.isNotEmpty() &&
                System.currentTimeMillis() - lastAuto > 6 * 60 * 60 * 1000L
            ) {
                meta.put("last_auto_update", System.currentTimeMillis())
                writeMeta(app, meta)
                LogBus.i("Обновление", "Автообновление компонентов: ${available.joinToString { it.id }}")
                updateAllComponents(app)
            }
        }
    }

    fun checkAppUpdate(context: Context) {
        val app = context.applicationContext
        ZmScope.launch {
            val info = runCatching { ApkUpdater(app).check() }.getOrElse {
                _state.value = _state.value.copy(message = "Не удалось проверить обновление: ${it.message}")
                null
            }
            _state.value = _state.value.copy(appUpdate = info)
            if (info != null) {
                _state.value = _state.value.copy(message = "Доступна версия ${info.version}")
                Notifications.notifyUpdate(app, info.version, "Нажмите, чтобы открыть «Обновления»")
            }
        }
    }

    fun updateComponent(context: Context, id: String) {
        val app = context.applicationContext
        _state.value = _state.value.copy(updating = true, progress = -1f, message = "Обновление…")
        ZmScope.launch {
            try {
                when (id) {
                    "strategies" -> updateStrategies(app)
                    "sites" -> updateSites(app)
                    "cf_domains" -> updateCfDomains(app)
                    else -> Unit
                }
                _state.value = _state.value.copy(message = "Компонент обновлён")
            } catch (t: Throwable) {
                _state.value = _state.value.copy(message = "Ошибка обновления: ${t.message}")
                LogBus.e("Обновление", "Компонент $id: ${t.message}")
            } finally {
                _state.value = _state.value.copy(updating = false, progress = -1f)
                refresh(app)
            }
        }
    }

    fun updateAllComponents(context: Context) {
        val app = context.applicationContext
        _state.value = _state.value.copy(updating = true, progress = -1f, message = "Обновление всех компонентов…")
        ZmScope.launch {
            for (id in listOf("strategies", "sites", "cf_domains")) {
                runCatching {
                    when (id) {
                        "strategies" -> updateStrategies(app)
                        "sites" -> updateSites(app)
                        "cf_domains" -> updateCfDomains(app)
                    }
                }.onFailure { LogBus.w("Обновление", "$id: ${it.message}") }
            }
            _state.value = _state.value.copy(updating = false, progress = -1f, message = "Компоненты обновлены")
            refresh(app)
        }
    }

    private suspend fun updateStrategies(context: Context) = withContext(Dispatchers.IO) {
        val url = "https://raw.githubusercontent.com/${BuildConfig.BYEDPI_REPO}/master/$STRATEGIES_PATH"
        val text = Net.getText(url)
        val count = Strategies.saveDownloaded(context, text)
        val sha = GitHub.pathCommit(BuildConfig.BYEDPI_REPO, STRATEGIES_PATH, token())?.first?.take(7) ?: "unknown"
        val meta = readMeta(context)
        meta.put("strategies", JSONObject().put("version", sha).put("count", count).put("at", System.currentTimeMillis()))
        writeMeta(context, meta)
        LogBus.i("Обновление", "Стратегии: $count шт., версия $sha")
    }

    private suspend fun updateSites(context: Context) = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "components/sites").apply { mkdirs() }
        for ((name, label) in sites) {
            val url = "https://raw.githubusercontent.com/${BuildConfig.BYEDPI_REPO}/master/$SITES_DIR/$name"
            try {
                val text = Net.getText(url)
                File(dir, name).writeText(text)
            } catch (t: Throwable) {
                LogBus.w("Обновление", "$label: ${t.message}")
            }
        }
        val sha = GitHub.pathCommit(BuildConfig.BYEDPI_REPO, SITES_PATH, token())?.first?.take(7) ?: "unknown"
        val meta = readMeta(context)
        meta.put("sites", JSONObject().put("version", sha).put("at", System.currentTimeMillis()))
        writeMeta(context, meta)
        LogBus.i("Обновление", "Списки сайтов обновлены (${sites.size} файлов), версия $sha")
    }

    private suspend fun updateCfDomains(context: Context) = withContext(Dispatchers.IO) {
        val text = Net.getText("$CF_DOMAINS_URL?t=${System.currentTimeMillis()}", token())
        val domains = text.lineSequence().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
        if (domains.size < 3) throw IllegalStateException("Слишком короткий список доменов: ${domains.size}")
        val file = File(componentDir(context), "cf-domains.txt")
        file.writeText(domains.joinToString("\n"))
        // Если прокси уже работает — применяем на лету.
        TgNative.applyCachedDomains(domains)
        val meta = readMeta(context)
        meta.put(
            "cf_domains",
            JSONObject()
                .put("version", "${text.hashCode().toUInt().toString(16)} (${domains.size} доменов)")
                .put("at", System.currentTimeMillis()),
        )
        writeMeta(context, meta)
        LogBus.i("Обновление", "Пул CF-доменов обновлён: ${domains.size} шт.")
    }

    fun downloadAppUpdate(context: Context, onProgress: (Float, String) -> Unit) {
        val app = context.applicationContext
        val info = _state.value.appUpdate ?: return
        _state.value = _state.value.copy(updating = true, progress = 0f, message = "Скачивание APK")
        ZmScope.launch {
            try {
                val updater = ApkUpdater(app)
                val file = updater.download(info) { downloaded, total ->
                    val fraction = if (total > 0) downloaded.toFloat() / total else -1f
                    onProgress(fraction, "${Versions.humanBytes(downloaded)} / ${Versions.humanBytes(total)}")
                    _state.value = _state.value.copy(
                        progress = fraction,
                        progressText = "${Versions.humanBytes(downloaded)} / ${Versions.humanBytes(total)}",
                    )
                }
                _state.value = _state.value.copy(message = "APK проверен, запускаю установщик")
                val started = updater.install(file)
                if (!started) updater.installViaIntent(file)
                _state.value = _state.value.copy(updating = false, progress = -1f)
            } catch (t: Throwable) {
                LogBus.e("Обновление", "Не удалось скачать обновление: ${t.message}")
                _state.value = _state.value.copy(
                    updating = false,
                    progress = -1f,
                    message = "Ошибка: ${t.message}",
                )
            }
        }
    }

    fun canInstall(context: Context): Boolean = ApkUpdater(context).canInstall()
    fun installPermissionIntent(context: Context): android.content.Intent = ApkUpdater(context).openInstallPermission()
}
