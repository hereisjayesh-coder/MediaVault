package com.mediavault.app.ui.components.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mediavault.app.R

/** Filled/empty dot row showing how many of [total] PIN digits have been entered so far — shared by the lock screen and Settings' PIN setup/verify/change dialogs. */
@Composable
fun PinDots(filledCount: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(total) { index ->
            val filled = index < filledCount
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(
                        color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    ),
            )
        }
    }
}

/** A 3x4 numeric keypad (1-9, optional biometric key, 0, backspace) — [onBiometricTapped]/[showBiometricKey] are null/false wherever biometric unlock isn't relevant (Settings' setup/verify/change dialogs). */
@Composable
fun PinNumericKeypad(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    showBiometricKey: Boolean = false,
    onBiometricTapped: (() -> Unit)? = null,
) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { digit -> PinKeypadKey(text = digit.toString(), enabled = enabled, onClick = { onDigit(digit) }) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                if (showBiometricKey && onBiometricTapped != null) {
                    IconButton(onClick = onBiometricTapped, enabled = enabled) {
                        Icon(
                            Icons.Default.Fingerprint,
                            contentDescription = stringResource(R.string.app_lock_biometric_action),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
            PinKeypadKey(text = "0", enabled = enabled, onClick = { onDigit('0') })
            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                IconButton(onClick = onBackspace, enabled = enabled) {
                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = stringResource(R.string.app_lock_backspace))
                }
            }
        }
    }
}

@Composable
private fun PinKeypadKey(text: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(64.dp)) {
        Text(text = text, style = MaterialTheme.typography.headlineSmall)
    }
}
