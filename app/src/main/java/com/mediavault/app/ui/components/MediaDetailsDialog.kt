package com.mediavault.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mediavault.app.R
import com.mediavault.app.ui.screens.home.formatDurationLabel
import com.mediavault.app.ui.screens.home.formatFileSizeLabel
import com.mediavault.core.database.entity.MediaItemEntity
import com.mediavault.core.model.MediaType
import java.text.DateFormat
import java.util.Date

/** Shared by the Library three-dot menu and the Player's "Media details" control — same facts, never invented. */
@Composable
fun MediaDetailsDialog(item: MediaItemEntity, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_details_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow(stringResource(R.string.library_details_type), if (item.mediaType == MediaType.AUDIO) "Audio" else "Video")
                formatDurationLabel(item.durationMs?.let { it / 1000 })?.let { DetailRow(stringResource(R.string.library_details_duration), it) }
                item.resolutionLabel?.let { DetailRow(stringResource(R.string.library_details_resolution), it) }
                formatFileSizeLabel(item.sizeBytes)?.let { DetailRow(stringResource(R.string.library_details_size), it) }
                item.container?.let { DetailRow(stringResource(R.string.library_details_format), it.uppercase()) }
                DetailRow(stringResource(R.string.library_details_downloaded), DateFormat.getDateTimeInstance().format(Date(item.addedAtEpochMs)))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_details_close)) }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value)
    }
}
