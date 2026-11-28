package io.github.robin19666.zapretmanager.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.robin19666.zapretmanager.byedpi.DpiEngine
import io.github.robin19666.zapretmanager.core.CheckStatus
import io.github.robin19666.zapretmanager.core.DiagnosticCheck
import io.github.robin19666.zapretmanager.core.DiagnosticFix
import io.github.robin19666.zapretmanager.core.Diagnostics
import io.github.robin19666.zapretmanager.core.Power
import io.github.robin19666.zapretmanager.core.UpdateManager
import io.github.robin19666.zapretmanager.tgproxy.TgProxyController
import io.github.robin19666.zapretmanager.ui.GhostButton
import io.github.robin19666.zapretmanager.ui.PrimaryButton
import io.github.robin19666.zapretmanager.ui.SectionTitle
import io.github.robin19666.zapretmanager.ui.ZmCard
import io.github.robin19666.zapretmanager.ui.ZmViewModel
import io.github.robin19666.zapretmanager.ui.copyToClipboard
import io.github.robin19666.zapretmanager.ui.theme.ZmColors
import kotlinx.coroutines.launch

@Composable
fun DiagnosticsScreen(vm: ZmViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checks by remember { mutableStateOf<List<DiagnosticCheck>?>(null) }
    var running by remember { mutableStateOf(false) }

    suspend fun refresh() {
        running = true
        checks = Diagnostics.run(context)
        running = false
    }

    LaunchedEffect(Unit) { refresh() }

    fun applyFix(check: DiagnosticCheck) {
        when (check.fix) {
            DiagnosticFix.VPN_PERMISSION -> {
                DpiEngine.prepareIntent(context)?.let {
                    runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
            DiagnosticFix.BATTERY -> Power.openPowerSettings(context)
            DiagnosticFix.AUTOSTART -> Power.openAutostartSettings(context)
            DiagnosticFix.NOTIFICATIONS -> runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            DiagnosticFix.INSTALL_PACKAGES ->
                runCatching { context.startActivity(UpdateManager.installPermissionIntent(context)) }
            DiagnosticFix.PRIVATE_DNS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching {
                    context.startActivity(
                        Intent("android.settings.PRIVATE_DNS_SETTINGS")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            DiagnosticFix.VPN_SETTINGS -> runCatching {
                context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            DiagnosticFix.START_DPI -> DpiEngine.startNow()
            DiagnosticFix.STOP_DPI -> DpiEngine.stop()
            DiagnosticFix.START_TG -> TgProxyController.startNow()
            DiagnosticFix.STOP_TG -> TgProxyController.stop()
            DiagnosticFix.RESTART_TG -> TgProxyController.restart()
            DiagnosticFix.UPDATE_COMPONENTS -> UpdateManager.updateAllComponents(context)
            DiagnosticFix.NONE -> Unit
        }
        // Даём системе/движку секунду и перепроверяем.
        scope.launch {
            kotlinx.coroutines.delay(1_500)
            refresh()
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
                    Text("Диагностика", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Разрешения, движки, порты, обновления и сетевой путь",
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

        val all = checks
        if (all == null) {
            item {
                ZmCard {
                    Text("Проверяю…", color = ZmColors.textSecondary)
                }
            }
        } else {
            val okCount = all.count { it.status == CheckStatus.OK }
            val problems = all.count { it.status == CheckStatus.FAIL || it.status == CheckStatus.WARN }
            item {
                ZmCard {
                    Text(
                        "$okCount из ${all.size} в порядке, проблем: $problems",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton(
                            "Прогнать заново",
                            modifier = Modifier.weight(1f),
                            enabled = !running,
                        ) { scope.launch { refresh() } }
                        GhostButton("Копировать отчёт", modifier = Modifier.weight(1f)) {
                            context.copyToClipboard("Диагностика", Diagnostics.report(context, all))
                        }
                    }
                }
            }

            all.groupBy { it.group }.forEach { (group, groupChecks) ->
                item { SectionTitle(group) }
                items(groupChecks.size) { index ->
                    val check = groupChecks[index]
                    ZmCard(modifier = Modifier.padding(vertical = 1.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .background(statusColor(check.status), CircleShape)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(check.title, fontSize = 13.sp)
                                Text(
                                    check.detail,
                                    fontSize = 11.sp,
                                    color = ZmColors.textSecondary,
                                )
                            }
                            if (check.fix != DiagnosticFix.NONE && check.fixLabel.isNotBlank()) {
                                GhostButton(
                                    text = check.fixLabel,
                                    modifier = Modifier.width(116.dp),
                                ) { applyFix(check) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun statusColor(status: CheckStatus) = when (status) {
    CheckStatus.OK -> ZmColors.accent
    CheckStatus.WARN -> ZmColors.warn
    CheckStatus.FAIL -> ZmColors.error
    CheckStatus.INFO -> ZmColors.textSecondary
}
