package com.mediavault.app.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.mediavault.core.database.entity.MediaItemEntity
import com.mediavault.core.domain.download.FormatSelectionModel
import com.mediavault.core.domain.download.QualityTier
import com.mediavault.core.domain.download.ResolvedSelection
import com.mediavault.core.domain.download.VideoQualityGroup
import com.mediavault.core.domain.extractor.ExtractionResult
import com.mediavault.core.domain.extractor.MediaAnalysisResult
import com.mediavault.core.domain.extractor.MediaCollectionItem
import com.mediavault.core.domain.extractor.MediaCollectionResult
import com.mediavault.core.domain.extractor.PlaylistAnalysisResult
import com.mediavault.core.domain.extractor.PlaylistItem
import com.mediavault.core.model.MediaFormat
import com.mediavault.core.model.MediaType
import java.time.LocalTime

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToDestination: (MediaVaultDestination) -> Unit = {},
    onNavigateToSources: () -> Unit = {},
) {
    // Home's NavBackStackEntry (and this ViewModel) is never destroyed by a tab switch — it's
    // the nav graph's start destination, so MediaVaultNavHost's popUpTo always excludes it —
    // so without this, a stale in-progress/completed link analysis from a previous visit would
    // still be showing the next time the Home tab is tapped. `remember(Unit)` runs once per
    // fresh composition entry (cold start, or returning from another destination) and
    // synchronously, before the collectAsState() read just below, so the very first frame of a
    // fresh entry already renders clean instead of flashing the stale state for one frame.
    // Unlike Library/Downloads/the Player tab, Home has no "resume where I left off" — it's
    // meant to be reset every time.
    remember(Unit) { viewModel.resetToCleanState() }

    val uiState by viewModel.uiState.collectAsState()

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
        qualityPickerActions = remember(viewModel) {
            QualityPickerActions(
                onTierSelected = viewModel::onQualityTierSelected,
                onVariantSelected = viewModel::onVideoVariantSelected,
                onIncludeMultipleAudioToggled = viewModel::onIncludeMultipleAudioToggled,
                onAudioTrackToggled = viewModel::onAudioTrackToggled,
            )
        },
        onDownloadClicked = viewModel::onDownloadClicked,
        onPlaylistItemTapped = viewModel::onPlaylistItemTapped,
        onBeginRangeSelection = viewModel::beginRangeSelection,
        onCancelSelection = viewModel::cancelSelection,
        onDownloadEntirePlaylist = viewModel::downloadEntirePlaylist,
        onDownloadSelected = viewModel::downloadSelectedItems,
        onSkipAlreadyDownloadedToggled = viewModel::onSkipAlreadyDownloadedToggled,
        playlistQualityPickerActions = remember(viewModel) {
            QualityPickerActions(
                onTierSelected = viewModel::onPlaylistQualityTierSelected,
                onVariantSelected = viewModel::onPlaylistVideoVariantSelected,
                onIncludeMultipleAudioToggled = viewModel::onPlaylistIncludeMultipleAudioToggled,
                onAudioTrackToggled = viewModel::onPlaylistAudioTrackToggled,
            )
        },
        onQueuePlaylistClicked = viewModel::onQueuePlaylistClicked,
        onCancelPlaylistDownloadSetup = viewModel::cancelPlaylistDownloadSetup,
        onCollectionItemTapped = viewModel::onCollectionItemTapped,
        onDownloadEntireCollection = viewModel::downloadEntireCollection,
        onDownloadSelectedCollectionItems = viewModel::downloadSelectedCollectionItems,
        onNetworkWarningConfirmed = viewModel::onNetworkWarningConfirmed,
        onNetworkWarningDismissed = viewModel::onNetworkWarningDismissed,
        onNavigateToDestination = onNavigateToDestination,
        onNavigateToSources = onNavigateToSources,
    )
}

