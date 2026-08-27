package com.mediavault.app.ui.components.support

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.mediavault.app.R
import com.mediavault.app.ui.components.MediaVaultCard
import com.mediavault.app.ui.components.SectionLabel

/**
 * Self-contained "Support the Project" UI: an explanation, UPI copy/QR/pay-by-any-app, and an
 * external Buy Me a Coffee link — everything it needs comes from [config], so a future app
 * reuses this composable unmodified by passing its own [com.mediavault.app.support.SupportProjectConfig].
 * No payment SDK, no stored credentials, no donation tracking: the UPI action is a plain
 * `ACTION_VIEW` intent on a standard `upi://pay` URI (the user's own UPI app handles the rest),
 * and Buy Me a Coffee is a plain external browser link.
 */
@Composable
fun SupportSection(config: com.mediavault.app.support.SupportProjectConfig, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var copyConfirmed by remember { mutableStateOf(false) }
    var upiAppMissing by remember { mutableStateOf(false) }

    val upiUri = remember(config) { buildUpiPayUri(config.upiId, config.upiPayeeName) }
    val qrBitmap = remember(upiUri) { generateQrCodeBitmap(upiUri) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(text = stringResource(R.string.settings_support_section))
        MediaVaultCard {
            Text(
                text = stringResource(R.string.settings_support_explanation, config.projectName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(text = stringResource(R.string.settings_support_upi_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = config.upiId, style = MaterialTheme.typography.bodyLarge)
                }
                IconButton(onClick = {
                    clipboardManager.setText(AnnotatedString(config.upiId))
                    copyConfirmed = true
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.settings_support_copy_upi))
                }
            }
            if (copyConfirmed) {
                Text(
                    text = stringResource(R.string.settings_support_copied),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.settings_support_qr_description),
                    modifier = Modifier
                        .size(96.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .padding(6.dp),
                )
                Column {
                    Icon(Icons.Default.QrCode2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = stringResource(R.string.settings_support_qr_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(
                onClick = {
                    upiAppMissing = try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(upiUri)))
                        false
                    } catch (_: ActivityNotFoundException) {
                        true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(stringResource(R.string.settings_support_pay_upi))
            }
            if (upiAppMissing) {
                Text(
                    text = stringResource(R.string.settings_support_no_upi_app),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            OutlinedButton(
                onClick = { openExternalUrl(context, config.buyMeACoffeeUrl) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(text = "  " + stringResource(R.string.settings_support_buy_me_a_coffee))
            }
            Text(
                text = stringResource(R.string.settings_support_external_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun buildUpiPayUri(upiId: String, payeeName: String): String {
    val encodedName = Uri.encode(payeeName)
    return "upi://pay?pa=$upiId&pn=$encodedName&cu=INR"
}

/** Shared by Support/Updates/About/Legal rows — every external browser link goes through this one call. */
fun openExternalUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        // No browser resolves ACTION_VIEW — nothing meaningful to recover into; the row's own
        // external-link icon already set the user's expectation correctly.
    }
}
