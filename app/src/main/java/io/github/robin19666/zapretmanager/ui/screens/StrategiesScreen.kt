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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.robin19666.zapretmanager.byedpi.CustomPreset
import io.github.robin19666.zapretmanager.byedpi.CustomPresets
import io.github.robin19666.zapretmanager.byedpi.Strategies
import io.github.robin19666.zapretmanager.byedpi.Strategy
import io.github.robin19666.zapretmanager.byedpi.StrategyTester
import io.github.robin19666.zapretmanager.core.AppSettings
import io.github.robin19666.zapretmanager.core.NetworkProfiles
import io.github.robin19666.zapretmanager.core.TuningGuard
import io.github.robin19666.zapretmanager.ui.GhostButton
import io.github.robin19666.zapretmanager.ui.PrimaryButton
import io.github.robin19666.zapretmanager.ui.SectionTitle
import io.github.robin19666.zapretmanager.ui.ZmCard
import io.github.robin19666.zapretmanager.ui.ZmProgressBar
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
    val tuningActive by TuningGuard.active.collectAsState()

    val filtered = remember(strategies, query) {
        if (query.isBlank()) strategies else strategies.filter {
            it.title.contains(query, true) || it.args.contains(query, true) || it.id.contains(query, true)
        }
    }
    val counts = remember(strategies) {
        strategies.groupingBy { Strategies.originLabel(it.origin) }.eachCount()
    }
    var showImport by remember { mutableStateOf(false) }
    var importText by rememberSaveable { mutableStateOf("") }
    var presets by remember { mutableStateOf<List<CustomPreset>>(emptyList()) }
    var showSavePreset by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }

    // Свои конфигурации читаем при открытии и после каждого изменения.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        presets = CustomPresets.all(context)
    }

    val refreshPresets = {
        presets = CustomPresets.all(context)
        vm.refreshStrategies()
    }

    val startOrCancelTest: () -> Unit = {
        if (testState.running) {
            StrategyTester.cancel()
        } else if (tuningActive) {
            context.toast("Сначала завершите автоподбор параметров")
        } else {
            val consent = VpnService.prepare(context)
            if (consent != null) {
                runCatching {
                    context.startActivity(consent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                context.toast("Разрешите VPN и повторите тест")
            } else {
                StrategyTester.start(context, strategies)
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                Text("Стратегии ускорения", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Наборы аргументов byedpi: встроенные, авторские из byedpi, список ByeByeDPI " +
                        "и свои. Свои можно вписать вручную или импортировать списком.",
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
                    ZmProgressBar(
                        progress = testState.progress,
                        modifier = Modifier.fillMaxWidth(),
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
                        enabled = testState.running || (strategies.isNotEmpty() && !tuningActive),
                    ) { startOrCancelTest() }
                    GhostButton(
                        text = "Лучшую",
                        modifier = Modifier.weight(1f),
                        enabled = !testState.running && testState.best != null,
                    ) {
                        val best = testState.best
                        val profile = StrategyTester.applyBest(context)
                        vm.refreshStrategies()
                        if (best != null) {
                            context.toast(
                                if (profile != null) {
                                    "«${best.title}» запомнена для «$profile»"
                                } else {
                                    "Выбрана «${best.title}» (${best.summary})"
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                GhostButton(
                    text = "Глубокий автоподбор параметров",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !testState.running && !tuningActive,
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
            ZmCard {
                SectionTitle("Источники (${strategies.size})")
                Text(
                    counts.entries.sortedByDescending { it.value }
                        .joinToString("  ·  ") { "${it.key}: ${it.value}" },
                    fontSize = 12.sp,
                    color = ZmColors.textSecondary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Встроенные наборы, авторские из byedpi, список ByeByeDPI и свои стратегии — " +
                        "все они доступны для теста и автоподбора.",
                    fontSize = 11.sp,
                    color = ZmColors.textSecondary,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Импорт стратегий", modifier = Modifier.weight(1f)) {
                        showImport = true
                    }
                    GhostButton(
                        "Очистить свои",
                        modifier = Modifier.weight(1f),
                        enabled = (counts["свои"] ?: 0) > 0,
                    ) {
                        Strategies.clearImported(context)
                        vm.refreshStrategies()
                        context.toast("Свой список очищен")
                    }
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
                    PrimaryButton("Применить", modifier = Modifier.weight(1f)) {
                        vm.setCustomArgs(customArgs.trim())
                        context.toast(
                            if (customArgs.isBlank()) "Свои аргументы отключены" else "Аргументы применены",
                        )
                    }
                    GhostButton("Сбросить", modifier = Modifier.weight(1f)) {
                        customArgs = ""
                        vm.setCustomArgs("")
                    }
                }
                Spacer(Modifier.height(8.dp))
                GhostButton(
                    text = "Сохранить как конфигурацию…",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = customArgs.isNotBlank(),
                ) {
                    presetName = CustomPresets.defaultName(customArgs.trim())
                    showSavePreset = true
                }
                Text(
                    "Если поле заполнено, аргументы стратегии игнорируются. {sni} подставляется автоматически.",
                    fontSize = 11.sp,
                    color = ZmColors.textSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(8.dp))
                GhostButton(
                    text = "Запомнить для этой сети",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = settings.customArgs.isNotBlank(),
                ) {
                    val profile = NetworkProfiles.rememberTuning(
                        context,
                        strategyId = settings.strategyId,
                        customArgs = settings.customArgs,
                    )
                    context.toast(
                        if (profile != null) "Запомнено для «${profile.label}»" else "Сеть не определена",
                    )
                }
            }
        }

        item {
            ZmCard {
                SectionTitle("Мои конфигурации (${presets.size})")
                if (presets.isEmpty()) {
                    Text(
                        "Пока пусто. Введите аргументы выше и нажмите «Сохранить как конфигурацию…» — " +
                            "конфигурация получит имя, её можно будет применить или удалить.",
                        fontSize = 11.sp,
                        color = ZmColors.textSecondary,
                    )
                } else {
                    presets.forEach { preset ->
                        val active = settings.strategyId == preset.id && settings.customArgs.isBlank()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.selectStrategy(preset.id)
                                    context.toast("Применено: ${preset.name}")
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    preset.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (active) ZmColors.accent else ZmColors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    preset.args,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = ZmColors.textSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = {
                                CustomPresets.delete(context, preset.id)
                                if (settings.strategyId == preset.id && settings.customArgs.isBlank()) {
                                    vm.selectStrategy("general")
                                }
                                refreshPresets()
                                context.toast("Удалено: ${preset.name}")
                            }) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = "Удалить «${preset.name}»",
                                    tint = ZmColors.textSecondary,
                                )
                            }
                        }
                    }
                    Text(
                        "Нажмите на запись, чтобы применить её к ускорению.",
                        fontSize = 10.sp,
                        color = ZmColors.textSecondary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
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
                if (strategy.origin != "builtin") {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "источник: ${Strategies.originLabel(strategy.origin)}",
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

    if (showImport) {
        Dialog(onDismissRequest = { showImport = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .background(ZmColors.surface, MaterialTheme.shapes.large)
                    .padding(16.dp),
            ) {
                Text("Свои стратегии", style = MaterialTheme.typography.titleMedium)
                Text(
                    "По одной строке на стратегию: аргументы byedpi, начинающиеся с «-». " +
                        "Например: -o1 -a1 -r-5+se",
                    fontSize = 11.sp,
                    color = ZmColors.textSecondary,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    placeholder = { Text("-d1 -s1+s -a1", fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 260.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZmColors.accent,
                        unfocusedBorderColor = ZmColors.divider,
                    ),
                )
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Импортировать", modifier = Modifier.fillMaxWidth()) {
                    runCatching { Strategies.saveImported(context, importText) }
                        .onSuccess { count ->
                            vm.refreshStrategies()
                            importText = ""
                            showImport = false
                            context.toast("Добавлено стратегий: $count")
                        }
                        .onFailure { context.toast(it.message ?: "Не удалось импортировать") }
                }
                Spacer(Modifier.height(6.dp))
                GhostButton("Отмена", modifier = Modifier.fillMaxWidth()) { showImport = false }
            }
        }
    }

    if (showSavePreset) {
        Dialog(onDismissRequest = { showSavePreset = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ZmColors.surface, MaterialTheme.shapes.large)
                    .padding(16.dp),
            ) {
                Text("Сохранить конфигурацию", style = MaterialTheme.typography.titleMedium)
                Text(
                    customArgs.trim(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = ZmColors.textSecondary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text("Имя конфигурации", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZmColors.accent,
                        unfocusedBorderColor = ZmColors.divider,
                    ),
                )
                Text(
                    "Например: «Дом, вечер» или «МТС — YouTube». Если имя уже есть, " +
                        "аргументы обновятся у существующей конфигурации.",
                    fontSize = 10.sp,
                    color = ZmColors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Сохранить", modifier = Modifier.fillMaxWidth()) {
                    val saved = CustomPresets.save(context, presetName, customArgs.trim())
                    if (saved == null) {
                        context.toast("Не удалось сохранить: пустые аргументы")
                    } else {
                        refreshPresets()
                        showSavePreset = false
                        context.toast("Сохранено: ${saved.name}")
                    }
                }
                Spacer(Modifier.height(6.dp))
                GhostButton("Отмена", modifier = Modifier.fillMaxWidth()) { showSavePreset = false }
            }
        }
    }
}