@Composable
private fun HomeScreenContent(
    uiState: HomeUiState,
    onUrlChanged: (String) -> Unit,
    onAnalyzeClick: () -> Unit,
    onCancelClick: () -> Unit,
    qualityPickerActions: QualityPickerActions,
    onDownloadClicked: () -> Unit,
    onPlaylistItemTapped: (PlaylistItem) -> Unit,
    onBeginRangeSelection: () -> Unit,
    onCancelSelection: () -> Unit,
    onDownloadEntirePlaylist: () -> Unit,
    onDownloadSelected: () -> Unit,
    onSkipAlreadyDownloadedToggled: (Boolean) -> Unit,
    playlistQualityPickerActions: QualityPickerActions,
    onQueuePlaylistClicked: () -> Unit,
    onCancelPlaylistDownloadSetup: () -> Unit,
    onCollectionItemTapped: (MediaCollectionItem) -> Unit,
    onDownloadEntireCollection: () -> Unit,
    onDownloadSelectedCollectionItems: () -> Unit,
    onNetworkWarningConfirmed: () -> Unit,
    onNetworkWarningDismissed: () -> Unit,
    onNavigateToDestination: (MediaVaultDestination) -> Unit,
    onNavigateToSources: () -> Unit,
) {
    val showDiscovery = uiState.result == null && !uiState.isAnalyzing
    val selectedSelection = uiState.formatSelection?.resolve(uiState.selectedQuality)
    val setup = uiState.playlistDownloadSetup

    // Exactly one persistent bottom bar can apply at a time: the single-item format picker's
    // Download bar takes priority while a Single result is showing; the playlist quality-setup
    // step's Queue bar only applies once that step is open.
    val showDownloadBar = uiState.result is ExtractionResult.Single && uiState.formatSelection != null
    val showPlaylistBar = uiState.result is ExtractionResult.Playlist && setup != null

    Box(modifier = Modifier.fillMaxSize()) {
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
                        formatSelection = uiState.formatSelection,
                        selectedQuality = uiState.selectedQuality,
                        actions = qualityPickerActions,
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
                            onSkipAlreadyDownloadedToggled = onSkipAlreadyDownloadedToggled,
                        )
                    }
                    if (setup != null) {
                        item {
                            PlaylistDownloadSetupCard(
                                setup = setup,
                                actions = playlistQualityPickerActions,
                                onCancelClicked = onCancelPlaylistDownloadSetup,
                            )
                        }
                    }
                    items(result.playlist.items, key = { it.id }) { playlistItem ->
                        PlaylistItemRow(
                            item = playlistItem,
                            isSelected = playlistItem.id in uiState.playlistSelection.selectedItemIds,
                            onClick = { onPlaylistItemTapped(playlistItem) },
                        )
                    }
                }

                is ExtractionResult.Collection -> {
                    val collection = result.collection
                    item {
                        CollectionHeader(
                            collection = collection,
                            onDownloadSingleImage = onDownloadEntireCollection,
                        )
                    }
                    if (collection.items.size > 1) {
                        item {
                            CollectionSelectionToolbar(
                                selection = uiState.playlistSelection,
                                onBeginRangeSelection = onBeginRangeSelection,
                                onCancelSelection = onCancelSelection,
                                onDownloadAll = onDownloadEntireCollection,
                                onDownloadSelected = onDownloadSelectedCollectionItems,
                                onSkipAlreadyDownloadedToggled = onSkipAlreadyDownloadedToggled,
                            )
                        }
                        items(collection.items, key = { it.id }) { collectionItem ->
                            CollectionItemRow(
                                item = collectionItem,
                                isSelected = collectionItem.id in uiState.playlistSelection.selectedItemIds,
                                onClick = { onCollectionItemTapped(collectionItem) },
                            )
                        }
                    }
                }

                null -> Unit
            }

            if (showDiscovery) {
                item { PopularSourcesSection(onClick = onNavigateToSources) }
                item { QuickActionsSection(onNavigateToDestination) }
                item {
                    RecentActivitySection(
                        items = uiState.recentActivity,
                        onItemClick = { onNavigateToDestination(MediaVaultDestination.LIBRARY) },
                    )
                }
                item { DeviceStatusRow(uiState.freeStorageBytes, uiState.networkStatus) }
            }

            // Reserves room so the last real item never sits behind the persistent bottom bar.
            if (showDownloadBar || showPlaylistBar) {
                item { Spacer(modifier = Modifier.height(72.dp)) }
            }
        }

        if (showDownloadBar) {
            DownloadActionBar(
                selectedSelection = selectedSelection,
                onDownloadClicked = onDownloadClicked,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else if (showPlaylistBar && setup != null) {
            PlaylistQueueActionBar(
                setup = setup,
                onQueueClicked = onQueuePlaylistClicked,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    val warning = uiState.networkWarning
    if (warning != null) {
        NetworkWarningDialog(
            reason = warning.reason,
            onConfirm = onNetworkWarningConfirmed,
            onDismiss = onNetworkWarningDismissed,
        )
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
private fun PopularSourcesSection(onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(
            text = stringResource(R.string.home_popular_sources),
            trailing = {
                Text(
                    text = stringResource(R.string.sources_see_all),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onClick),
                )
            },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(popularSourceNames) { name ->
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.clickable(onClick = onClick),
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

/**
 * The most recent completed downloads/media additions — [items] is
 * [HomeUiState.recentActivity], a live, capped slice of the exact same Room-backed
 * [com.mediavault.app.library.LibraryRepository] flow Library itself renders (see
 * [HomeViewModel]'s init block), so a newly completed download appears here the moment it lands
 * in the Library, no restart or manual refresh needed. Falls back to the real empty state only
 * when [items] is genuinely empty — never a hard-coded placeholder.
 */
@Composable
private fun RecentActivitySection(items: List<MediaItemEntity>, onItemClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(text = stringResource(R.string.home_recent_activity))
        if (items.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Default.History,
                title = stringResource(R.string.home_recent_activity_empty_title),
                description = stringResource(R.string.home_recent_activity_empty_body),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { item ->
                    RecentActivityRow(item = item, onClick = onItemClick)
                }
            }
        }
    }
}

/** One Recent Activity row: thumbnail, title, and [recentActivitySubtitle] — tapping opens Library, the one place this item can actually be played/managed (Home itself has no per-item player/library actions of its own). */
@Composable
private fun RecentActivityRow(item: MediaItemEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Thumbnail(
                thumbnailUrl = item.thumbnailUrl,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(
                    text = recentActivitySubtitle(item),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
    formatSelection: FormatSelectionModel?,
    selectedQuality: SelectedQualityState,
    actions: QualityPickerActions,
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

        if (formatSelection != null) {
            QualityPickerSection(model = formatSelection, selection = selectedQuality, actions = actions)
        }
    }
}

/**
 * The whole format picker for one [FormatSelectionModel]: quality tiers (only ever a handful of
 * chips, never the 100+ raw rows a modern source can report — see
 * [com.mediavault.core.domain.download.QualityTier]), that tier's own variant picker when it
 * genuinely has more than one, and an Audio section — multi-select once a video tier needing a
 * separate track is picked, single-select for a bare audio-only source. Shared identically by
 * the single-item screen and the playlist quality-setup step (see each's own call site).
 */
@Composable
private fun QualityPickerSection(
    model: FormatSelectionModel,
    selection: SelectedQualityState,
    actions: QualityPickerActions,
) {
    if (model.videoQualityGroups.isEmpty() && model.audioTracks.isEmpty()) return

    if (model.videoQualityGroups.isNotEmpty()) {
        Text(text = stringResource(R.string.home_section_video), style = MaterialTheme.typography.labelLarge)
        QualityTierRow(groups = model.videoQualityGroups, selectedTier = selection.tier, onTierSelected = actions.onTierSelected)

        val selectedGroup = model.videoQualityGroups.firstOrNull { it.tier == selection.tier }
        if (selectedGroup != null && selectedGroup.variants.size > 1) {
            VariantPicker(
                variants = selectedGroup.variants,
                selectedFormatId = selection.videoVariantFormatId ?: selectedGroup.bestVariant.formatId,
                onVariantSelected = actions.onVariantSelected,
            )
        }

        val selectedVariant = selectedGroup?.variants?.firstOrNull { it.formatId == selection.videoVariantFormatId } ?: selectedGroup?.bestVariant
        if (selectedGroup != null && selectedVariant?.hasAudio != true && model.audioTracks.isNotEmpty()) {
            AudioTrackSection(tracks = model.audioTracks, selection = selection, actions = actions)
        }
    } else {
        Text(text = stringResource(R.string.home_section_audio), style = MaterialTheme.typography.labelLarge)
        AudioTrackSection(tracks = model.audioTracks, selection = selection, actions = actions)
    }
}

/** A horizontal row of quality-tier chips — only tiers this source actually offers, never the full 4K/2K/1080p/720p/480p/Lower set padded out with unavailable ones. */
@Composable
private fun QualityTierRow(groups: List<VideoQualityGroup>, selectedTier: QualityTier?, onTierSelected: (QualityTier) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(groups, key = { it.tier }) { group ->
            FilterChip(
                selected = group.tier == selectedTier,
                onClick = { onTierSelected(group.tier) },
                label = { Text(group.tier.label) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
            )
        }
    }
}

/**
 * Shown only when the selected tier genuinely offers more than one variant (a different
 * codec/container/frame-rate at the same resolution) — per this picker's "useful variants only
 * when needed" requirement, a single-variant tier never shows this at all.
 */
@Composable
private fun VariantPicker(variants: List<MediaFormat>, selectedFormatId: String, onVariantSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = stringResource(R.string.home_variant_section_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        variants.forEach { variant ->
            DownloadOptionRow(
                title = videoVariantTitle(variant),
                subtitle = videoVariantSubtitle(variant),
                isSelected = variant.formatId == selectedFormatId,
                isSelectable = true,
                unavailableReason = null,
                onClick = { onVariantSelected(variant.formatId) },
            )
        }
    }
}

/**
 * Every available audio track as its own row, language as the title (see
 * [audioLanguageDisplayName] — never a guess), single-select (radio) until
 * [SelectedQualityState.includeMultipleAudio] is switched on, which turns every row into a
 * checkbox and reveals the toggle can add as many tracks as the user wants muxed into the final
 * file — see [ResolvedSelection.requiresProcessing][com.mediavault.core.domain.download.ResolvedSelection.requiresProcessing].
 */
@Composable
private fun AudioTrackSection(tracks: List<MediaFormat>, selection: SelectedQualityState, actions: QualityPickerActions) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = stringResource(R.string.home_section_audio), style = MaterialTheme.typography.labelLarge)

        if (tracks.size > 1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = selection.includeMultipleAudio, onCheckedChange = actions.onIncludeMultipleAudioToggled)
                Text(text = stringResource(R.string.home_include_multiple_audio), style = MaterialTheme.typography.bodyMedium)
            }
        }

        tracks.forEach { track ->
            val isSelected = track.formatId in selection.selectedAudioFormatIds
            if (selection.includeMultipleAudio) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { actions.onAudioTrackToggled(track.formatId) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = isSelected, onCheckedChange = { actions.onAudioTrackToggled(track.formatId) })
                    Column {
                        Text(text = audioLanguageDisplayName(track.languageCode), style = MaterialTheme.typography.bodyMedium)
                        Text(text = audioTrackSubtitle(track), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                DownloadOptionRow(
                    title = audioLanguageDisplayName(track.languageCode),
                    subtitle = audioTrackSubtitle(track),
                    isSelected = isSelected,
                    isSelectable = true,
                    unavailableReason = null,
                    onClick = { actions.onAudioTrackToggled(track.formatId) },
                )
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
    onSkipAlreadyDownloadedToggled: (Boolean) -> Unit,
) {
    SelectionToolbar(
        selection = selection,
        onBeginRangeSelection = onBeginRangeSelection,
        onCancelSelection = onCancelSelection,
        onDownloadAll = onDownloadEntirePlaylist,
        onDownloadSelected = onDownloadSelected,
        onSkipAlreadyDownloadedToggled = onSkipAlreadyDownloadedToggled,
        downloadAllLabel = stringResource(R.string.home_download_playlist),
        downloadSelectedLabel = stringResource(R.string.home_download_selected, selection.selectedItemIds.size),
    )
}

/** Same multi-select toolbar as [PlaylistSelectionToolbar], worded for an image collection. Only shown for a genuine multi-image carousel — a single image skips selection entirely (see [CollectionHeader]). */
@Composable
private fun CollectionSelectionToolbar(
    selection: PlaylistSelectionState,
    onBeginRangeSelection: () -> Unit,
    onCancelSelection: () -> Unit,
    onDownloadAll: () -> Unit,
    onDownloadSelected: () -> Unit,
    onSkipAlreadyDownloadedToggled: (Boolean) -> Unit,
) {
    SelectionToolbar(
        selection = selection,
        onBeginRangeSelection = onBeginRangeSelection,
        onCancelSelection = onCancelSelection,
        onDownloadAll = onDownloadAll,
        onDownloadSelected = onDownloadSelected,
        onSkipAlreadyDownloadedToggled = onSkipAlreadyDownloadedToggled,
        downloadAllLabel = stringResource(R.string.home_download_all_images),
        downloadSelectedLabel = stringResource(R.string.home_download_selected_images, selection.selectedItemIds.size),
    )
}

/** Range-select/skip-toggle/download toolbar shared by a video playlist and an image collection — the mechanics (toggle vs. range selection, skip-already-downloaded, "all" vs. "selected") don't depend on what kind of item is being selected, only the button wording does. */
@Composable
private fun SelectionToolbar(
    selection: PlaylistSelectionState,
    onBeginRangeSelection: () -> Unit,
    onCancelSelection: () -> Unit,
    onDownloadAll: () -> Unit,
    onDownloadSelected: () -> Unit,
    onSkipAlreadyDownloadedToggled: (Boolean) -> Unit,
    downloadAllLabel: String,
    downloadSelectedLabel: String,
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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onSkipAlreadyDownloadedToggled(!selection.skipAlreadyDownloaded) },
        ) {
            Checkbox(checked = selection.skipAlreadyDownloaded, onCheckedChange = onSkipAlreadyDownloadedToggled)
            Text(
                text = stringResource(R.string.home_skip_already_downloaded),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onDownloadAll,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(downloadAllLabel)
            }
            Button(
                onClick = onDownloadSelected,
                enabled = selection.selectedItemIds.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(downloadSelectedLabel)
            }
        }
    }
}

@Composable
private fun PlaylistDownloadSetupCard(
    setup: PlaylistDownloadSetupState,
    actions: QualityPickerActions,
    onCancelClicked: () -> Unit,
) {
    MediaVaultCard {
        Text(
            text = stringResource(R.string.home_playlist_setup_title, setup.items.size),
            style = MaterialTheme.typography.titleMedium,
        )

        when {
            setup.isResolvingFormats -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.home_playlist_setup_resolving),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            setup.errorMessage != null -> Text(
                text = setup.errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )

            else -> {
                Text(text = stringResource(R.string.home_select_format), style = MaterialTheme.typography.labelLarge)

                // Exact same picker as the single-item screen's own AnalysisResultCard (see
                // QualityPickerSection) — multi-audio-track selection resolves through the
                // identical logic, never a second, duplicated picker implementation.
                val model = setup.formatSelection
                if (model != null) {
                    QualityPickerSection(model = model, selection = setup.selectedQuality, actions = actions)
                }
            }
        }

        OutlinedButton(onClick = onCancelClicked) {
            Text(stringResource(R.string.home_cancel))
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

/**
 * Preview card for a single-item post or a (possibly mixed image/video) carousel. A single item
 * (the common "just one photo/reel" post) gets its own inline Download button here — no
 * selection toolbar/item list makes sense for a batch of one. A genuine multi-item carousel shows
 * only the real count here; [CollectionSelectionToolbar]/`CollectionItemRow` below it handle
 * picking which items. The count wording itself ("N images"/"N videos"/"N items") reflects
 * [collection]'s *actual* mix of [MediaCollectionItem.mediaType]s — never a flat "images" label
 * for a carousel that also contains video, which is what silently undercounted a mixed carousel
 * before every item carried its own real type.
 */
@Composable
private fun CollectionHeader(collection: MediaCollectionResult, onDownloadSingleImage: () -> Unit) {
    val isSingleItem = collection.items.size <= 1
    val hasImages = collection.items.any { it.mediaType == MediaType.IMAGE }
    val hasVideos = collection.items.any { it.mediaType == MediaType.VIDEO }
    val itemCountRes = when {
        hasImages && hasVideos -> R.string.home_collection_item_count_mixed
        hasVideos -> R.string.home_collection_item_count_videos
        else -> R.string.home_collection_item_count
    }
    MediaVaultCard {
        Icon(
            imageVector = Icons.Default.Photo,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )

        Thumbnail(collection.thumbnailUrl)

        if (collection.title.isNotBlank()) {
            Text(text = collection.title, style = MaterialTheme.typography.titleMedium, maxLines = 3)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = collection.sourceName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (!isSingleItem) {
                Text(
                    text = stringResource(itemCountRes, collection.items.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (isSingleItem) {
            Button(
                onClick = onDownloadSingleImage,
                modifier = Modifier.fillMaxWidth(),
                enabled = collection.items.isNotEmpty(),
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
private fun CollectionItemRow(item: MediaCollectionItem, isSelected: Boolean, onClick: () -> Unit) {
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

            Box {
                Thumbnail(
                    thumbnailUrl = item.thumbnailUrl,
                    modifier = Modifier
                        .width(96.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(6.dp)),
                )
                // A play-icon badge is the only per-item signal that this is a video, not an
                // image — CollectionItemRow's thumbnail alone can't otherwise distinguish them,
                // and a mixed carousel needs that distinction visible at a glance, not just in
                // the row's own text label.
                if (item.mediaType == MediaType.VIDEO) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(20.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    )
                }
            }

            Column {
                val indexRes = if (item.mediaType == MediaType.VIDEO) {
                    R.string.home_collection_item_index_video
                } else {
                    R.string.home_collection_item_index
                }
                Text(
                    text = stringResource(indexRes, item.index),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!item.isAvailable) {
                    Text(
                        text = stringResource(R.string.home_collection_item_unavailable),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
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

/** A shared, generic radio-row used by both [VariantPicker] and the single-select variant of [AudioTrackSection]. */
@Composable
private fun DownloadOptionRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    isSelectable: Boolean,
    unavailableReason: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isSelectable, onClick = onClick)
            .alpha(if (isSelectable) 1f else 0.5f)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = isSelected, onClick = onClick, enabled = isSelectable)
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(text = subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (unavailableReason != null) {
                Text(text = unavailableReason, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * Persistent bottom bar for the single-item format picker — stays visible while the format list
 * scrolls (see the [Box]/[Alignment.BottomCenter] usage in [HomeScreenContent]). Disabled with a
 * prompt until a format is selected; shows the selected quality and its estimated final size once
 * one is.
 */
@Composable
private fun DownloadActionBar(
    selectedSelection: ResolvedSelection?,
    onDownloadClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionBarSurface(modifier) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = selectedSelection?.let { selectedQualitySummaryLabel(it) } ?: stringResource(R.string.home_download_bar_prompt),
                style = MaterialTheme.typography.bodyMedium,
                color = if (selectedSelection != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = onDownloadClicked,
            enabled = selectedSelection != null,
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = stringResource(R.string.home_download_button), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/**
 * Persistent bottom bar for the playlist quality-setup step: item count, chosen quality (once
 * picked), and the running total estimate — see [estimatedPlaylistTotalSizeBytes]. Same
 * stays-visible-while-scrolling placement as [DownloadActionBar].
 */
@Composable
private fun PlaylistQueueActionBar(
    setup: PlaylistDownloadSetupState,
    onQueueClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedSelection = setup.formatSelection?.resolve(setup.selectedQuality)
    ActionBarSurface(modifier) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_playlist_bar_items, setup.items.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val qualityAndSize = if (selectedSelection != null) {
                val total = estimatedPlaylistTotalSizeBytes(selectedSelection, setup.items.size)
                listOfNotNull(selectedQualityLabel(selectedSelection), formatFileSizeLabel(total) ?: stringResource(R.string.home_playlist_bar_total_unknown))
                    .joinToString(" • ")
            } else {
                stringResource(R.string.home_playlist_bar_prompt)
            }
            Text(
                text = qualityAndSize,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = onQueueClicked,
            enabled = selectedSelection != null,
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = stringResource(R.string.home_queue_button), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** Shared chrome for the two persistent bottom bars — an opaque surface (never lets scrolled content show through) with a top border standing in for elevation, per this design system's flat/no-shadow style. */
@Composable
private fun ActionBarSurface(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

/** Confirmation dialog for [com.mediavault.core.domain.network.NetworkPolicyDecision.Warn] — a risky-but-not-blocked download is never queued without the user explicitly choosing to proceed. */
@Composable
private fun NetworkWarningDialog(reason: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_network_warn_title)) },
        text = { Text(reason) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.home_network_warn_proceed)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_cancel)) }
        },
    )
}
