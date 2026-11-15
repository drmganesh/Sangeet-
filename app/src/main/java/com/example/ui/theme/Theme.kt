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
    GOLD("Golden Cassette", SangeetGold),
    AMBER("Neon Amber", NeonAmber),
    CYAN("Sangeet Cyan", SangeetCyan),
    VIOLET("Royal Violet", RoyalViolet),
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
    val secondaryGold = if (accent == AccentColor.GOLD || accent == AccentColor.AMBER) {
        SangeetGoldBright
    } else {
        accent.color.copy(alpha = 0.85f)
    }

    return if (isDark) {
        val bg = if (isAmoled) AmoledBlack else DarkBackground
        val surface = if (isAmoled) CassetteSurfaceDark else DarkSurface
        val surfaceContainer = if (isAmoled) CassetteCardSurface else DarkSurfaceElevated

        darkColorScheme(
            primary = accent.color,
            onPrimary = Color.Black,
            primaryContainer = accent.color.copy(alpha = 0.18f),
            onPrimaryContainer = accent.color,
            secondary = secondaryGold,
            onSecondary = Color.Black,
            secondaryContainer = accent.color.copy(alpha = 0.14f),
            onSecondaryContainer = secondaryGold,
            background = bg,
            onBackground = TextPrimaryDark,
            surface = surface,
            onSurface = TextPrimaryDark,
            surfaceVariant = surfaceContainer,
            onSurfaceVariant = TextSecondaryDark,
            outline = if (isAmoled) CassetteBorderGold else DarkSurfaceBorder
        )
    } else {
        lightColorScheme(
            primary = accent.color,
            onPrimary = Color.Black,
            primaryContainer = accent.color.copy(alpha = 0.15f),
            onPrimaryContainer = Color.Black,
            secondary = accent.color.copy(alpha = 0.8f),
            onSecondary = Color.Black,
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
    accentColor: AccentColor = AccentColor.GOLD,
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
