package io.github.robin19666.zapretmanager.ui.screens

import android.content.Intent
import android.net.VpnService
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.robin19666.zapretmanager.byedpi.Autotuner
import io.github.robin19666.zapretmanager.core.TuningGuard
import io.github.robin19666.zapretmanager.ui.GhostButton
import io.github.robin19666.zapretmanager.ui.PrimaryButton
import io.github.robin19666.zapretmanager.ui.SectionTitle
import io.github.robin19666.zapretmanager.ui.ZmCard
import io.github.robin19666.zapretmanager.ui.ZmProgressBar
import io.github.robin19666.zapretmanager.ui.ZmViewModel
import io.github.robin19666.zapretmanager.ui.copyToClipboard
import io.github.robin19666.zapretmanager.ui.toast
import io.github.robin19666.zapretmanager.ui.theme.ZmColors

/**
 * Глубокий автоподбор: перебор параметров byedpi на живом туннеле.
 * Итог — готовая строка аргументов, которую можно применить и сохранить.
 */
@Composable
fun AutotuneScreen(vm: ZmViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val state by Autotuner.state.collectAsState()
    val tuningActive by TuningGuard.active.collectAsState()

    val startOrCancel: () -> Unit = {
        if (state.running) {
            Autotuner.cancel()
        } else if (tuningActive) {
            context.toast("Сначала завершите тест стратегий")
        } else {
            val consent = VpnService.prepare(context)
            if (consent != null) {
                runCatching {
                    context.startActivity(consent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                context.toast("Разрешите VPN и повторите автоподбор")
            } else {
                Autotuner.start(context)
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад", tint = ZmColors.textSecondary)
                }
                Column(Modifier.weight(1f)) {
                    Text("Глубокий автоподбор", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Перебор параметров byedpi, а не готовых стратегий",
                        fontSize = 12.sp,
                        color = ZmColors.textSecondary,
                    )
                }
            }
        }

        item {
            ZmCard {
                Text(
                    "Что перебирается",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "1. Базовые техники: split, disorder, OOB, disoob, fake TLS, TLS record, mod-http " +
                        "с разными позициями (22 варианта).\n" +
                        "2. Комбинации лучшей базы со второй техникой (6 вариантов).\n" +
                        "3. Модификаторы: TTL, повторы, OOB-символ, MD5 signature (4 варианта).\n\n" +
                        "Каждый вариант проверяется на YouTube, Discord и Cloudflare через SOCKS-порт " +
                        "byedpi. Полный проход занимает 4–6 минут, экран можно свернуть — прогресс идёт в фоне.",
                    fontSize = 12.sp,
                    color = ZmColors.textSecondary,
                )
            }
        }

        item {
            ZmCard {
                if (state.running) {
                    Text(
                        state.stage.ifBlank { "Подготовка" },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "${state.index} из ${state.total}",
                        fontSize = 11.sp,
                        color = ZmColors.textSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    ZmProgressBar(
                        progress = state.progress,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.current?.let { candidate ->
                        Spacer(Modifier.height(8.dp))
                        Text(candidate.label, fontSize = 12.sp)
                        Text(
                            candidate.args,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = ZmColors.textSecondary,
                        )
                    }
                } else {
                    Text(
                        state.message.ifBlank { "Готов к запуску" },
                        fontSize = 12.sp,
                        color = ZmColors.textSecondary,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
                        text = if (state.running) "Остановить" else "Запустить автоподбор",
                        modifier = Modifier.weight(1f),
                        enabled = state.running || !tuningActive,
                    ) { startOrCancel() }
                    GhostButton(
                        text = "Применить лучшую",
                        modifier = Modifier.width(160.dp),
                        enabled = !state.running && !tuningActive && state.best != null,
                    ) {
                        state.best?.let { best ->
                            Autotuner.apply(best)
                            Autotuner.saveBest(context, best)
                            vm.refreshStrategies()
                            context.toast("Применено: ${best.summary}")
                        }
                    }
                }
            }
        }

        val best = state.best
        if (best != null) {
            item {
                ZmCard {
                    SectionTitle("Лучший результат")
                    Text(
                        best.candidate.args,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = ZmColors.accent,
                    )
                    Text(best.summary, fontSize = 11.sp, color = ZmColors.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("Копировать", modifier = Modifier.weight(1f)) {
                            context.copyToClipboard("byedpi args", best.candidate.args)
                        }
                        GhostButton("Сохранить в файл", modifier = Modifier.weight(1f)) {
                            Autotuner.saveBest(context, best)
                            context.toast("Сохранено в files/autotune-best.txt")
                        }
                    }
                }
            }
        }

        if (state.results.isNotEmpty()) {
            item { SectionTitle("Результаты (${state.results.size})") }
            items(state.results.size) { index ->
                val result = state.results
                    .sortedWith(
                        compareByDescending<io.github.robin19666.zapretmanager.byedpi.AutoResult> { it.passed }
                            .thenBy { if (it.averageMs <= 0) Long.MAX_VALUE else it.averageMs }
                    )[index]
                ZmCard(modifier = Modifier.padding(vertical = 2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(result.candidate.label, fontSize = 13.sp)
                            Text(
                                result.candidate.args,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = ZmColors.textSecondary,
                                maxLines = 2,
                            )
                        }
                        Text(
                            result.summary,
                            fontSize = 12.sp,
                            color = if (result.passed > 0) ZmColors.accent else ZmColors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}