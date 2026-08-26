package com.mediavault.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.mediavault.app.settings.ThemeMode
import com.mediavault.app.settings.resolveIsDark

private val MediaVaultLightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = PrimaryBlueContainer,
    onPrimaryContainer = PrimaryBlueDark,
    secondary = PrimaryBlue,
    onSecondary = Color.White,
    background = BackgroundLight,
    onBackground = TextPrimary,
    surface = SurfaceLight,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondary,
    outline = OutlineLight,
    outlineVariant = OutlineLight,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorRedContainer,
    onErrorContainer = ErrorRed,
)

/** True/near-black AMOLED-friendly surfaces — see PROJECT_MASTER.md §37, 2026-08-26. */
private val MediaVaultDarkColorScheme = darkColorScheme(
    primary = PrimaryBlueOnDark,
    onPrimary = Color.Black,
    primaryContainer = PrimaryBlueContainerDark,
    onPrimaryContainer = PrimaryBlueOnDark,
    secondary = PrimaryBlueOnDark,
    onSecondary = Color.Black,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = OutlineDark,
    outlineVariant = OutlineDark,
    error = ErrorRedOnDark,
    onError = Color.Black,
    errorContainer = ErrorRedContainerDark,
    onErrorContainer = ErrorRedOnDark,
)

/**
 * MediaVault's approved design system: light surfaces with a blue primary accent, subtle
 * borders/elevation instead of glow or gradients (see PROJECT_MASTER.md §1 and §37) — plus a
 * true-dark/AMOLED-friendly counterpart, chosen per [themeMode]. Every screen/component reads
 * colors from [MaterialTheme.colorScheme] rather than these tokens directly, so switching the
 * scheme here re-themes the whole app with no per-screen changes needed.
 */
@Composable
fun MediaVaultTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val isDark = themeMode.resolveIsDark(isSystemInDarkTheme())
    MaterialTheme(
        colorScheme = if (isDark) MediaVaultDarkColorScheme else MediaVaultLightColorScheme,
        typography = MediaVaultTypography,
        content = content,
    )
}
