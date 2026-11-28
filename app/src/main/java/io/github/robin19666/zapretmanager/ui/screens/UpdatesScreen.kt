package io.github.robin19666.zapretmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.robin19666.zapretmanager.BuildConfig
import io.github.robin19666.zapretmanager.core.ComponentInfo
import io.github.robin19666.zapretmanager.core.ComponentStatus
import io.github.robin19666.zapretmanager.core.UpdateManager
import io.github.robin19666.zapretmanager.core.UpdateUiState
import io.github.robin19666.zapretmanager.ui.GhostButton
import io.github.robin19666.zapretmanager.ui.MarkdownText
import io.github.robin19666.zapretmanager.ui.PrimaryButton
import io.github.robin19666.zapretmanager.ui.SectionTitle
import io.github.robin19666.zapretmanager.ui.ZmCard
import io.github.robin19666.zapretmanager.ui.ZmProgressBar
import io.github.robin19666.zapretmanager.ui.ZmViewModel
import io.github.robin19666.zapretmanager.ui.openUrl
import io.github.robin19666.zapretmanager.ui.theme.ZmColors

@Composable
fun UpdatesScreen(vm: ZmViewModel, state: UpdateUiState) {
    val context = LocalContext.current
    var permissionEpoch by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionEpoch++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val canInstall = remember(permissionEpoch) { UpdateManager.canInstall(context) }
    var showNotes by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Обновления", style = MaterialTheme.typography.headlineSmall)
        }

        item {
            ZmCard {
                SectionTitle("Приложение")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Zapret Manager ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "versionCode ${BuildConfig.VERSION_CODE} · ${BuildConfig.SELF_REPO}",
                            fontSize = 11.sp,
                            color = ZmColors.textSecondary,
                        )
                    }
                    GhostButton("GitHub", modifier = Modifier.width(100.dp)) {
                        context.openUrl("https://github.com/${BuildConfig.SELF_REPO}/releases")
                    }
                }

                val update = state.appUpdate
                Spacer(Modifier.height(10.dp))
                if (update == null) {
                    Text(
                        if (state.checking) "Проверяю релизы…" else "Обновлений нет",
                        fontSize = 12.sp,
                        color = ZmColors.textSecondary,
                    )
                } else {
                    HorizontalDivider(color = ZmColors.divider)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Доступна версия ${update.version}",
                        color = ZmColors.accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (!canInstall) {
                        Text(
                            "Нужно разрешить установку из этого источника",
                            fontSize = 12.sp,
                            color = ZmColors.warn,
                        )
                        Spacer(Modifier.height(6.dp))
                        PrimaryButton("Разрешить установку", modifier = Modifier.fillMaxWidth()) {
                            runCatching { context.startActivity(UpdateManager.installPermissionIntent(context)) }
                        }
                    } else {
                        PrimaryButton(
                            text = if (state.updating) "Скачивание…" else "Скачать и установить",
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.updating,
                        ) { vm.downloadAppUpdate() }
                    }
                    if (state.progress >= 0f) {
                        Spacer(Modifier.height(8.dp))
                        ZmProgressBar(
                            progress = state.progress,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(state.progressText, fontSize = 11.sp, color = ZmColors.textSecondary)
                    }
                    // Описание свёрнуто, чтобы кнопки установки были на виду:
                    // длинный патчноут раскрывается отдельно и прокручивается.
                    if (update.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        GhostButton(
                            text = if (showNotes) "Скрыть описание" else "Что нового",
                            modifier = Modifier.fillMaxWidth(),
                        ) { showNotes = !showNotes }
                        if (showNotes) {
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                MarkdownText(update.notes.take(6000))
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (state.message.contains("Подтвердите установку")) {
                            "Открылся системный установщик — подтвердите обновление и откройте приложение вручную."
                        } else {
                            "После установки откройте Zapret Manager вручную и включите движки."
                        },
                        fontSize = 10.sp,
                        color = ZmColors.textSecondary,
                    )
                }
            }
        }

        item {
            ZmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Компоненты", modifier = Modifier.weight(1f))
                    Text(
                        if (state.lastCheck > 0) "проверено" else "",
                        fontSize = 10.sp,
                        color = ZmColors.textSecondary,
                    )
                }
                if (state.components.isEmpty()) {
                    Text("Нажмите «Проверить обновления»", fontSize = 12.sp, color = ZmColors.textSecondary)
                }
                state.components.forEachIndexed { index, component ->
                    if (index > 0) HorizontalDivider(color = ZmColors.divider, modifier = Modifier.padding(vertical = 8.dp))
                    ComponentRow(component, state.updating) { vm.updateComponent(component.id) }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
                        text = if (state.checking) "Проверка…" else "Проверить",
                        modifier = Modifier.weight(1f),
                        enabled = !state.checking && !state.updating,
                    ) { vm.refreshUpdates() }
                    GhostButton(
                        text = "Обновить всё",
                        modifier = Modifier.weight(1f),
                        enabled = !state.checking && !state.updating,
                    ) { vm.updateEverything() }
                }
            }
        }

        if (state.message.isNotBlank()) {
            item {
                Text(
                    state.message,
                    fontSize = 12.sp,
                    color = if (state.message.startsWith("Ошибка")) ZmColors.error else ZmColors.textSecondary,
                )
            }
        }

        item {
            ZmCard {
                Text("Как обновляется", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "• Стратегии и списки сайтов — из репозитория ByeByeDPI (используются в движке ускорения).\n" +
                        "• Пул Cloudflare-доменов — из Flowseal/tg-ws-proxy.\n" +
                        "• Движки byedpi и libtgwsproxy собираются в APK, поэтому обновляются вместе с приложением.\n" +
                        "• APK проверяется по SHA-256 и подписи перед установкой.",
                    fontSize = 12.sp,
                    color = ZmColors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ComponentRow(component: ComponentInfo, busy: Boolean, onUpdate: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(component.title, style = MaterialTheme.typography.bodyMedium)
            Text(component.description, fontSize = 11.sp, color = ZmColors.textSecondary)
            val versionText = buildString {
                append("установлено: ${component.localVersion}")
                if (!component.remoteVersion.isNullOrBlank()) append(" · доступно: ${component.remoteVersion}")
            }
            Text(versionText, fontSize = 10.sp, color = ZmColors.textSecondary)
            if (component.note.isNotBlank()) {
                Text(component.note, fontSize = 10.sp, color = ZmColors.accent)
            }
        }
        when {
            component.builtin -> Text("встроен", fontSize = 11.sp, color = ZmColors.textSecondary)
            component.status == ComponentStatus.UPDATE_AVAILABLE -> PrimaryButton(
                text = "Обновить",
                modifier = Modifier.width(104.dp),
                enabled = !busy,
            ) { onUpdate() }
            component.status == ComponentStatus.UP_TO_DATE -> Text("актуально", fontSize = 11.sp, color = ZmColors.accent)
            component.status == ComponentStatus.ERROR -> Text("ошибка", fontSize = 11.sp, color = ZmColors.error)
            else -> Text("—", fontSize = 11.sp, color = ZmColors.textSecondary)
        }
    }
}