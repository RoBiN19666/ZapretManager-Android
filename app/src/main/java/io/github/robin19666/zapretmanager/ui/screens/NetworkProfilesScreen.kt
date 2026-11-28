package io.github.robin19666.zapretmanager.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.window.Dialog
import io.github.robin19666.zapretmanager.byedpi.Strategies
import io.github.robin19666.zapretmanager.core.NetworkInfo
import io.github.robin19666.zapretmanager.core.NetworkProfile
import io.github.robin19666.zapretmanager.core.NetworkProfiles
import io.github.robin19666.zapretmanager.core.NetworkSnapshot
import io.github.robin19666.zapretmanager.core.NetworkWatcher
import io.github.robin19666.zapretmanager.core.Settings
import io.github.robin19666.zapretmanager.ui.GhostButton
import io.github.robin19666.zapretmanager.ui.PrimaryButton
import io.github.robin19666.zapretmanager.ui.SectionTitle
import io.github.robin19666.zapretmanager.ui.SwitchRow
import io.github.robin19666.zapretmanager.ui.ZmCard
import io.github.robin19666.zapretmanager.ui.ZmViewModel
import io.github.robin19666.zapretmanager.ui.toast
import io.github.robin19666.zapretmanager.ui.theme.ZmColors
import io.github.robin19666.zapretmanager.ui.LocalNavBarInset
import io.github.robin19666.zapretmanager.ui.LocalTopBarInset

