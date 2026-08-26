package com.mediavault.app.settings

import kotlinx.coroutines.flow.MutableStateFlow

/** Test double for [ThemeStore] — an in-memory value instead of real DataStore/disk I/O. */
class FakeThemeStore(initial: ThemeMode = ThemeMode.SYSTEM) : ThemeStore {

    private val state = MutableStateFlow(initial)

    override val themeMode = state

    override suspend fun currentThemeMode(): ThemeMode = state.value

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.value = mode
    }
}
