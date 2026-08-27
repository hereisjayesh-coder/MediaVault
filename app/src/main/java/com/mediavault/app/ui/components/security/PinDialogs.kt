package com.mediavault.app.ui.components.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mediavault.app.R
import com.mediavault.app.security.PIN_LENGTH
import kotlinx.coroutines.launch

/**
 * Two-step "enter a new PIN, then enter it again to confirm" flow — used both when first turning
 * App Lock on and for the second half of Change PIN (after [PinVerifyDialog] confirms the old
 * one). A mismatch on step 2 clears just that step's entry, not the whole flow.
 */
@Composable
fun PinSetupDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirmed: (CharArray) -> Unit,
) {
    var firstEntry by remember { mutableStateOf<String?>(null) }
    var currentDigits by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (firstEntry == null) {
                        stringResource(R.string.app_lock_pin_setup_enter)
                    } else {
                        stringResource(R.string.app_lock_pin_setup_confirm)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                PinDots(filledCount = currentDigits.length, total = PIN_LENGTH)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (mismatch) stringResource(R.string.app_lock_pin_setup_mismatch) else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                PinNumericKeypad(
                    enabled = true,
                    onDigit = { digit ->
                        if (currentDigits.length >= PIN_LENGTH) return@PinNumericKeypad
                        mismatch = false
                        currentDigits += digit
                        if (currentDigits.length == PIN_LENGTH) {
                            val entered = currentDigits
                            currentDigits = ""
                            val pending = firstEntry
                            if (pending == null) {
                                firstEntry = entered
                            } else if (pending == entered) {
                                onConfirmed(entered.toCharArray())
                            } else {
                                mismatch = true
                                firstEntry = null
                            }
                        }
                    },
                    onBackspace = { if (currentDigits.isNotEmpty()) currentDigits = currentDigits.dropLast(1) },
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.app_lock_dialog_cancel)) }
        },
    )
}

/** Requires the existing PIN before a security-sensitive change (disabling App Lock, or the first step of Change PIN). */
@Composable
fun PinVerifyDialog(
    title: String,
    onDismiss: () -> Unit,
    onVerify: suspend (CharArray) -> Boolean,
    onVerified: () -> Unit,
) {
    var currentDigits by remember { mutableStateOf("") }
    var incorrect by remember { mutableStateOf(false) }
    var attemptToken by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = stringResource(R.string.app_lock_pin_verify_prompt), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                PinDots(filledCount = currentDigits.length, total = PIN_LENGTH)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (incorrect) stringResource(R.string.app_lock_incorrect_pin) else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                PinNumericKeypad(
                    enabled = true,
                    onDigit = { digit ->
                        if (currentDigits.length >= PIN_LENGTH) return@PinNumericKeypad
                        incorrect = false
                        currentDigits += digit
                        if (currentDigits.length == PIN_LENGTH) {
                            val entered = currentDigits.toCharArray()
                            currentDigits = ""
                            val token = ++attemptToken
                            scope.launch {
                                val correct = onVerify(entered)
                                if (token != attemptToken) return@launch // a newer attempt has already started
                                if (correct) onVerified() else incorrect = true
                            }
                        }
                    },
                    onBackspace = { if (currentDigits.isNotEmpty()) currentDigits = currentDigits.dropLast(1) },
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.app_lock_dialog_cancel)) }
        },
    )
}
