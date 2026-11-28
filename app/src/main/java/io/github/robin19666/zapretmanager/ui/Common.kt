package io.github.robin19666.zapretmanager.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.robin19666.zapretmanager.core.EnginePhase
import io.github.robin19666.zapretmanager.ui.theme.ZmColors

@Composable
fun ZmCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ZmColors.surface, RoundedCornerShape(18.dp))
            .border(1.dp, ZmColors.divider, RoundedCornerShape(18.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = ZmColors.textSecondary,
        modifier = modifier.padding(bottom = 8.dp, top = 4.dp),
    )
}

@Composable
fun StatusDot(phase: EnginePhase, modifier: Modifier = Modifier) {
    val color = when (phase) {
        EnginePhase.RUNNING -> ZmColors.accent
        EnginePhase.STARTING, EnginePhase.STOPPING -> ZmColors.warn
        EnginePhase.ERROR -> ZmColors.error
        EnginePhase.STOPPED -> Color(0xFF5B6B7D)
    }
    Box(
        modifier = modifier
            .size(10.dp)
            .background(color, CircleShape),
    )
}

/**
 * Карточка движка: иконка, название, состояние и переключатель.
 *
 * Включение анимировано: подсветка карточки, пульсирующий индикатор работы,
 * плавная смена цвета иконки и лёгкое увеличение переключателя.
 */
@Composable
fun EngineRow(
    title: String,
    subtitle: String,
    phase: EnginePhase,
    checked: Boolean,
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    val active = checked && phase != EnginePhase.STOPPED
    val failed = phase == EnginePhase.ERROR
    val accent = when {
        failed -> ZmColors.error
        phase == EnginePhase.RUNNING -> ZmColors.accent
        phase == EnginePhase.STARTING || phase == EnginePhase.STOPPING -> ZmColors.warn
        else -> Color(0xFF5B6B7D)
    }
    val container by animateColorAsState(
        targetValue = when {
            failed -> ZmColors.error.copy(alpha = 0.08f)
            active -> ZmColors.accent.copy(alpha = 0.08f)
            else -> Color.Transparent
        },
        animationSpec = tween(280),
        label = "engineContainer",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            failed -> ZmColors.error.copy(alpha = 0.45f)
            active -> ZmColors.accent.copy(alpha = 0.40f)
            else -> ZmColors.divider
        },
        animationSpec = tween(280),
        label = "engineBorder",
    )
    val iconTint by animateColorAsState(
        targetValue = accent,
        animationSpec = tween(280),
        label = "engineTint",
    )
    val iconBackground by animateColorAsState(
        targetValue = accent.copy(alpha = if (active || failed) 0.18f else 0.10f),
        animationSpec = tween(280),
        label = "engineIconBackground",
    )
    val switchScale by animateFloatAsState(
        targetValue = if (active) 1.06f else 1f,
        animationSpec = spring(
            dampingRatio = 0.45f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "engineSwitchScale",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EngineIcon(phase, icon, iconTint, iconBackground)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (failed) ZmColors.error else ZmColors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.scale(switchScale),
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = ZmColors.accent,
                uncheckedThumbColor = ZmColors.textSecondary,
                uncheckedTrackColor = ZmColors.surfaceHigh,
            ),
        )
    }
}

/** Иконка движка с «дышащим» индикатором, пока туннель работает или запускается. */
@Composable
private fun EngineIcon(
    phase: EnginePhase,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    tint: Color,
    background: Color,
) {
    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        if (phase == EnginePhase.RUNNING || phase == EnginePhase.STARTING) {
            val transition = rememberInfiniteTransition(label = "enginePulse")
            val ringScale by transition.animateFloat(
                initialValue = 1f,
                targetValue = 1.5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 1500,
                        easing = LinearOutSlowInEasing,
                    ),
                ),
                label = "enginePulseScale",
            )
            val ringAlpha by transition.animateFloat(
                initialValue = 0.30f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 1500,
                        easing = LinearOutSlowInEasing,
                    ),
                ),
                label = "enginePulseAlpha",
            )
            Box(
                Modifier
                    .size(40.dp)
                    .scale(ringScale)
                    .background(tint.copy(alpha = ringAlpha), CircleShape),
            )
        }
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Box(Modifier.size(10.dp).background(tint, CircleShape))
            }
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    subtitle: String = "",
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ZmColors.textSecondary)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = ZmColors.accent,
                uncheckedThumbColor = ZmColors.textSecondary,
                uncheckedTrackColor = ZmColors.surfaceHigh,
            ),
        )
    }
}

@Composable
fun KeyValueRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = ZmColors.textSecondary)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ZmColors.accent,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = ZmColors.surfaceHigh,
            disabledContentColor = ZmColors.textSecondary,
        ),
    ) {
        Text(
            text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ZmColors.divider),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Text(
            text,
            fontSize = 13.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun Context.copyToClipboard(label: String, text: String) {
    val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText(label, text))
    toast("Скопировано")
}

fun Context.openUrl(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { toast("Не удалось открыть ссылку") }
}