@Composable
fun NetworkProfilesScreen(vm: ZmViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    var snapshot by remember { mutableStateOf(NetworkInfo.current(context)) }
    var profiles by remember { mutableStateOf(NetworkProfiles.all(context)) }
    var pickerFor by remember { mutableStateOf<NetworkProfile?>(null) }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        snapshot = NetworkInfo.current(context)
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        context.toast(if (fine) "Имя Wi-Fi доступно" else "Без разрешения профиль будет по шлюзу")
    }

    fun refresh() {
        snapshot = NetworkInfo.current(context)
        profiles = NetworkProfiles.all(context)
    }

    fun addCurrent() {
        if (snapshot.kind == NetworkSnapshot.KIND_NONE) {
            context.toast("Нет активной сети")
            return
        }
        val current = Settings.current()
        NetworkProfiles.upsert(
            context,
            NetworkProfiles.newProfile(
                snapshot = snapshot,
                strategyId = current.strategyId,
                customArgs = current.customArgs,
                tgPort = current.tgPort,
                tgRoute = current.tgRoute,
                tgMaskingDomain = current.tgMaskingDomain,
            ),
        )
        refresh()
        context.toast("Профиль для «${snapshot.label}» создан")
    }

    val matched = remember(profiles, snapshot) { NetworkProfiles.match(profiles, snapshot) }
    val weakKey = snapshot.matchKey.contains(":gw:") || snapshot.matchKey == "wifi:*"

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp + LocalTopBarInset.current, bottom = 16.dp + LocalNavBarInset.current),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад", tint = ZmColors.textSecondary)
                }
                Column(Modifier.weight(1f)) {
                    Text("Профили сети", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Свой конфиг под каждый Wi-Fi и оператора, автопереключение при смене сети",
                        fontSize = 12.sp,
                        color = ZmColors.textSecondary,
                    )
                }
            }
        }

        item {
            ZmCard {
                SectionTitle("Текущая сеть")
                Text(snapshot.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    when (snapshot.kind) {
                        NetworkSnapshot.KIND_WIFI -> "Wi-Fi · ${snapshot.matchKey}"
                        NetworkSnapshot.KIND_MOBILE -> "Мобильная сеть · ${snapshot.matchKey}"
                        else -> "нет сети"
                    },
                    fontSize = 11.sp,
                    color = ZmColors.textSecondary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (matched != null) {
                        "Профиль: «${matched.label}» · ${Strategies.resolve(context, matched.strategyId).title}"
                    } else {
                        "Профиль для этой сети не задан — используется текущий конфиг"
                    },
                    fontSize = 11.sp,
                    color = if (matched != null) ZmColors.accent else ZmColors.textSecondary,
                )
                if (weakKey) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Имя Wi-Fi скрыто системой, профиль привязан к шлюзу. Разрешите доступ к " +
                            "местоположению, чтобы различать сети по SSID.",
                        fontSize = 10.sp,
                        color = ZmColors.warn,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Добавить профиль", modifier = Modifier.weight(1f)) { addCurrent() }
                    GhostButton("Обновить", modifier = Modifier.width(120.dp)) { refresh() }
                }
                if (weakKey && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Spacer(Modifier.height(6.dp))
                    GhostButton("Разрешить имя Wi-Fi", modifier = Modifier.fillMaxWidth()) {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            )
                        )
                    }
                }
            }
        }

        item {
            ZmCard {
                SectionTitle("Автопереключение")
                SwitchRow(
                    title = "Переключать конфиг при смене сети",
                    subtitle = "Срабатывает, когда ускорение соединения включён",
                    checked = settings.networkAutoSwitch,
                    onCheckedChange = { enabled ->
                        vm.updateSettings { it.copy(networkAutoSwitch = enabled) }
                        if (enabled) NetworkWatcher.start(context) else NetworkWatcher.stop()
                    },
                )
                Text(
                    "Задержка, если Wi-Fi пропал, но остаётся включённым",
                    fontSize = 12.sp,
                    color = ZmColors.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 8, 15, 30).forEach { seconds ->
                        FilterChip(
                            selected = settings.wifiLossDelaySec == seconds,
                            onClick = { vm.updateSettings { it.copy(wifiLossDelaySec = seconds) } },
                            label = { Text("$seconds с", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ZmColors.accent.copy(alpha = 0.18f),
                                selectedLabelColor = ZmColors.accent,
                            ),
                        )
                    }
                }
                Text(
                    "Если Wi-Fi выключили — переключение на мобильный профиль происходит сразу. " +
                        "Если Wi-Fi вернулся — сразу возвращается его профиль.",
                    fontSize = 10.sp,
                    color = ZmColors.textSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        item { SectionTitle("Профили (${profiles.size})") }

        if (profiles.isEmpty()) {
            item {
                ZmCard {
                    Text(
                        "Профилей пока нет. Включите нужную сеть, выберите стратегию и нажмите " +
                            "«Добавить профиль».",
                        fontSize = 11.sp,
                        color = ZmColors.textSecondary,
                    )
                }
            }
        }

        items(profiles, key = { it.id }) { profile ->
            ZmCard(modifier = Modifier.clickable { pickerFor = profile }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(profile.label, fontSize = 14.sp)
                        Text(
                            (if (profile.matchKey == "*") "запасной профиль" else profile.matchKey),
                            fontSize = 10.sp,
                            color = ZmColors.textSecondary,
                        )
                        Text(
                            Strategies.resolve(context, profile.strategyId).title,
                            fontSize = 11.sp,
                            color = ZmColors.accent,
                        )
                    }
                    GhostButton("Изменить", modifier = Modifier.width(120.dp)) { pickerFor = profile }
                }
            }
        }
    }

    pickerFor?.let { profile ->
        StrategyPickerDialog(
            title = profile.label,
            onDismiss = { pickerFor = null },
            onPick = { strategyId ->
                NetworkProfiles.upsert(context, profile.copy(strategyId = strategyId))
                pickerFor = null
                refresh()
            },
            onMakeFallback = {
                NetworkProfiles.upsert(
                    context,
                    profile.copy(matchKey = "*", kind = NetworkSnapshot.KIND_DEFAULT, label = "${profile.label} (запасной)"),
                )
                pickerFor = null
                refresh()
            },
            onDelete = {
                NetworkProfiles.delete(context, profile.id)
                pickerFor = null
                refresh()
            },
        )
    }
}

@Composable
private fun StrategyPickerDialog(
    title: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onMakeFallback: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val strategies = remember { Strategies.all(context) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .background(ZmColors.surface, MaterialTheme.shapes.large)
                .padding(16.dp),
        ) {
            if (io.github.robin19666.zapretmanager.ui.theme.LocalNewYear.current) {
                io.github.robin19666.zapretmanager.ui.NewYearGarland(compact = true)
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text("Выберите стратегию для этого профиля", fontSize = 11.sp, color = ZmColors.textSecondary)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.heightIn(max = 340.dp)) {
                items(strategies, key = { it.id }) { strategy ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(strategy.id) }
                            .padding(vertical = 6.dp),
                    ) {
                        Text(strategy.title, fontSize = 13.sp)
                        Text(
                            strategy.args,
                            fontSize = 9.sp,
                            color = ZmColors.textSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Запасной", modifier = Modifier.weight(1f)) { onMakeFallback() }
                GhostButton("Удалить", modifier = Modifier.weight(1f)) { onDelete() }
            }
            Spacer(Modifier.height(6.dp))
            GhostButton("Отмена", modifier = Modifier.fillMaxWidth()) { onDismiss() }
        }
    }
}
