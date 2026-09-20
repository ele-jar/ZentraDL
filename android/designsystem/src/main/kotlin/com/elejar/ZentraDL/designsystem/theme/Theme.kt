package com.elejar.ZentraDL.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode { System, Light, Dark, Amoled }

/**
 * ZentraDL theme: dynamic color on Android 12+ (unless [dynamic] is off),
 * designed fallback + accent picker otherwise, AMOLED true-black option.
 */
@Composable
fun ZentraDLTheme(
    mode: ThemeMode = ThemeMode.System,
    accent: Accent = Accent.Blue,
    dynamic: Boolean = true,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (mode) {
        ThemeMode.Light -> false
        ThemeMode.Dark, ThemeMode.Amoled -> true
        ThemeMode.System -> systemDark
    }
    val useDynamic = dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = when {
        useDynamic -> {
            val ctx = LocalContext.current
            val base = if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            if (mode == ThemeMode.Amoled) base.copyAmoled() else base
        }
        mode == ThemeMode.Light -> fallbackLightScheme(accent)
        mode == ThemeMode.Amoled -> amoledScheme(accent)
        mode == ThemeMode.Dark -> fallbackDarkScheme(accent)
        darkTheme -> fallbackDarkScheme(accent)
        else -> fallbackLightScheme(accent)
    }
    CompositionLocalProvider(
        LocalStatusColors provides if (darkTheme) darkStatusColors(scheme) else lightStatusColors(scheme),
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

private fun ColorScheme.copyAmoled() = copy(
    surface = Color.Black,
    onSurface = Color(0xFFE6E6E6),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color(0xFF0A0A0A),
    surfaceContainerHigh = Color(0xFF141414),
    surfaceContainerHighest = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF141414),
    background = Color.Black,
    onBackground = Color(0xFFE6E6E6),
)
