package com.mediavault.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val MediaVaultDarkColorScheme = darkColorScheme(
    primary = AccentTeal,
    onPrimary = AmoledBlack,
    primaryContainer = AccentTealMuted,
    onPrimaryContainer = TextPrimary,
    secondary = AccentTeal,
    onSecondary = AmoledBlack,
    background = AmoledBlack,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary,
    outline = OutlineDark,
    error = ErrorRed,
)

/**
 * MediaVault only ships a minimal AMOLED-black theme: light mode is intentionally
 * not offered as part of the product design.
 */
@Composable
fun MediaVaultTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = MediaVaultDarkColorScheme,
        typography = MediaVaultTypography,
        content = content,
    )
}
