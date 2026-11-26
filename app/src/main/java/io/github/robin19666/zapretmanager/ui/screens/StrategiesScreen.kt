package io.github.robin19666.zapretmanager.ui.screens

import android.content.Intent
import android.net.VpnService
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import io.github.robin19666.zapretmanager.byedpi.StrategyTester
import io.github.robin19666.zapretmanager.core.AppSettings
import io.github.robin19666.zapretmanager.ui.GhostButton
import io.github.robin19666.zapretmanager.ui.PrimaryButton
import io.github.robin19666.zapretmanager.ui.SectionTitle
import io.github.robin19666.zapretmanager.ui.ZmCard
import io.github.robin19666.zapretmanager.ui.ZmViewModel
import io.github.robin19666.zapretmanager.ui.toast
import io.github.robin19666.zapretmanager.ui.theme.ZmColors

@Composable
fun StrategiesScreen(
    vm: ZmViewModel,
    strategies: List<Strategy>,
    settings: AppSettings,
    onOpenAutotune: () -> Unit = {},
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var customArgs by rememberSaveable(settings.customArgs) { mutableStateOf(settings.customArgs) }
    val testState by StrategyTester.state.collectAsState()

    val filtered = remember(strategies, query) {
        if (query.isBlank()) strategies else strategies.filter {
            it.title.contains(query, true) || it.args.contains(query, true) || it.id.contains(query, true)
        }
    }

    val startOrCancelTest: () -> Unit = {
        if (testState.running) {
            StrategyTester.cancel()
        } else {
            val consent = VpnService.prepare(context)
            if (consent != null) {
                runCatching {
                    context.startActivity(consent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                context.toast("Разрешите VPN и повторите тест")
            } else {
                StrategyTester.start(strategies)
            }
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
                    color = ZmColors.textSecondary,
                )
            }
        }

        item {
            ZmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Тест стратегий", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Каждая стратегия проверяется на живом туннеле: YouTube, Discord, Telegram, Cloudflare",
                            fontSize = 11.sp,
                            color = ZmColors.textSecondary,
                        )
                    }
                }
                if (testState.running) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { testState.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = ZmColors.accent,
                        trackColor = ZmColors.surfaceHigh,
                    )
                    Text(
                        "${testState.current} из ${testState.total}: ${testState.currentTitle}",
                        fontSize = 11.sp,
                        color = ZmColors.textSecondary,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
                        text = if (testState.running) "Остановить" else "Проверить ${strategies.size}",
                        modifier = Modifier.weight(1f),
                        enabled = strategies.isNotEmpty(),
                    ) { startOrCancelTest() }
                    GhostButton(
                        text = "Лучшую",
                        modifier = Modifier.weight(1f),
                        enabled = !testState.running && testState.best != null,
                    ) {
                        val best = StrategyTester.applyBest()
                        if (best != null) {
                            vm.refreshStrategies()
                            context.toast("Выбрана «${best.title}» (${best.summary})")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                GhostButton(
                    text = "Глубокий автоподбор параметров",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !testState.running,
                ) { onOpenAutotune() }
                testState.results
                    .sortedWith(
                        compareByDescending<io.github.robin19666.zapretmanager.byedpi.StrategyTestResult> { it.score }
                            .thenBy { if (it.averageMs <= 0) Long.MAX_VALUE else it.averageMs }
                    )
                    .take(12)
                    .forEach { result ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                result.title,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            Text(
                                result.summary,
                                fontSize = 12.sp,
                                color = if (result.passed > 0) ZmColors.accent else ZmColors.textSecondary,
                            )
                        }
                    }
                if (testState.message.isNotBlank() && !testState.running) {
                    Text(testState.message, fontSize = 11.sp, color = ZmColors.textSecondary)
                }
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
                    focusedBorderColor = ZmColors.accent,
                    unfocusedBorderColor = ZmColors.divider,
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
                        focusedBorderColor = ZmColors.accent,
                        unfocusedBorderColor = ZmColors.divider,
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
                    color = ZmColors.textSecondary,
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
                    .background(if (selected) ZmColors.accent.copy(alpha = 0.08f) else ZmColors.surface, RoundedCornerShape(14.dp))
                    .border(
                        1.dp,
                        if (selected) ZmColors.accent else ZmColors.divider,
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
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = ZmColors.accent)
                    }
                }
                if (strategy.note.isNotBlank()) {
                    Text(strategy.note, fontSize = 12.sp, color = ZmColors.textSecondary)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    strategy.args,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = ZmColors.textSecondary,
                    maxLines = 3,
                )
                if (strategy.origin == "upstream") {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "из ByeByeDPI",
                        fontSize = 10.sp,
                        color = ZmColors.accent,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                ZmCard {
                    Text("Ничего не найдено", color = ZmColors.textSecondary)
                }
            }
        }
    }
}