package com.mediavault.app.ui.screens.home

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.mediavault.app.R
import com.mediavault.app.navigation.MediaVaultDestination
import com.mediavault.app.ui.components.EmptyStateCard
import com.mediavault.app.ui.components.MediaVaultCard
import com.mediavault.app.ui.components.MediaVaultTopBar
import com.mediavault.app.ui.components.SectionLabel
import com.mediavault.app.util.NetworkStatus
import com.mediavault.core.domain.extractor.ExtractionResult
import com.mediavault.core.domain.extractor.MediaAnalysisResult
import com.mediavault.core.domain.extractor.PlaylistAnalysisResult
import com.mediavault.core.domain.extractor.PlaylistItem
import com.mediavault.core.model.MediaFormat
import java.time.LocalTime

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToDestination: (MediaVaultDestination) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.onDestinationFolderPicked(uri.toString())
        } else {
            viewModel.onDestinationPickerDismissed()
        }
    }

    LaunchedEffect(uiState.awaitingDestinationPick) {
        if (uiState.awaitingDestinationPick) {
            folderPickerLauncher.launch(null)
        }
    }

    LaunchedEffect(uiState.justQueued) {
        if (uiState.justQueued) {
            onNavigateToDestination(MediaVaultDestination.DOWNLOADS)
            viewModel.consumeJustQueued()
        }
    }

    HomeScreenContent(
        uiState = uiState,
        onUrlChanged = viewModel::onUrlChanged,
        onAnalyzeClick = viewModel::analyze,
        onCancelClick = viewModel::cancelInFlightAnalysis,
        onFormatSelected = viewModel::onFormatSelected,
        onDownloadClicked = viewModel::onDownloadClicked,
        onPlaylistItemTapped = viewModel::onPlaylistItemTapped,
        onBeginRangeSelection = viewModel::beginRangeSelection,
        onCancelSelection = viewModel::cancelSelection,
        onDownloadEntirePlaylist = viewModel::downloadEntirePlaylist,
        onDownloadSelected = viewModel::downloadSelectedItems,
        onNavigateToDestination = onNavigateToDestination,
    )
}

@Composable
private fun HomeScreenContent(
    uiState: HomeUiState,
    onUrlChanged: (String) -> Unit,
    onAnalyzeClick: () -> Unit,
    onCancelClick: () -> Unit,
    onFormatSelected: (MediaFormat) -> Unit,
    onDownloadClicked: () -> Unit,
    onPlaylistItemTapped: (PlaylistItem) -> Unit,
    onBeginRangeSelection: () -> Unit,
    onCancelSelection: () -> Unit,
    onDownloadEntirePlaylist: () -> Unit,
    onDownloadSelected: () -> Unit,
    onNavigateToDestination: (MediaVaultDestination) -> Unit,
) {
    val showDiscovery = uiState.result == null && !uiState.isAnalyzing

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { MediaVaultTopBar(title = stringResource(R.string.home_title)) }

        item { GreetingHeader() }

        item {
            UrlAnalyzeCard(
                url = uiState.url,
                isAnalyzing = uiState.isAnalyzing,
                onUrlChanged = onUrlChanged,
                onAnalyzeClick = onAnalyzeClick,
                onCancelClick = onCancelClick,
            )
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
            is ExtractionResult.Single -> item {
                AnalysisResultCard(
                    result = result.media,
                    selectedFormatId = uiState.selectedFormatId,
                    onFormatSelected = onFormatSelected,
                    onDownloadClicked = onDownloadClicked,
                )
            }

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

        if (showDiscovery) {
            item { PopularSourcesSection() }
            item { QuickActionsSection(onNavigateToDestination) }
            item { RecentActivitySection() }
            item { DeviceStatusRow(uiState.freeStorageBytes, uiState.networkStatus) }
        }
    }
}

@Composable
private fun GreetingHeader() {
    val greetingRes = when (LocalTime.now().hour) {
        in 5..11 -> R.string.home_greeting_morning
        in 12..17 -> R.string.home_greeting_afternoon
        else -> R.string.home_greeting_evening
    }
    Column {
        Text(text = stringResource(greetingRes), style = MaterialTheme.typography.headlineSmall)
        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UrlAnalyzeCard(
    url: String,
    isAnalyzing: Boolean,
    onUrlChanged: (String) -> Unit,
    onAnalyzeClick: () -> Unit,
    onCancelClick: () -> Unit,
) {
    MediaVaultCard {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.home_url_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            enabled = !isAnalyzing,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )

        Button(
            onClick = onAnalyzeClick,
            enabled = !isAnalyzing,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = stringResource(R.string.home_analyze),
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        if (isAnalyzing) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.home_analyzing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onCancelClick) {
                    Text(stringResource(R.string.home_cancel))
                }
            }
        }
    }
}

private val popularSourceNames = listOf("YouTube", "Instagram", "TikTok", "Facebook", "Vimeo")

