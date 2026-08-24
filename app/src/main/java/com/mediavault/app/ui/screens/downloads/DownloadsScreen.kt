package com.mediavault.app.ui.screens.downloads

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.webkit.MimeTypeMap
import coil3.compose.AsyncImage
import com.mediavault.app.R
import com.mediavault.app.ui.components.EmptyStateCard
import com.mediavault.app.ui.components.MediaVaultCard
import com.mediavault.app.ui.components.MediaVaultTopBar
import com.mediavault.app.ui.components.SectionLabel
import com.mediavault.app.ui.screens.home.formatFileSizeLabel
import com.mediavault.core.domain.download.DownloadProgress
import com.mediavault.core.domain.download.PlaylistProgress
import com.mediavault.core.model.DownloadStatus

@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    DownloadsScreenContent(
        uiState = uiState,
        onPause = viewModel::pause,
        onResume = viewModel::resume,
        onCancel = viewModel::cancel,
        onRetry = viewModel::retry,
        onPausePlaylist = viewModel::pausePlaylist,
        onCancelPlaylist = viewModel::cancelPlaylist,
        onRetryFailedInPlaylist = viewModel::retryFailedInPlaylist,
    )
}

@Composable
private fun DownloadsScreenContent(
    uiState: DownloadsUiState,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onPausePlaylist: (String) -> Unit,
    onCancelPlaylist: (String) -> Unit,
    onRetryFailedInPlaylist: (String) -> Unit,
) {
    if (uiState.tasks.isEmpty() && uiState.playlists.isEmpty()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { MediaVaultTopBar(title = stringResource(R.string.downloads_title)) }
            item {
                EmptyStateCard(
                    icon = Icons.Default.Download,
                    title = stringResource(R.string.downloads_empty_title),
                    description = stringResource(R.string.downloads_empty_body),
                )
            }
        }
        return
    }

    val active = uiState.tasks.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PROCESSING }
    val queued = uiState.tasks.filter { it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.PAUSED }
    val completed = uiState.tasks.filter { it.status == DownloadStatus.COMPLETED }
    val failed = uiState.tasks.filter { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { MediaVaultTopBar(title = stringResource(R.string.downloads_title)) }

        if (uiState.playlists.isNotEmpty()) {
            item { SectionLabel(text = stringResource(R.string.downloads_section_playlists)) }
            items(uiState.playlists, key = { it.playlistId }) { playlist ->
                PlaylistGroupCard(
                    playlist = playlist,
                    items = uiState.playlistTasksById[playlist.playlistId].orEmpty(),
                    onPause = onPause,
                    onResume = onResume,
                    onCancel = onCancel,
                    onRetry = onRetry,
                    onPausePlaylist = { onPausePlaylist(playlist.playlistId) },
                    onCancelPlaylist = { onCancelPlaylist(playlist.playlistId) },
                    onRetryFailedInPlaylist = { onRetryFailedInPlaylist(playlist.playlistId) },
                )
            }
        }

        downloadSection(R.string.downloads_section_active, active, onPause, onResume, onCancel, onRetry)
        downloadSection(R.string.downloads_section_queued, queued, onPause, onResume, onCancel, onRetry)
        downloadSection(R.string.downloads_section_failed, failed, onPause, onResume, onCancel, onRetry)
        downloadSection(R.string.downloads_section_completed, completed, onPause, onResume, onCancel, onRetry)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.downloadSection(
    titleRes: Int,
    tasks: List<DownloadProgress>,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
) {
    if (tasks.isEmpty()) return
    item { SectionLabel(text = stringResource(titleRes)) }
    items(tasks, key = { it.taskId }) { task ->
        DownloadTaskCard(task = task, onPause = onPause, onResume = onResume, onCancel = onCancel, onRetry = onRetry)
    }
}

@Composable
private fun PlaylistGroupCard(
    playlist: PlaylistProgress,
    items: List<DownloadProgress>,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onPausePlaylist: () -> Unit,
    onCancelPlaylist: () -> Unit,
    onRetryFailedInPlaylist: () -> Unit,
) {
    MediaVaultCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier
                    .width(64.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                if (playlist.playlistThumbnailUrl != null) {
                    AsyncImage(
                        model = playlist.playlistThumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = playlist.playlistTitle ?: stringResource(R.string.downloads_section_playlists),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                )
                Text(
                    text = stringResource(
                        R.string.downloads_playlist_counts,
                        playlist.completedCount,
                        playlist.totalCount,
                        playlist.failedCount,
                        playlist.remainingCount,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val currentItem = playlist.currentItemTitle
                if (currentItem != null) {
                    Text(
                        text = stringResource(R.string.downloads_playlist_current_item, currentItem),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        val overallFraction = playlist.totalCount.takeIf { it > 0 }
            ?.let { total -> (playlist.completedCount.toFloat() / total).coerceIn(0f, 1f) }
        if (overallFraction != null) {
            LinearProgressIndicator(progress = { overallFraction }, modifier = Modifier.fillMaxWidth())
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPausePlaylist) { Text(stringResource(R.string.downloads_action_pause_all)) }
            OutlinedButton(onClick = onCancelPlaylist) { Text(stringResource(R.string.downloads_action_cancel_all)) }
            if (playlist.failedCount > 0) {
                OutlinedButton(onClick = onRetryFailedInPlaylist) { Text(stringResource(R.string.downloads_action_retry_failed)) }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items.forEach { item ->
                PlaylistItemStatusRow(item = item, onPause = onPause, onResume = onResume, onCancel = onCancel, onRetry = onRetry)
            }
        }
    }
}

@Composable
private fun PlaylistItemStatusRow(
    item: DownloadProgress,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "${item.playlistItemIndex ?: 0}. ${item.title ?: item.taskId}",
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = statusLabel(item.status),
            style = MaterialTheme.typography.labelMedium,
            color = statusColor(item.status),
        )
        when (item.status) {
            DownloadStatus.DOWNLOADING, DownloadStatus.PROCESSING ->
                TextButton(onClick = { onPause(item.taskId) }) { Text(stringResource(R.string.downloads_action_pause)) }

            DownloadStatus.PAUSED ->
                TextButton(onClick = { onResume(item.taskId) }) { Text(stringResource(R.string.downloads_action_resume)) }

            DownloadStatus.FAILED ->
                TextButton(onClick = { onRetry(item.taskId) }) { Text(stringResource(R.string.downloads_action_retry)) }

            DownloadStatus.QUEUED, DownloadStatus.ANALYZING ->
                TextButton(onClick = { onCancel(item.taskId) }) { Text(stringResource(R.string.downloads_action_cancel)) }

            else -> Unit
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadProgress,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                modifier = Modifier
                    .width(80.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                if (task.thumbnailUrl != null) {
                    AsyncImage(
                        model = task.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.BrokenImage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = task.title ?: task.taskId, style = MaterialTheme.typography.bodyMedium, maxLines = 2)

                Text(
                    text = statusLabel(task.status),
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColor(task.status),
                )

                if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.PROCESSING) {
                    val progressFraction = task.totalBytes?.takeIf { it > 0 }
                        ?.let { total -> (task.bytesTransferred.toFloat() / total).coerceIn(0f, 1f) }
                    if (progressFraction != null) {
                        LinearProgressIndicator(progress = { progressFraction }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        text = progressDetailLabel(task),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val errorMessage = task.errorMessage
                if (task.status == DownloadStatus.FAILED && errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (task.status) {
                        DownloadStatus.DOWNLOADING, DownloadStatus.PROCESSING -> {
                            OutlinedButton(onClick = { onPause(task.taskId) }) {
                                Text(stringResource(R.string.downloads_action_pause))
                            }
                            OutlinedButton(onClick = { onCancel(task.taskId) }) {
                                Text(stringResource(R.string.downloads_action_cancel))
                            }
                        }

                        DownloadStatus.PAUSED -> {
                            OutlinedButton(onClick = { onResume(task.taskId) }) {
                                Text(stringResource(R.string.downloads_action_resume))
                            }
                            OutlinedButton(onClick = { onCancel(task.taskId) }) {
                                Text(stringResource(R.string.downloads_action_cancel))
                            }
                        }

                        DownloadStatus.QUEUED -> {
                            OutlinedButton(onClick = { onCancel(task.taskId) }) {
                                Text(stringResource(R.string.downloads_action_cancel))
                            }
                        }

                        DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                            OutlinedButton(onClick = { onRetry(task.taskId) }) {
                                Text(stringResource(R.string.downloads_action_retry))
                            }
                        }

                        DownloadStatus.COMPLETED -> {
                            val destinationUri = task.destinationUri
                            if (destinationUri != null) {
                                OutlinedButton(onClick = { openDownloadedFile(context, destinationUri) }) {
                                    Text(stringResource(R.string.downloads_action_open))
                                }
                            }
                        }

                        else -> Unit
                    }
                }
            }
        }
    }
}

private fun openDownloadedFile(context: android.content.Context, destinationUri: String) {
    val uri = Uri.parse(destinationUri)
    val extension = destinationUri.substringAfterLast('.', "")
    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

private fun statusLabel(status: DownloadStatus): String = when (status) {
    DownloadStatus.QUEUED -> "Queued"
    DownloadStatus.ANALYZING -> "Checking quality…"
    DownloadStatus.DOWNLOADING -> "Downloading"
    DownloadStatus.PROCESSING, DownloadStatus.MERGING -> "Processing"
    DownloadStatus.COMPLETED -> "Completed"
    DownloadStatus.PAUSED -> "Paused"
    DownloadStatus.CANCELLED -> "Cancelled"
    DownloadStatus.FAILED -> "Failed"
}

@Composable
private fun statusColor(status: DownloadStatus) = when (status) {
    DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
    DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun progressDetailLabel(task: DownloadProgress): String {
    val transferred = formatFileSizeLabel(task.bytesTransferred) ?: "0 B"
    val total = task.totalBytes?.let { formatFileSizeLabel(it) }
    val sizePart = if (total != null) "$transferred / $total" else transferred
    val speedPart = task.throughputBytesPerSecond?.let { formatFileSizeLabel(it)?.let { s -> "$s/s" } }
    val etaPart = task.etaSeconds?.takeIf { it >= 0 }?.let { "ETA ${formatEtaLabel(it)}" }
    return listOfNotNull(sizePart, speedPart, etaPart).joinToString(" • ")
}

private fun formatEtaLabel(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}
