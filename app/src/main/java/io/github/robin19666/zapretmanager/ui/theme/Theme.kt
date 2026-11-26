package io.github.robin19666.zapretmanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Палитра приложения: одна и та же структура для тёмной и светлой темы. */
data class ZmPalette(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val accent: Color,
    val accentDark: Color,
    val blue: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val warn: Color,
    val error: Color,
)

val ZmDarkPalette = ZmPalette(
    background = Color(0xFF0B1017),
    surface = Color(0xFF121A24),
    surfaceHigh = Color(0xFF18222F),
    accent = Color(0xFF31D0AA),
    accentDark = Color(0xFF0F3B31),
    blue = Color(0xFF4C8DFF),
    textPrimary = Color(0xFFE6EDF5),
    textSecondary = Color(0xFF93A4B8),
    divider = Color(0xFF1F2A38),
    warn = Color(0xFFFFC857),
    error = Color(0xFFFF6B6B),
)

val ZmLightPalette = ZmPalette(
    background = Color(0xFFF3F6FA),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFE6EDF4),
    accent = Color(0xFF0E8C74),
    accentDark = Color(0xFFCDEFE6),
    blue = Color(0xFF2A5FBF),
    textPrimary = Color(0xFF121A24),
    textSecondary = Color(0xFF51617A),
    divider = Color(0xFFC9D4E2),
    warn = Color(0xFF9A6400),
    error = Color(0xFFB3261E),
)

val LocalZmPalette = staticCompositionLocalOf { ZmDarkPalette }

/** Доступ к цветам текущей темы: ZmColors.accent, ZmColors.surface и т.д. */
object ZmColors {
    val background: Color @Composable get() = LocalZmPalette.current.background
    val surface: Color @Composable get() = LocalZmPalette.current.surface
    val surfaceHigh: Color @Composable get() = LocalZmPalette.current.surfaceHigh
    val accent: Color @Composable get() = LocalZmPalette.current.accent
    val accentDark: Color @Composable get() = LocalZmPalette.current.accentDark
    val blue: Color @Composable get() = LocalZmPalette.current.blue
    val textPrimary: Color @Composable get() = LocalZmPalette.current.textPrimary
    val textSecondary: Color @Composable get() = LocalZmPalette.current.textSecondary
    val divider: Color @Composable get() = LocalZmPalette.current.divider
    val warn: Color @Composable get() = LocalZmPalette.current.warn
    val error: Color @Composable get() = LocalZmPalette.current.error
}

private fun darkScheme(p: ZmPalette) = darkColorScheme(
    primary = p.accent,
    onPrimary = Color(0xFF04231C),
    primaryContainer = p.accentDark,
    onPrimaryContainer = p.accent,
    secondary = p.blue,
    onSecondary = Color(0xFF04142B),
    tertiary = p.warn,
    background = p.background,
    onBackground = p.textPrimary,
    surface = p.surface,
    onSurface = p.textPrimary,
    surfaceVariant = p.surfaceHigh,
    onSurfaceVariant = p.textSecondary,
    outline = p.divider,
    error = p.error,
)

private fun lightScheme(p: ZmPalette) = lightColorScheme(
    primary = p.accent,
    onPrimary = Color.White,
    primaryContainer = p.accentDark,
    onPrimaryContainer = Color(0xFF06251F),
    secondary = p.blue,
    onSecondary = Color.White,
    tertiary = p.warn,
    background = p.background,
    onBackground = p.textPrimary,
    surface = p.surface,
    onSurface = p.textPrimary,
    surfaceVariant = p.surfaceHigh,
    onSurfaceVariant = p.textSecondary,
    outline = p.divider,
    error = p.error,
)

private val ZmTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp,
    ),
)

@Composable
fun ZapretManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) ZmDarkPalette else ZmLightPalette
    CompositionLocalProvider(LocalZmPalette provides palette) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkScheme(palette) else lightScheme(palette),
            typography = ZmTypography,
            content = content,
        )
    }
}