@Composable
private fun PopularSourcesSection() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(text = stringResource(R.string.home_popular_sources))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(popularSourceNames) { name ->
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

private data class QuickAction(
    val destination: MediaVaultDestination,
    val labelRes: Int,
    val subtitleRes: Int,
    val icon: ImageVector,
)

@Composable
private fun QuickActionsSection(onNavigateToDestination: (MediaVaultDestination) -> Unit) {
    val actions = listOf(
        QuickAction(
            MediaVaultDestination.DOWNLOADS,
            R.string.nav_downloads,
            R.string.home_quick_action_downloads_subtitle,
            Icons.Default.Download,
        ),
        QuickAction(
            MediaVaultDestination.LIBRARY,
            R.string.nav_library,
            R.string.home_quick_action_library_subtitle,
            Icons.AutoMirrored.Filled.PlaylistPlay,
        ),
        QuickAction(
            MediaVaultDestination.PLAYER,
            R.string.nav_player,
            R.string.home_quick_action_player_subtitle,
            Icons.Default.PlayArrow,
        ),
        QuickAction(
            MediaVaultDestination.SETTINGS,
            R.string.nav_settings,
            R.string.home_quick_action_settings_subtitle,
            Icons.Default.Storage,
        ),
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(text = stringResource(R.string.home_quick_actions))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuickActionCard(actions[0], onNavigateToDestination, Modifier.weight(1f))
            QuickActionCard(actions[1], onNavigateToDestination, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuickActionCard(actions[2], onNavigateToDestination, Modifier.weight(1f))
            QuickActionCard(actions[3], onNavigateToDestination, Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickActionCard(
    action: QuickAction,
    onNavigateToDestination: (MediaVaultDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable { onNavigateToDestination(action.destination) },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(text = stringResource(action.labelRes), style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(action.subtitleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentActivitySection() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(text = stringResource(R.string.home_recent_activity))
        EmptyStateCard(
            icon = Icons.Default.History,
            title = stringResource(R.string.home_recent_activity_empty_title),
            description = stringResource(R.string.home_recent_activity_empty_body),
        )
    }
}

@Composable
private fun DeviceStatusRow(freeStorageBytes: Long?, networkStatus: NetworkStatus?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            val storageLabel = formatFileSizeLabel(freeStorageBytes)
            if (storageLabel != null) {
                StatusChip(
                    icon = Icons.Default.Storage,
                    text = stringResource(R.string.home_storage_free, storageLabel),
                )
            }

            val (networkIcon, networkLabelRes) = when (networkStatus) {
                NetworkStatus.WIFI -> Icons.Default.Wifi to R.string.network_status_wifi
                NetworkStatus.MOBILE_DATA -> Icons.Default.SignalCellularAlt to R.string.network_status_mobile
                NetworkStatus.OFFLINE -> Icons.Default.WifiOff to R.string.network_status_offline
                null -> null to null
            }
            if (networkIcon != null && networkLabelRes != null) {
                StatusChip(icon = networkIcon, text = stringResource(networkLabelRes))
            }
        }
    }
}

@Composable
private fun StatusChip(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MessageCard(message: String, isError: Boolean) {
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
private fun AnalysisResultCard(
    result: MediaAnalysisResult,
    selectedFormatId: String?,
    onFormatSelected: (MediaFormat) -> Unit,
    onDownloadClicked: () -> Unit,
) {
    MediaVaultCard {
        Thumbnail(result.thumbnailUrl)

        Text(text = result.title, style = MaterialTheme.typography.titleMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = result.sourceName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
                text = stringResource(R.string.home_select_format),
                style = MaterialTheme.typography.labelLarge,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                result.formats
                    .sortedByDescending { it.estimatedSizeBytes ?: 0L }
                    .forEach { format ->
                        FormatRow(
                            format = format,
                            isSelected = format.formatId == selectedFormatId,
                            onClick = { onFormatSelected(format) },
                        )
                    }
            }

            Button(
                onClick = onDownloadClicked,
                enabled = selectedFormatId != null,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(text = stringResource(R.string.home_download_button), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun PlaylistHeader(playlist: PlaylistAnalysisResult) {
    MediaVaultCard {
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
            Text(text = playlist.sourceName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
            Button(
                onClick = onDownloadEntirePlaylist,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(stringResource(R.string.home_download_playlist))
            }
            Button(
                onClick = onDownloadSelected,
                enabled = selection.selectedItemIds.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(stringResource(R.string.home_download_selected, selection.selectedItemIds.size))
            }
        }
    }
}

@Composable
private fun PlaylistItemRow(item: PlaylistItem, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.isAvailable, onClick = onClick)
            .alpha(if (item.isAvailable) 1f else 0.5f),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
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
}

@Composable
private fun Thumbnail(
    thumbnailUrl: String?,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f)
        .clip(RoundedCornerShape(8.dp)),
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant) {
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
private fun FormatRow(format: MediaFormat, isSelected: Boolean, onClick: () -> Unit) {
    val isSelectable = format.isSelectableForDownload()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isSelectable, onClick = onClick)
            .alpha(if (isSelectable) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = isSelected, onClick = onClick, enabled = isSelectable)
        Column {
            Text(
                text = formatFormatSummary(format),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!isSelectable) {
                Text(
                    text = stringResource(R.string.home_format_requires_merge),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
