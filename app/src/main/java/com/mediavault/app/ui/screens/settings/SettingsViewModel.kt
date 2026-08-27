package com.mediavault.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediavault.app.BuildConfig
import com.mediavault.app.player.PlayerPreferences
import com.mediavault.app.player.PlayerPreferencesProvider
import com.mediavault.app.player.SubtitleStyle
import com.mediavault.app.player.SubtitleStyleProvider
import com.mediavault.app.policy.NetworkPolicySettings
import com.mediavault.app.policy.NetworkPolicyStore
import com.mediavault.app.security.AppLockSettings
import com.mediavault.app.security.AppLockSettingsStore
import com.mediavault.app.security.AutoLockTimeout
import com.mediavault.app.security.BiometricAuthenticator
import com.mediavault.app.security.PinCredentialStore
import com.mediavault.app.util.DeviceStatusProvider
import com.mediavault.core.domain.source.SourceCatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val networkPolicy: NetworkPolicySettings = NetworkPolicySettings(
        mobileDownloadsEnabled = true,
        perDownloadLimitBytes = 500L * 1024 * 1024,
        dailyBudgetBytes = 2L * 1024 * 1024 * 1024,
    ),
    val mobileBytesUsedToday: Long = 0L,
    val playerPreferences: PlayerPreferences = PlayerPreferences(),
    val subtitleStyle: SubtitleStyle = SubtitleStyle.CLEAN,
    val freeStorageBytes: Long = 0L,
    val appVersionName: String = "",
    val appVersionCode: Int = 0,
    /** Null until the (locally-bundled, non-network) source catalog finishes its one-time load. */
    val extractionEngineVersion: String? = null,
    val appLockSettings: AppLockSettings = AppLockSettings(),
    val pinIsSet: Boolean = false,
    val biometricAvailable: Boolean = false,
)

