package io.github.robin19666.zapretmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.robin19666.zapretmanager.byedpi.ByeDpiVpnService
import io.github.robin19666.zapretmanager.byedpi.DpiEngine
import io.github.robin19666.zapretmanager.core.EnginePhase
import io.github.robin19666.zapretmanager.core.SiteCheckEngine
import io.github.robin19666.zapretmanager.core.SiteCheckReport
import io.github.robin19666.zapretmanager.core.SiteVerdict
import io.github.robin19666.zapretmanager.ui.GhostButton
import io.github.robin19666.zapretmanager.ui.PrimaryButton
import io.github.robin19666.zapretmanager.ui.ZmCard
import io.github.robin19666.zapretmanager.ui.copyToClipboard
import io.github.robin19666.zapretmanager.ui.theme.ZmColors
import kotlinx.coroutines.launch

@Composable
fun SiteCheckScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dpiState by DpiEngine.state.collectAsState()
    val dpiRunning = dpiState.phase == EnginePhase.RUNNING
    var host by remember { mutableStateOf("youtube.com") }
    var running by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<SiteCheckReport?>(null) }

    fun check() {
        if (running) return
        running = true
        scope.launch {
            report = SiteCheckEngine.check(
                host,
                socksPort = if (dpiRunning) {
                    ByeDpiVpnService.SOCKS_PORT
                } else {
                    null
                },
            )
            running = false
        }
    }

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
                    Text("Проверка сайта", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "DNS → TCP → TLS → HTTP с вердиктом: что именно мешает",
                        fontSize = 12.sp,
                        color = ZmColors.textSecondary,
                    )
                }
                if (running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = ZmColors.accent,
                    )
                }
            }
        }

        item {
            ZmCard {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Домен") },
                    placeholder = { Text("youtube.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZmColors.accent,
                        unfocusedBorderColor = ZmColors.divider,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("youtube.com", "discord.com", "telegram.org", "rutracker.org").forEach { sample ->
                        FilterChip(
                            selected = host == sample,
                            onClick = { host = sample },
                            label = { Text(sample, fontSize = 10.sp, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ZmColors.accent.copy(alpha = 0.18f),
                                selectedLabelColor = ZmColors.accent,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    text = if (running) "Проверяю…" else "Проверить",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !running,
                ) { check() }
                Text(
                    if (dpiRunning) {
                        "Проверка идёт через SOCKS-порт byedpi (туннель включён)."
                    } else {
                        "Ускорение соединения выключен: проверяется прямое соединение."
                    },
                    fontSize = 10.sp,
                    color = ZmColors.textSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        val result = report
        if (result != null) {
            item {
                ZmCard {
                    Text(
                        result.verdictText(),
                        style = MaterialTheme.typography.titleMedium,
                        color = when (result.verdict) {
                            SiteVerdict.OK -> ZmColors.accent
                            SiteVerdict.UNKNOWN -> ZmColors.textSecondary
                            else -> ZmColors.error
                        },
                    )
                    Text(
                        result.hint(),
                        fontSize = 11.sp,
                        color = ZmColors.textSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    result.address.takeIf { it.isNotBlank() }?.let {
                        Text("адрес: $it", fontSize = 11.sp, color = ZmColors.textSecondary)
                    }
                    Spacer(Modifier.height(8.dp))
                    result.steps.forEach { step ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (step.ok) "✓" else "✕",
                                color = if (step.ok) ZmColors.accent else ZmColors.error,
                                fontSize = 13.sp,
                                modifier = Modifier.width(20.dp),
                            )
                            Text(step.name, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text(
                                "${step.millis} мс · ${step.detail}",
                                fontSize = 10.sp,
                                color = ZmColors.textSecondary,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("Копировать отчёт", modifier = Modifier.weight(1f)) {
                            context.copyToClipboard("Проверка сайта", result.report())
                        }
                        GhostButton("Повторить", modifier = Modifier.weight(1f)) { check() }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        result.report(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = ZmColors.textSecondary,
                    )
                }
            }
        }
    }
}
