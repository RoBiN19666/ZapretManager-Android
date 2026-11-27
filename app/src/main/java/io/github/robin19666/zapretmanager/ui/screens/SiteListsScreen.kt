package io.github.robin19666.zapretmanager.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.robin19666.zapretmanager.core.SiteListInfo
import io.github.robin19666.zapretmanager.core.SiteLists
import io.github.robin19666.zapretmanager.core.UpdateManager
import io.github.robin19666.zapretmanager.ui.GhostButton
import io.github.robin19666.zapretmanager.ui.PrimaryButton
import io.github.robin19666.zapretmanager.ui.SectionTitle
import io.github.robin19666.zapretmanager.ui.ZmCard
import io.github.robin19666.zapretmanager.ui.toast
import io.github.robin19666.zapretmanager.ui.theme.ZmColors

@Composable
fun SiteListsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var lists by remember { mutableStateOf(SiteLists.info(context)) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var report by remember { mutableStateOf("") }

    fun refresh() {
        lists = SiteLists.info(context)
    }

    fun open(id: String) {
        selectedId = id
        text = SiteLists.load(context, id).joinToString("\n")
        report = ""
    }

    val selected = selectedId

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад", tint = ZmColors.textSecondary)
                }
                Column(Modifier.weight(1f)) {
                    Text("Списки сайтов", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Домены для проверки сайтов и теста стратегий. Правки сохраняются локально.",
                        fontSize = 12.sp,
                        color = ZmColors.textSecondary,
                    )
                }
            }
        }

        if (selected == null) {
            item {
                ZmCard {
                    Text(
                        "Списки приходят из ByeByeDPI. Изменённые вручную файлы помечаются, " +
                            "их можно откатить к исходной версии.",
                        fontSize = 11.sp,
                        color = ZmColors.textSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton("Обновить списки из репозитория", modifier = Modifier.fillMaxWidth()) {
                        UpdateManager.updateComponent(context, "sites")
                        context.toast("Обновление запущено — списки появятся в журнале")
                    }
                }
            }
            items(lists, key = { it.id }) { info ->
                ListRow(info) { open(info.id) }
            }
        } else {
            item {
                ZmCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle(
                            lists.firstOrNull { it.id == selected }?.title ?: selected,
                            modifier = Modifier.weight(1f),
                        )
                        GhostButton("К списку", modifier = Modifier.width(120.dp)) {
                            selectedId = null
                            refresh()
                        }
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 380.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        placeholder = { Text("example.com\nsub.example.org", fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ZmColors.accent,
                            unfocusedBorderColor = ZmColors.divider,
                        ),
                    )
                    if (report.isNotBlank()) {
                        Text(report, fontSize = 11.sp, color = ZmColors.accent, modifier = Modifier.padding(top = 6.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton("Сохранить", modifier = Modifier.fillMaxWidth()) {
                        val result = runCatching { SiteLists.save(context, selected, text) }
                            .onSuccess { saved ->
                                report = "Сохранено: ${saved.summary}"
                                text = SiteLists.load(context, selected).joinToString("\n")
                                refresh()
                            }
                            .onFailure { context.toast(it.message ?: "Не удалось сохранить") }
                        result.getOrNull()
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("Сброс к upstream", modifier = Modifier.weight(1f)) {
                            val ok = SiteLists.reset(context, selected)
                            text = SiteLists.load(context, selected).joinToString("\n")
                            report = if (ok) "Возвращена исходная версия" else "Исходной копии нет — список очищен"
                            refresh()
                        }
                        GhostButton("Очистить", modifier = Modifier.weight(1f)) {
                            SiteLists.clear(context, selected)
                            text = ""
                            report = "Список очищен"
                            refresh()
                        }
                    }
                    Text(
                        "Дубликаты, пустые строки и мусор отбрасываются при сохранении; " +
                            "строки с # считаются комментариями.",
                        fontSize = 10.sp,
                        color = ZmColors.textSecondary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ListRow(info: SiteListInfo, onOpen: () -> Unit) {
    ZmCard(modifier = Modifier.clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(info.title, fontSize = 14.sp)
                Text(
                    "${info.count} доменов" + if (info.modified) " · изменён вручную" else "",
                    fontSize = 11.sp,
                    color = if (info.modified) ZmColors.warn else ZmColors.textSecondary,
                )
            }
            Text("изменить", fontSize = 12.sp, color = ZmColors.accent)
        }
    }
}
