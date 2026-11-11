package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode {
    SYSTEM, DARK, AMOLED, LIGHT
}

enum class AccentColor(val displayName: String, val color: Color) {
    CYAN("Sangeet Cyan", SangeetCyan),
    VIOLET("Royal Violet", RoyalViolet),
    AMBER("Neon Amber", NeonAmber),
    EMERALD("Emerald Green", EmeraldGreen),
    ROSE("Electric Rose", ElectricRose)
}

fun createSangeetColorScheme(
    themeMode: ThemeMode,
    accent: AccentColor,
    isSystemDark: Boolean
): ColorScheme {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemDark
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.LIGHT -> false
    }

    val isAmoled = themeMode == ThemeMode.AMOLED

    return if (isDark) {
        val bg = if (isAmoled) AmoledBlack else DarkBackground
        val surface = if (isAmoled) Color(0xFF070707) else DarkSurface
        val surfaceContainer = if (isAmoled) Color(0xFF101010) else DarkSurfaceElevated

        darkColorScheme(
            primary = accent.color,
            onPrimary = Color.Black,
            primaryContainer = accent.color.copy(alpha = 0.2f),
            onPrimaryContainer = accent.color,
            secondary = accent.color.copy(alpha = 0.8f),
            onSecondary = Color.Black,
            background = bg,
            onBackground = TextPrimaryDark,
            surface = surface,
            onSurface = TextPrimaryDark,
            surfaceVariant = surfaceContainer,
            onSurfaceVariant = TextSecondaryDark,
            outline = if (isAmoled) Color(0xFF222222) else DarkSurfaceBorder
        )
    } else {
        lightColorScheme(
            primary = accent.color,
            onPrimary = Color.White,
            primaryContainer = accent.color.copy(alpha = 0.15f),
            onPrimaryContainer = Color.Black,
            secondary = accent.color.copy(alpha = 0.8f),
            onSecondary = Color.White,
            background = LightBackground,
            onBackground = TextPrimaryLight,
            surface = LightSurface,
            onSurface = TextPrimaryLight,
            surfaceVariant = LightSurfaceElevated,
            onSurfaceVariant = TextSecondaryLight,
            outline = Color(0xFFCBD5E1)
        )
    }
}

@Composable
fun SangeetTheme(
    themeMode: ThemeMode = ThemeMode.AMOLED,
    accentColor: AccentColor = AccentColor.CYAN,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val colorScheme = createSangeetColorScheme(themeMode, accentColor, isSystemDark)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
