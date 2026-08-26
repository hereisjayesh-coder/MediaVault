package com.mediavault.app.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "theme_settings")

/** Persists the user's theme choice — same [androidx.datastore.preferences] approach already used for network policy settings. Defaults to [ThemeMode.SYSTEM] until the user picks something else. */
@Singleton
class DataStoreThemeStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : ThemeStore {

    private val themeModeKey = stringPreferencesKey("theme_mode")

    override val themeMode: Flow<ThemeMode> = context.themeDataStore.data.map { prefs -> prefs.toThemeMode() }

    override suspend fun currentThemeMode(): ThemeMode = context.themeDataStore.data.first().toThemeMode()

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.themeDataStore.edit { prefs -> prefs[themeModeKey] = mode.name }
    }

    private fun Preferences.toThemeMode(): ThemeMode =
        this[themeModeKey]?.let { stored -> runCatching { ThemeMode.valueOf(stored) }.getOrNull() } ?: ThemeMode.SYSTEM
}
