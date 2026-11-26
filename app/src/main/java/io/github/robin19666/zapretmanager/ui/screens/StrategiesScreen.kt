package io.github.robin19666.zapretmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.robin19666.zapretmanager.byedpi.Strategy
import io.github.robin19666.zapretmanager.core.AppSettings
import io.github.robin19666.zapretmanager.ui.GhostButton
import io.github.robin19666.zapretmanager.ui.PrimaryButton
import io.github.robin19666.zapretmanager.ui.SectionTitle
import io.github.robin19666.zapretmanager.ui.ZmCard
import io.github.robin19666.zapretmanager.ui.ZmViewModel
import io.github.robin19666.zapretmanager.ui.toast
import io.github.robin19666.zapretmanager.ui.theme.ZmAccent
import io.github.robin19666.zapretmanager.ui.theme.ZmDivider
import io.github.robin19666.zapretmanager.ui.theme.ZmSurface
import io.github.robin19666.zapretmanager.ui.theme.ZmSurfaceHigh
import io.github.robin19666.zapretmanager.ui.theme.ZmTextSecondary

@Composable
fun StrategiesScreen(
    vm: ZmViewModel,
    strategies: List<Strategy>,
    settings: AppSettings,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var customArgs by rememberSaveable(settings.customArgs) { mutableStateOf(settings.customArgs) }

    val filtered = remember(strategies, query) {
        if (query.isBlank()) strategies else strategies.filter {
            it.title.contains(query, true) || it.args.contains(query, true) || it.id.contains(query, true)
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                Text("Стратегии обхода", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Наборы аргументов byedpi. Список обновляется из ByeByeDPI, свои варианты можно вписать вручную.",
                    fontSize = 12.sp,
                    color = ZmTextSecondary,
                )
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Поиск") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ZmAccent,
                    unfocusedBorderColor = ZmDivider,
                ),
            )
        }

        item {
            ZmCard {
                SectionTitle("Свои аргументы")
                OutlinedTextField(
                    value = customArgs,
                    onValueChange = { customArgs = it },
                    placeholder = { Text("-o1 -a1 -r-5+se", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZmAccent,
                        unfocusedBorderColor = ZmDivider,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Сохранить", modifier = Modifier.weight(1f)) {
                        vm.setCustomArgs(customArgs.trim())
                        context.toast("Аргументы сохранены")
                    }
                    GhostButton("Сбросить", modifier = Modifier.weight(1f)) {
                        customArgs = ""
                        vm.setCustomArgs("")
                    }
                }
                Text(
                    "Если поле заполнено, аргументы стратегии игнорируются. {sni} подставляется автоматически.",
                    fontSize = 11.sp,
                    color = ZmTextSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        item { SectionTitle("Доступные стратегии (${filtered.size})") }

        items(filtered, key = { it.id }) { strategy ->
            val selected = strategy.id == settings.strategyId && settings.customArgs.isBlank()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (selected) ZmAccent.copy(alpha = 0.08f) else ZmSurface, RoundedCornerShape(14.dp))
                    .border(
                        1.dp,
                        if (selected) ZmAccent else ZmDivider,
                        RoundedCornerShape(14.dp),
                    )
                    .clickable { vm.selectStrategy(strategy.id) }
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        strategy.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = ZmAccent)
                    }
                }
                if (strategy.note.isNotBlank()) {
                    Text(strategy.note, fontSize = 12.sp, color = ZmTextSecondary)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    strategy.args,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = ZmTextSecondary,
                    maxLines = 3,
                )
                if (strategy.origin == "upstream") {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "из ByeByeDPI",
                        fontSize = 10.sp,
                        color = ZmAccent,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                ZmCard {
                    Text("Ничего не найдено", color = ZmTextSecondary)
                }
            }
        }
    }
}
