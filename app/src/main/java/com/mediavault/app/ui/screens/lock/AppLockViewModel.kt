package com.mediavault.app.ui.screens.lock

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediavault.app.security.AppLockManager
import com.mediavault.app.security.BiometricAuthenticator
import com.mediavault.app.security.PIN_LENGTH
import com.mediavault.app.security.PinCredentialStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * App Lock uses a fixed 4-digit PIN — not a variable length like Android's own device lock —
 * specifically so this screen can auto-submit the instant the 4th digit lands, with no separate
 * "done" action. A variable-length PIN would make that auto-submit ambiguous (has the user
 * finished a 4-digit PIN, or paused partway through a 6-digit one?); a fixed length removes the
 * ambiguity entirely, at the cost of not letting a user choose a longer PIN.
 */
data class AppLockUiState(
    val enteredDigits: Int = 0,
    val showIncorrectPinError: Boolean = false,
    val biometricAvailable: Boolean = false,
    val biometricEnabled: Boolean = false,
    /** A system-provided, already-localized [androidx.biometric.BiometricPrompt] error string — not app copy, so it's safe to render as-is. */
    val biometricErrorMessage: String? = null,
    val lockoutSecondsRemaining: Long = 0,
) {
    val isLockedOut: Boolean get() = lockoutSecondsRemaining > 0
}

/**
 * Owns only the lock screen's own input/error/lockout state — the actual "is the app locked"
 * decision stays in [AppLockManager], which this reads from and reports success/failure back to.
 * No Compose/string-resource dependency here; the screen resolves display text itself.
 */
@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val appLockManager: AppLockManager,
    private val pinCredentialStore: PinCredentialStore,
    private val biometricAuthenticator: BiometricAuthenticator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppLockUiState())
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()

    private val enteredPin = StringBuilder()
    private var lockoutCountdownJob: Job? = null

    init {
        viewModelScope.launch {
            appLockManager.lockoutState.collect {
                val remaining = appLockManager.remainingLockoutSeconds()
                _uiState.update { state -> state.copy(lockoutSecondsRemaining = remaining) }
                if (remaining > 0) startLockoutCountdown()
            }
        }
    }

    /** Reads current biometric availability/settings once per lock-screen appearance — cheap, and avoids a continuous collector for a value that only changes from Settings, which this screen isn't showing. */
    fun refreshBiometricState(biometricEnabledSetting: Boolean) {
        _uiState.update {
            it.copy(
                biometricAvailable = biometricAuthenticator.isAvailable(),
                biometricEnabled = biometricEnabledSetting,
            )
        }
    }

    fun onDigitEntered(digit: Char) {
        if (_uiState.value.isLockedOut || enteredPin.length >= PIN_LENGTH) return
        enteredPin.append(digit)
        _uiState.update { it.copy(enteredDigits = enteredPin.length, showIncorrectPinError = false) }
        if (enteredPin.length == PIN_LENGTH) submitPin()
    }

    fun onBackspace() {
        if (enteredPin.isEmpty()) return
        enteredPin.deleteCharAt(enteredPin.length - 1)
        _uiState.update { it.copy(enteredDigits = enteredPin.length, showIncorrectPinError = false) }
    }

    private fun submitPin() {
        val pinChars = enteredPin.toString().toCharArray()
        enteredPin.clear()
        viewModelScope.launch {
            val correct = pinCredentialStore.verifyPin(pinChars)
            if (correct) {
                appLockManager.unlock()
            } else {
                appLockManager.recordFailedAttempt()
                _uiState.update {
                    it.copy(
                        enteredDigits = 0,
                        showIncorrectPinError = true,
                        lockoutSecondsRemaining = appLockManager.remainingLockoutSeconds(),
                    )
                }
            }
        }
    }

    fun onBiometricRequested(activity: FragmentActivity, promptTitle: String, useAsPinFallbackLabel: String) {
        if (!_uiState.value.biometricEnabled || !_uiState.value.biometricAvailable || _uiState.value.isLockedOut) return
        _uiState.update { it.copy(biometricErrorMessage = null) }
        biometricAuthenticator.authenticate(
            activity = activity,
            title = promptTitle,
            negativeButtonText = useAsPinFallbackLabel,
            onSuccess = { appLockManager.unlock() },
            onError = { message -> _uiState.update { it.copy(biometricErrorMessage = message) } },
        )
    }

    private fun startLockoutCountdown() {
        lockoutCountdownJob?.cancel()
        lockoutCountdownJob = viewModelScope.launch {
            while (appLockManager.remainingLockoutSeconds() > 0) {
                delay(1_000)
                _uiState.update { it.copy(lockoutSecondsRemaining = appLockManager.remainingLockoutSeconds()) }
            }
        }
    }
}
