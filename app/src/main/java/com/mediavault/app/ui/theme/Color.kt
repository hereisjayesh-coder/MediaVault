package com.mediavault.app.ui.theme

import androidx.compose.ui.graphics.Color

// Approved MediaVault design system: light, white/light-gray surfaces, blue primary accent.
// See PROJECT_MASTER.md §37 (Decision Log) for why this replaced the earlier AMOLED-dark theme.

val BackgroundLight = Color(0xFFF5F6FA)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFF0F2F8)
val OutlineLight = Color(0xFFE3E6EE)

val PrimaryBlue = Color(0xFF2F6FEB)
val PrimaryBlueDark = Color(0xFF1D4FC4)
val PrimaryBlueContainer = Color(0xFFE8EFFE)

val TextPrimary = Color(0xFF14161F)
val TextSecondary = Color(0xFF6B7280)
val TextTertiary = Color(0xFF9AA0AC)

val SuccessGreen = Color(0xFF1FA556)
val SuccessGreenContainer = Color(0xFFE6F7ED)

val ErrorRed = Color(0xFFDC2626)
val ErrorRedContainer = Color(0xFFFCE9E9)

// Dark variant — true/near-black AMOLED-friendly surfaces (see PROJECT_MASTER.md §37,
// 2026-08-26). Selectable via Settings alongside Light; not a replacement for the
// approved light/blue identity, which stays the default.

val BackgroundDark = Color(0xFF000000)
val SurfaceDark = Color(0xFF121318)
val SurfaceVariantDark = Color(0xFF1E2028)
val OutlineDark = Color(0xFF2C2E38)

// A lighter blue tone than PrimaryBlue — Material's own dark-theme guidance calls for a
// lighter, less saturated tone of the brand color so it keeps AA contrast on near-black
// surfaces instead of looking muddy.
val PrimaryBlueOnDark = Color(0xFF7FA6FF)
val PrimaryBlueContainerDark = Color(0xFF1D3A6B)

val TextPrimaryDark = Color(0xFFF2F3F7)
val TextSecondaryDark = Color(0xFFA7ACB9)

val ErrorRedOnDark = Color(0xFFFF6B6B)
val ErrorRedContainerDark = Color(0xFF4A1414)
