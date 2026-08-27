package com.mediavault.app.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around `androidx.biometric` — the current recommended Android biometric API.
 * Deliberately restricted to [BIOMETRIC_STRONG] only (no device-credential/PIN-pattern fallback
 * through the OS prompt): MediaVault has its own PIN screen for the fallback path, so allowing
 * the OS's own device-credential prompt here would duplicate that with a second, inconsistent UI.
 *
 * This gates local UI access, not a cryptographic secret — the PIN verifier's actual protection
 * comes from [PinCredentialStore]'s Keystore-backed encryption, independent of whether biometric
 * succeeds. A [BiometricPrompt.CryptoObject] tied to a Keystore key would be the stronger,
 * "authorizes a specific decrypt" pattern; it's not used here because there's no secret being
 * decrypted by a successful biometric check — only a boolean unlock decision — and adding a
 * Keystore key with biometric invalidation handling would be meaningfully more code for a
 * cryptographic guarantee this feature doesn't need.
 */
@Singleton
class BiometricAuthenticator @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun isAvailable(): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        negativeButtonText: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // A user tapping "use PIN instead" or dismissing the system prompt isn't a
                    // failure worth surfacing as an error — it's the expected fallback path.
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_USER_CANCELED) return
                    onError(errString.toString())
                }
                // onAuthenticationFailed (e.g. an unrecognized fingerprint) is intentionally not
                // treated as terminal — BiometricPrompt keeps its own prompt open for retry.
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info)
    }
}
