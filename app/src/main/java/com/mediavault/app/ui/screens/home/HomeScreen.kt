package com.mediavault.app.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.mediavault.app.R
import com.mediavault.core.domain.extractor.ExtractionResult
import com.mediavault.core.domain.extractor.MediaAnalysisResult
import com.mediavault.core.domain.extractor.PlaylistAnalysisResult
import com.mediavault.core.domain.extractor.PlaylistItem
import com.mediavault.core.model.MediaFormat

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    HomeScreenContent(
        uiState = uiState,
        onUrlChanged = viewModel::onUrlChanged,
        onAnalyzeClick = viewModel::analyze,
        onCancelClick = viewModel::cancelInFlightAnalysis,
        onPlaylistItemTapped = viewModel::onPlaylistItemTapped,
        onBeginRangeSelection = viewModel::beginRangeSelection,
        onCancelSelection = viewModel::cancelSelection,
        onDownloadEntirePlaylist = viewModel::downloadEntirePlaylist,
        onDownloadSelected = viewModel::downloadSelectedItems,
    )
}

@Composable
private fun HomeScreenContent(
    uiState: HomeUiState,
    onUrlChanged: (String) -> Unit,
    onAnalyzeClick: () -> Unit,
    onCancelClick: () -> Unit,
    onPlaylistItemTapped: (PlaylistItem) -> Unit,
    onBeginRangeSelection: () -> Unit,
    onCancelSelection: () -> Unit,
    onDownloadEntirePlaylist: () -> Unit,
    onDownloadSelected: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(text = stringResource(R.string.home_title), style = MaterialTheme.typography.titleLarge)
        }

        item {
            OutlinedTextField(
                value = uiState.url,
                onValueChange = onUrlChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.home_url_hint)) },
                singleLine = true,
                enabled = !uiState.isAnalyzing,
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onAnalyzeClick, enabled = !uiState.isAnalyzing) {
                    Text(stringResource(R.string.home_analyze))
                }
                if (uiState.isAnalyzing) {
                    OutlinedButton(onClick = onCancelClick) {
                        Text(stringResource(R.string.home_cancel))
                    }
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(R.string.home_analyzing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        val error = uiState.errorMessage
        if (error != null) {
            item { MessageCard(message = error, isError = true) }
        }

        val info = uiState.infoMessage
        if (info != null) {
            item { MessageCard(message = info, isError = false) }
        }

        when (val result = uiState.result) {
            is ExtractionResult.Single -> item { AnalysisResultCard(result.media) }

            is ExtractionResult.Playlist -> {
                item { PlaylistHeader(result.playlist) }
                item {
                    PlaylistSelectionToolbar(
                        selection = uiState.playlistSelection,
                        onBeginRangeSelection = onBeginRangeSelection,
                        onCancelSelection = onCancelSelection,
                        onDownloadEntirePlaylist = onDownloadEntirePlaylist,
                        onDownloadSelected = onDownloadSelected,
                    )
                }
                items(result.playlist.items, key = { it.id }) { playlistItem ->
                    PlaylistItemRow(
                        item = playlistItem,
                        isSelected = playlistItem.id in uiState.playlistSelection.selectedItemIds,
                        onClick = { onPlaylistItemTapped(playlistItem) },
                    )
                }
            }

            null -> Unit
        }
    }
}

@Composable
private fun MessageCard(message: String, isError: Boolean) {
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun AnalysisResultCard(result: MediaAnalysisResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Thumbnail(result.thumbnailUrl)

            Text(text = result.title, style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = result.sourceName, style = MaterialTheme.typography.labelLarge)
                val durationLabel = formatDurationLabel(result.durationSeconds)
                if (durationLabel != null) {
                    Text(
                        text = durationLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (result.formats.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.home_available_formats),
                    style = MaterialTheme.typography.labelLarge,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    result.formats
                        .sortedByDescending { it.estimatedSizeBytes ?: 0L }
                        .forEach { format -> FormatRow(format) }
                }
            }
        }
    }
}

@Composable
private fun PlaylistHeader(playlist: PlaylistAnalysisResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.home_playlist_detected),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Thumbnail(playlist.thumbnailUrl)

            Text(text = playlist.title, style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = playlist.sourceName, style = MaterialTheme.typography.labelLarge)
                val count = playlist.itemCount
                if (count != null) {
                    Text(
                        text = stringResource(R.string.home_playlist_item_count, count),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistSelectionToolbar(
    selection: PlaylistSelectionState,
    onBeginRangeSelection: () -> Unit,
    onCancelSelection: () -> Unit,
    onDownloadEntirePlaylist: () -> Unit,
    onDownloadSelected: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBeginRangeSelection, enabled = !selection.isRangeSelectionActive) {
                Text(stringResource(R.string.home_select_range))
            }
            val hasSelection = selection.selectedItemIds.isNotEmpty() || selection.isRangeSelectionActive
            if (hasSelection) {
                OutlinedButton(onClick = onCancelSelection) {
                    Text(stringResource(R.string.home_cancel_selection))
                }
            }
        }

        if (selection.isRangeSelectionActive) {
            Text(
                text = stringResource(R.string.home_select_range_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onDownloadEntirePlaylist) {
                Text(stringResource(R.string.home_download_playlist))
            }
            Button(onClick = onDownloadSelected, enabled = selection.selectedItemIds.isNotEmpty()) {
                Text(stringResource(R.string.home_download_selected, selection.selectedItemIds.size))
            }
        }
    }
}

@Composable
private fun PlaylistItemRow(item: PlaylistItem, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.isAvailable, onClick = onClick)
            .alpha(if (item.isAvailable) 1f else 0.5f)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = isSelected, onCheckedChange = { onClick() }, enabled = item.isAvailable)

        Thumbnail(
            thumbnailUrl = item.thumbnailUrl,
            modifier = Modifier
                .width(96.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp)),
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "${item.index}. ${item.title}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
            val subtitle = if (!item.isAvailable) {
                stringResource(R.string.home_unavailable)
            } else {
                formatDurationLabel(item.durationSeconds)
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Thumbnail(
    thumbnailUrl: String?,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f)
        .clip(RoundedCornerShape(8.dp)),
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        if (thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FormatRow(format: MediaFormat) {
    Text(
        text = formatFormatSummary(format),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
