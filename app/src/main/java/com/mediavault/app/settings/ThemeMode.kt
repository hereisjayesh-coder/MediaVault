package com.mediavault.app.settings

/** User-selectable global theme — applied consistently to every screen via [com.mediavault.app.ui.theme.MediaVaultTheme]. */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
}

/** Resolves this preference against the current system dark-mode state — the only place that decision is made, so it can't drift between the startup (pre-Compose) and in-composition resolution paths. */
fun ThemeMode.resolveIsDark(systemInDark: Boolean): Boolean = when (this) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> systemInDark
}
