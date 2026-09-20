package com.elejar.ZentraDL.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Fallback seeds (skill §2): light 0xFF2E6BE6, dark 0xFF90B4FF. */

/** Six accents offered when dynamic color is OFF. */
enum class Accent(val label: String, val light: Color, val dark: Color) {
    Blue("Blue", Color(0xFF2E6BE6), Color(0xFF90B4FF)),
    Green("Green", Color(0xFF2E7D32), Color(0xFF81C784)),
    Teal("Teal", Color(0xFF00796B), Color(0xFF4DD0C4)),
    Purple("Purple", Color(0xFF6750A4), Color(0xFFD0BCFF)),
    Orange("Orange", Color(0xFF9C4A00), Color(0xFFFFB871)),
    Pink("Pink", Color(0xFFB3261E), Color(0xFFF2B8B5)),
}

fun fallbackLightScheme(accent: Accent = Accent.Blue) = lightColorScheme(
    primary = accent.light,
    onPrimary = Color.White,
    primaryContainer = when (accent) {
        Accent.Blue -> Color(0xFFDCE6FF)
        Accent.Green -> Color(0xFFC9F0C9)
        Accent.Teal -> Color(0xFFB8F0E8)
        Accent.Purple -> Color(0xFFEADDFF)
        Accent.Orange -> Color(0xFFFFDDBB)
        Accent.Pink -> Color(0xFFFFDAD6)
    },
)

fun fallbackDarkScheme(accent: Accent = Accent.Blue) = darkColorScheme(
    primary = accent.dark,
    primaryContainer = when (accent) {
        Accent.Blue -> Color(0xFF00408A)
        Accent.Green -> Color(0xFF1B5E20)
        Accent.Teal -> Color(0xFF005046)
        Accent.Purple -> Color(0xFF4F378B)
        Accent.Orange -> Color(0xFF5F2F00)
        Accent.Pink -> Color(0xFF93000A)
    },
)

/** AMOLED = dark scheme with true-black surfaces. */
fun amoledScheme(accent: Accent = Accent.Blue) = fallbackDarkScheme(accent).copy(
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

/**
 * Semantic status colors. ALWAYS paired with an icon + label (never color alone).
 * Downloading/Queued use theme roles; the rest are fixed harmonized hues.
 */
@Immutable
data class StatusColors(
    val downloading: Color,
    val queued: Color,
    val paused: Color,
    val completed: Color,
    val failed: Color,
    val seeding: Color,
    val checking: Color,
)

fun lightStatusColors(scheme: ColorScheme) = StatusColors(
    downloading = scheme.primary,
    queued = scheme.secondary,
    paused = scheme.tertiary,
    completed = Color(0xFF2E7D32),
    failed = scheme.error,
    seeding = Color(0xFF00796B),
    checking = Color(0xFF8A6D00),
)

fun darkStatusColors(scheme: ColorScheme) = StatusColors(
    downloading = scheme.primary,
    queued = scheme.secondary,
    paused = scheme.tertiary,
    completed = Color(0xFF81C784),
    failed = scheme.error,
    seeding = Color(0xFF4DD0C4),
    checking = Color(0xFFFFD661),
)

val LocalStatusColors = staticCompositionLocalOf<StatusColors> {
    error("StatusColors not provided")
}
