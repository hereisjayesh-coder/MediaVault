package com.mediavault.app.settings

import kotlinx.coroutines.flow.Flow

/** Sole owner of the persisted theme preference — see [com.mediavault.app.settings.DataStoreThemeStore] for the real, DataStore-backed implementation. */
interface ThemeStore {
    val themeMode: Flow<ThemeMode>
    suspend fun currentThemeMode(): ThemeMode
    suspend fun setThemeMode(mode: ThemeMode)
}
