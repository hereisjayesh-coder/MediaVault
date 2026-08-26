package com.mediavault.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Shared by [com.mediavault.app.MainActivity] (drives [com.mediavault.app.ui.theme.MediaVaultTheme]
 * for the whole app) and the Settings screen's theme picker. Every instance — regardless of which
 * [androidx.lifecycle.ViewModelStoreOwner] created it — reflects the same persisted value, since
 * both read through the single [ThemeStore] singleton.
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themeStore: ThemeStore,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = themeStore.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ThemeMode.SYSTEM,
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { themeStore.setThemeMode(mode) }
    }
}
