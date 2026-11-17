package com.urbext.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.urbext.domain.model.ReliabilityStatus

object UrbexColors {
    val Background = Color(0xFF0C0F0E)
    val Surface = Color(0xFF131816)
    val Elevated = Color(0xFF1A201E)
    val Border = Color(0xFF252E2A)
    val BorderStrong = Color(0xFF323E39)

    val TextPrimary = Color(0xFFE6EBE8)
    val TextSecondary = Color(0xFF98A49E)
    val TextDim = Color(0xFF6B7771)

    val Green = Color(0xFF5B9D72)
    val GreenBright = Color(0xFF6DB386)
    val GreenSoft = Color(0xFF203328)
    val Amber = Color(0xFFD39239)
    val AmberSoft = Color(0xFF362816)
    val Danger = Color(0xFFC7564E)
    val DangerSoft = Color(0xFF381B19)
}

fun ReliabilityStatus.color(): Color = when (this) {
    ReliabilityStatus.VERIFIED -> UrbexColors.GreenBright
    ReliabilityStatus.UNVERIFIED -> UrbexColors.Amber
    ReliabilityStatus.CONTESTED -> Color(0xFFB9A46B)
    ReliabilityStatus.HAZARDOUS -> UrbexColors.Danger
}

private val UrbexDarkColorScheme = darkColorScheme(
    primary = UrbexColors.Green,
    onPrimary = UrbexColors.Background,
    secondary = UrbexColors.Amber,
    onSecondary = UrbexColors.Background,
    error = UrbexColors.Danger,
    onError = UrbexColors.Background,
    background = UrbexColors.Background,
    onBackground = UrbexColors.TextPrimary,
    surface = UrbexColors.Surface,
    onSurface = UrbexColors.TextPrimary,
    surfaceVariant = UrbexColors.Elevated,
    onSurfaceVariant = UrbexColors.TextSecondary,
    outline = UrbexColors.Border
)

private val UrbexTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

@Composable
fun UrbexTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = UrbexDarkColorScheme,
        typography = UrbexTypography,
        content = content
    )
}