/**
 * Aggregates every Settings section *except* Appearance — theme stays owned by the existing
 * [com.mediavault.app.settings.ThemeViewModel] (shared with `MainActivity`), injected directly
 * by `SettingsScreen` per this milestone's "no duplicate theme logic" requirement. Every store
 * this reads from/writes to is a pre-existing (or newly added, but singular) domain store —
 * network policy, player preferences, subtitle style — never a second copy of any of them.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val networkPolicyStore: NetworkPolicyStore,
    private val playerPreferencesProvider: PlayerPreferencesProvider,
    private val subtitleStyleProvider: SubtitleStyleProvider,
    private val appLockSettingsStore: AppLockSettingsStore,
    private val pinCredentialStore: PinCredentialStore,
    biometricAuthenticator: BiometricAuthenticator,
    deviceStatusProvider: DeviceStatusProvider,
    sourceCatalogRepository: SourceCatalogRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE,
            freeStorageBytes = deviceStatusProvider.freeStorageBytes(),
            biometricAvailable = biometricAuthenticator.isAvailable(),
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            networkPolicyStore.settings.collect { settings -> _uiState.update { it.copy(networkPolicy = settings) } }
        }
        viewModelScope.launch {
            val usedToday = networkPolicyStore.mobileBytesUsedToday()
            _uiState.update { it.copy(mobileBytesUsedToday = usedToday) }
        }
        viewModelScope.launch {
            playerPreferencesProvider.preferences.collect { prefs -> _uiState.update { it.copy(playerPreferences = prefs) } }
        }
        viewModelScope.launch {
            subtitleStyleProvider.subtitleStyle.collect { style -> _uiState.update { it.copy(subtitleStyle = style) } }
        }
        viewModelScope.launch {
            appLockSettingsStore.settings.collect { settings -> _uiState.update { it.copy(appLockSettings = settings) } }
        }
        viewModelScope.launch {
            val pinIsSet = pinCredentialStore.isPinSet()
            _uiState.update { it.copy(pinIsSet = pinIsSet) }
        }
        viewModelScope.launch {
            // The catalog is a bundled asset, not a network call (see SourceCatalogRepository's
            // KDoc) — this can't fail from a connectivity standpoint, but stays defensive since
            // it's still I/O/parsing on first load.
            val engineVersion = runCatching { sourceCatalogRepository.getCatalog().metadata.engineVersion }.getOrNull()
            _uiState.update { it.copy(extractionEngineVersion = engineVersion) }
        }
    }

    // --- Network & Mobile Data ----------------------------------------------------------------

    fun setMobileDownloadsEnabled(enabled: Boolean) {
        viewModelScope.launch { networkPolicyStore.setMobileDownloadsEnabled(enabled) }
    }

    fun setPerDownloadLimitBytes(bytes: Long) {
        viewModelScope.launch { networkPolicyStore.setPerDownloadLimitBytes(bytes) }
    }

    fun setDailyBudgetBytes(bytes: Long) {
        viewModelScope.launch { networkPolicyStore.setDailyBudgetBytes(bytes) }
    }

    // --- Player ---------------------------------------------------------------------------

    fun setDefaultPlaybackSpeed(speed: Float) {
        viewModelScope.launch { playerPreferencesProvider.setDefaultPlaybackSpeed(speed) }
    }

    fun setResumePlaybackEnabled(enabled: Boolean) {
        viewModelScope.launch { playerPreferencesProvider.setResumePlaybackEnabled(enabled) }
    }

    fun setAutoFullscreenLandscape(enabled: Boolean) {
        viewModelScope.launch { playerPreferencesProvider.setAutoFullscreenLandscape(enabled) }
    }

    fun setAutoEnterPip(enabled: Boolean) {
        viewModelScope.launch { playerPreferencesProvider.setAutoEnterPip(enabled) }
    }

    fun setAutoAdvancePlaylist(enabled: Boolean) {
        viewModelScope.launch { playerPreferencesProvider.setAutoAdvancePlaylist(enabled) }
    }

    // --- Subtitles --------------------------------------------------------------------------

    /** Same [SubtitleStyleProvider] the Player screen's own Subtitles menu writes to — one store, two entry points. */
    fun setSubtitleStyle(style: SubtitleStyle) {
        viewModelScope.launch { subtitleStyleProvider.setSubtitleStyle(style) }
    }

    // --- Security / App Lock ----------------------------------------------------------------

    /**
     * Called only after the UI has already collected and confirmed a new PIN (see the
     * Security section's setup dialog) — this is the one path that actually turns App Lock on,
     * since enabling it with no PIN would leave the lock screen with nothing to verify against.
     */
    fun enableAppLockWithNewPin(pin: CharArray) {
        viewModelScope.launch {
            pinCredentialStore.setPin(pin)
            appLockSettingsStore.setAppLockEnabled(true)
            _uiState.update { it.copy(pinIsSet = true) }
        }
    }

    /**
     * Disabling clears the stored PIN and biometric toggle along with the feature itself —
     * re-enabling later always starts from a fresh PIN setup, the same predictable state as
     * a user who has never turned App Lock on, rather than silently keeping an old PIN alive
     * for a feature that's supposedly off.
     */
    fun disableAppLock() {
        viewModelScope.launch {
            appLockSettingsStore.setAppLockEnabled(false)
            appLockSettingsStore.setBiometricEnabled(false)
            pinCredentialStore.clearPin()
            _uiState.update { it.copy(pinIsSet = false) }
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { appLockSettingsStore.setBiometricEnabled(enabled) }
    }

    fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        viewModelScope.launch { appLockSettingsStore.setAutoLockTimeout(timeout) }
    }

    suspend fun verifyCurrentPin(pin: CharArray): Boolean = pinCredentialStore.verifyPin(pin)

    /** Used by the Change PIN flow once the current PIN has already been verified. */
    fun changePin(newPin: CharArray) {
        viewModelScope.launch { pinCredentialStore.setPin(newPin) }
    }
}
