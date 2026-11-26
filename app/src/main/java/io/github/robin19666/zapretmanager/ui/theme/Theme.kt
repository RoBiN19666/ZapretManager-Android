package io.github.robin19666.zapretmanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val ZmBackground = Color(0xFF0B1017)
val ZmSurface = Color(0xFF121A24)
val ZmSurfaceHigh = Color(0xFF18222F)
val ZmAccent = Color(0xFF31D0AA)
val ZmAccentDark = Color(0xFF0F3B31)
val ZmBlue = Color(0xFF4C8DFF)
val ZmWarn = Color(0xFFFFC857)
val ZmError = Color(0xFFFF6B6B)
val ZmTextPrimary = Color(0xFFE6EDF5)
val ZmTextSecondary = Color(0xFF93A4B8)
val ZmDivider = Color(0xFF1F2A38)

private val DarkColors = darkColorScheme(
    primary = ZmAccent,
    onPrimary = Color(0xFF04231C),
    primaryContainer = ZmAccentDark,
    onPrimaryContainer = ZmAccent,
    secondary = ZmBlue,
    onSecondary = Color(0xFF04142B),
    background = ZmBackground,
    onBackground = ZmTextPrimary,
    surface = ZmSurface,
    onSurface = ZmTextPrimary,
    surfaceVariant = ZmSurfaceHigh,
    onSurfaceVariant = ZmTextSecondary,
    outline = ZmDivider,
    error = ZmError,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E8C74),
    onPrimary = Color.White,
    secondary = Color(0xFF2A5FBF),
    background = Color(0xFFF4F7FA),
    onBackground = Color(0xFF101820),
    surface = Color.White,
    onSurface = Color(0xFF101820),
    surfaceVariant = Color(0xFFE7EDF3),
    onSurfaceVariant = Color(0xFF4B5A6B),
    outline = Color(0xFFCBD5E0),
    error = Color(0xFFB3261E),
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
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ZmTypography,
        content = content,
    )
}
