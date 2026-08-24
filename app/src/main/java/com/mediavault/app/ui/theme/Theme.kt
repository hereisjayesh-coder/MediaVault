package com.mediavault.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

/**
 * MediaVault's approved design system: light surfaces with a blue primary accent, subtle
 * borders/elevation instead of glow or gradients. See PROJECT_MASTER.md §1 and §37.
 */
@Composable
fun MediaVaultTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = MediaVaultLightColorScheme,
        typography = MediaVaultTypography,
        content = content,
    )
}
