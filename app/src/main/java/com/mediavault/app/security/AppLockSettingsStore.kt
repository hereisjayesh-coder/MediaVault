package com.mediavault.app.security

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appLockDataStore by preferencesDataStore(name = "app_lock_settings")

/** How long the app can sit backgrounded before the next foreground re-requires authentication. */
enum class AutoLockTimeout(val seconds: Long) {
    IMMEDIATE(0L),
    THIRTY_SECONDS(30L),
    ONE_MINUTE(60L),
    FIVE_MINUTES(300L),
}

/**
 * The user-editable App Lock settings — everything here is non-secret (a feature toggle, a
 * timeout choice), unlike the PIN verifier itself which lives in [PinCredentialStore]. See
 * [AppLockSettingsStore].
 */
data class AppLockSettings(
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.IMMEDIATE,
)

/**
 * Persists App Lock's non-secret settings — one DataStore file, same one-store-per-concern
 * convention as [com.mediavault.app.player.PlayerPreferencesStore]/`NetworkPolicyStore`. The PIN
 * verifier deliberately does NOT live here: it goes through [PinCredentialStore]'s
 * Keystore-backed `EncryptedSharedPreferences` instead, since plain DataStore has no
 * encryption-at-rest.
 */
interface AppLockSettingsStore {
    val settings: Flow<AppLockSettings>
    suspend fun currentSettings(): AppLockSettings
    suspend fun setAppLockEnabled(enabled: Boolean)
    suspend fun setBiometricEnabled(enabled: Boolean)
    suspend fun setAutoLockTimeout(timeout: AutoLockTimeout)
}

@Singleton
class DataStoreAppLockSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppLockSettingsStore {

    private val appLockEnabledKey = booleanPreferencesKey("app_lock_enabled")
    private val biometricEnabledKey = booleanPreferencesKey("biometric_enabled")
    private val autoLockTimeoutKey = stringPreferencesKey("auto_lock_timeout")

    override val settings: Flow<AppLockSettings> = context.appLockDataStore.data.map { it.toAppLockSettings() }

    override suspend fun currentSettings(): AppLockSettings =
        context.appLockDataStore.data.first().toAppLockSettings()

    override suspend fun setAppLockEnabled(enabled: Boolean) {
        context.appLockDataStore.edit { it[appLockEnabledKey] = enabled }
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        context.appLockDataStore.edit { it[biometricEnabledKey] = enabled }
    }

    override suspend fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        context.appLockDataStore.edit { it[autoLockTimeoutKey] = timeout.name }
    }

    private fun Preferences.toAppLockSettings() = AppLockSettings(
        appLockEnabled = this[appLockEnabledKey] ?: false,
        biometricEnabled = this[biometricEnabledKey] ?: false,
        autoLockTimeout = this[autoLockTimeoutKey]
            ?.let { stored -> runCatching { AutoLockTimeout.valueOf(stored) }.getOrNull() }
            ?: AutoLockTimeout.IMMEDIATE,
    )
}
