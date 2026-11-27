package io.github.robin19666.zapretmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.robin19666.zapretmanager.core.Hosts
import io.github.robin19666.zapretmanager.ui.GhostButton
import io.github.robin19666.zapretmanager.ui.PrimaryButton
import io.github.robin19666.zapretmanager.ui.SectionTitle
import io.github.robin19666.zapretmanager.ui.ZmCard
import io.github.robin19666.zapretmanager.ui.ZmViewModel
import io.github.robin19666.zapretmanager.ui.toast
import io.github.robin19666.zapretmanager.ui.theme.ZmColors

@Composable
fun HostsScreen(vm: ZmViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    var white by remember { mutableStateOf(Hosts.load(context)) }
    var black by remember { mutableStateOf(Hosts.loadBlack(context)) }
    var showBlack by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    fun refresh() {
        white = Hosts.load(context)
        black = Hosts.loadBlack(context)
    }

    val list = if (showBlack) black else white

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Назад", tint = ZmColors.textSecondary)
                }
                Column(Modifier.weight(1f)) {
                    Text("Домены для ускорениеа", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Белый список ускорениеа и исключения — как hostlist в zapret",
                        fontSize = 12.sp,
                        color = ZmColors.textSecondary,
                    )
                }
            }
        }

        item {
            ZmCard {
                SectionTitle("Режим")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.hostsMode == Hosts.HOSTS_ALL,
                        onClick = { vm.updateSettings { it.copy(hostsMode = Hosts.HOSTS_ALL) } },
                        label = { Text("Все домены", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ZmColors.accent.copy(alpha = 0.18f),
                            selectedLabelColor = ZmColors.accent,
                        ),
                    )
                    FilterChip(
                        selected = settings.hostsMode == Hosts.HOSTS_ONLY,
                        onClick = { vm.updateSettings { it.copy(hostsMode = Hosts.HOSTS_ONLY) } },
                        label = { Text("Только из списка", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ZmColors.accent.copy(alpha = 0.18f),
                            selectedLabelColor = ZmColors.accent,
                        ),
                    )
                }
                Text(
                    if (settings.hostsMode == Hosts.HOSTS_ONLY) {
                        "Ускорение соединения применяется только к доменам белого списка, минус исключения. " +
                            "Изменения действуют после перезапуска ускорениеа."
                    } else {
                        "Ускорение соединения применяется ко всем сайтам (по умолчанию)."
                    },
                    fontSize = 10.sp,
                    color = ZmColors.textSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !showBlack,
                    onClick = { showBlack = false },
                    label = { Text("Белый список (${white.size})", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ZmColors.accent.copy(alpha = 0.18f),
                        selectedLabelColor = ZmColors.accent,
                    ),
                )
                FilterChip(
                    selected = showBlack,
                    onClick = { showBlack = true },
                    label = { Text("Исключения (${black.size})", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ZmColors.error.copy(alpha = 0.18f),
                        selectedLabelColor = ZmColors.error,
                    ),
                )
            }
        }

        item {
            ZmCard {
                SectionTitle(if (showBlack) "Добавить исключение" else "Добавить домен")
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZmColors.accent,
                        unfocusedBorderColor = ZmColors.divider,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Добавить", modifier = Modifier.weight(1f)) {
                        val added = if (showBlack) Hosts.addBlack(context, input) else Hosts.add(context, input)
                        if (added) {
                            input = ""
                            refresh()
                            context.toast("Домен добавлен")
                        } else {
                            context.toast("Пустой или уже есть в списке")
                        }
                    }
                    GhostButton("Очистить", modifier = Modifier.width(120.dp)) {
                        if (showBlack) Hosts.saveBlack(context, emptyList()) else Hosts.save(context, emptyList())
                        refresh()
                        context.toast("Список очищен")
                    }
                }
                if (!showBlack) {
                    Spacer(Modifier.height(6.dp))
                    GhostButton("Добавить базовые домены", modifier = Modifier.fillMaxWidth()) {
                        val added = Hosts.addDefaults(context)
                        refresh()
                        context.toast(if (added > 0) "Добавлено доменов: $added" else "Базовые домены уже в списке")
                    }
                    Text(
                        "YouTube, Instagram, Discord, Telegram, Twitter/X, TikTok, OpenAI и другие " +
                            "популярные сервисы.",
                        fontSize = 10.sp,
                        color = ZmColors.textSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                } else {
                    Text(
                        "Исключения вычитаются из белого списка: такие домены ускорение не применяет. " +
                            "Режима «ускорять всё, кроме списка» у byedpi нет.",
                        fontSize = 10.sp,
                        color = ZmColors.warn,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        item { SectionTitle(if (showBlack) "Исключения (${list.size})" else "Белый список (${list.size})") }

        if (list.isEmpty()) {
            item {
                ZmCard {
                    Text(
                        if (showBlack) {
                            "Исключений нет."
                        } else {
                            "Список пуст. Пока он пуст, режим «только из списка» не ограничивает ускорение."
                        },
                        fontSize = 11.sp,
                        color = ZmColors.textSecondary,
                    )
                }
            }
        }

        items(list, key = { (if (showBlack) "b:" else "w:") + it }) { domain ->
            ZmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(domain, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    GhostButton("Удалить", modifier = Modifier.width(120.dp)) {
                        if (showBlack) Hosts.removeBlack(context, domain) else Hosts.remove(context, domain)
                        refresh()
                    }
                }
            }
        }
    }
}
