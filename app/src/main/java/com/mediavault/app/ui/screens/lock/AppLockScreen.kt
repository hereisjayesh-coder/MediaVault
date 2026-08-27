package com.mediavault.app.ui.screens.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.mediavault.app.R
import com.mediavault.app.security.PIN_LENGTH
import com.mediavault.app.ui.components.MediaVaultLogo
import com.mediavault.app.ui.components.security.PinDots
import com.mediavault.app.ui.components.security.PinNumericKeypad

/**
 * The full-screen, opaque gate shown whenever [com.mediavault.app.security.AppLockManager.isLocked]
 * is true. Deliberately renders nothing from Library/Downloads/Player/Settings — no thumbnail,
 * title, or file detail of any kind ever reaches this composable, so there is nothing sensitive
 * for it to accidentally leak while locked.
 */
@Composable
fun AppLockScreen(
    biometricEnabledSetting: Boolean,
    viewModel: AppLockViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    val biometricPromptTitle = stringResource(R.string.app_lock_biometric_prompt_title)
    val usePinLabel = stringResource(R.string.app_lock_use_pin)

    // Keyed on the setting itself, not Unit: on a cold process start, MainActivity's
    // AppLockSettingsStore collection begins from `initialValue = null` (this parameter starts
    // `false`) and only reflects the real persisted value a moment later, once DataStore's first
    // emission arrives. A one-shot LaunchedEffect(Unit) would capture that initial `false` and
    // never re-sync, permanently missing the biometric auto-prompt after a real app restart —
    // re-running this whenever the parameter actually changes is what catches that late update.
    LaunchedEffect(biometricEnabledSetting) {
        viewModel.refreshBiometricState(biometricEnabledSetting)
    }
    LaunchedEffect(uiState.biometricEnabled, uiState.biometricAvailable) {
        if (activity != null && uiState.biometricEnabled && uiState.biometricAvailable && !uiState.isLockedOut) {
            viewModel.onBiometricRequested(activity, biometricPromptTitle, usePinLabel)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            MediaVaultLogo(size = 72.dp)
            Spacer(Modifier.height(16.dp))
            Text(text = stringResource(R.string.app_lock_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.app_lock_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))

            PinDots(filledCount = uiState.enteredDigits, total = PIN_LENGTH)
            Spacer(Modifier.height(16.dp))

            val statusText = when {
                uiState.isLockedOut -> stringResource(R.string.app_lock_locked_out, uiState.lockoutSecondsRemaining)
                uiState.showIncorrectPinError -> stringResource(R.string.app_lock_incorrect_pin)
                uiState.biometricErrorMessage != null -> uiState.biometricErrorMessage!!
                else -> ""
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.height(20.dp),
            )
            Spacer(Modifier.height(24.dp))

            PinNumericKeypad(
                enabled = !uiState.isLockedOut,
                onDigit = viewModel::onDigitEntered,
                onBackspace = viewModel::onBackspace,
                showBiometricKey = uiState.biometricEnabled && uiState.biometricAvailable,
                onBiometricTapped = {
                    if (activity != null) viewModel.onBiometricRequested(activity, biometricPromptTitle, usePinLabel)
                },
            )
        }
    }
}
