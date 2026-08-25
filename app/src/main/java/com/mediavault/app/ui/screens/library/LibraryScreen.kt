package com.mediavault.app.ui.screens.library

import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.mediavault.app.R
import com.mediavault.app.library.LibrarySortOrder
import com.mediavault.app.ui.components.EmptyStateCard
import com.mediavault.app.ui.components.MediaDetailsDialog
import com.mediavault.app.ui.components.MediaVaultTopBar
import com.mediavault.app.ui.screens.home.formatDurationLabel
import com.mediavault.app.ui.screens.home.formatFileSizeLabel
import com.mediavault.core.database.entity.MediaItemEntity
import com.mediavault.core.model.MediaType
import kotlinx.coroutines.delay

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onOpenPlayer: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var exportTarget by remember { mutableStateOf<MediaItemEntity?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        val target = exportTarget
        exportTarget = null
        if (uri != null && target != null) viewModel.exportTo(target, uri)
    }

    LaunchedEffect(uiState.errorMessage) {
        if (uiState.errorMessage != null) {
            delay(4000)
            viewModel.consumeErrorMessage()
        }
    }
    LaunchedEffect(uiState.infoMessage) {
        if (uiState.infoMessage != null) {
            delay(2500)
            viewModel.consumeInfoMessage()
        }
    }

    LibraryScreenContent(
        uiState = uiState,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onSortOrderChanged = viewModel::onSortOrderChanged,
        onPlay = { onOpenPlayer(it.id) },
        onShare = { item ->
            val uri = viewModel.shareUriFor(item) ?: return@LibraryScreenContent
            val mimeType = mimeTypeFor(item)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(Intent.createChooser(intent, item.title)) }
        },
        onExport = { item ->
            exportTarget = item
            exportLauncher.launch(suggestedExportFileName(item))
        },
        onRenameRequested = viewModel::onRenameRequested,
        onDeleteRequested = viewModel::onDeleteRequested,
        onDetailsRequested = viewModel::onDetailsRequested,
    )

    val renameTarget = uiState.renameTarget
    if (renameTarget != null) {
        RenameDialog(
            initialTitle = renameTarget.title,
            onConfirm = viewModel::onRenameConfirmed,
            onDismiss = viewModel::onRenameDismissed,
        )
    }

    val deleteTarget = uiState.deleteTarget
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = viewModel::onDeleteDismissed,
            title = { Text(stringResource(R.string.library_delete_title)) },
            text = { Text(stringResource(R.string.library_delete_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::onDeleteConfirmed) {
                    Text(stringResource(R.string.library_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDeleteDismissed) { Text(stringResource(R.string.library_dialog_cancel)) }
            },
        )
    }

    val detailsTarget = uiState.detailsTarget
    if (detailsTarget != null) {
        MediaDetailsDialog(item = detailsTarget, onDismiss = viewModel::onDetailsDismissed)
    }
}

private fun mimeTypeFor(item: MediaItemEntity): String =
    item.container?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) } ?: "*/*"

private fun suggestedExportFileName(item: MediaItemEntity): String {
    val extension = item.container ?: "bin"
    return "${item.title}.$extension"
}

@Composable
private fun LibraryScreenContent(
    uiState: LibraryUiState,
    onSearchQueryChanged: (String) -> Unit,
    onSortOrderChanged: (LibrarySortOrder) -> Unit,
    onPlay: (MediaItemEntity) -> Unit,
    onShare: (MediaItemEntity) -> Unit,
    onExport: (MediaItemEntity) -> Unit,
    onRenameRequested: (MediaItemEntity) -> Unit,
    onDeleteRequested: (MediaItemEntity) -> Unit,
    onDetailsRequested: (MediaItemEntity) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { MediaVaultTopBar(title = stringResource(R.string.library_title)) }

        item {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.library_search_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SortChip(stringResource(R.string.library_sort_recent), uiState.sortOrder == LibrarySortOrder.RECENT) {
                    onSortOrderChanged(LibrarySortOrder.RECENT)
                }
                SortChip(stringResource(R.string.library_sort_name), uiState.sortOrder == LibrarySortOrder.NAME) {
                    onSortOrderChanged(LibrarySortOrder.NAME)
                }
                SortChip(stringResource(R.string.library_sort_size), uiState.sortOrder == LibrarySortOrder.SIZE) {
                    onSortOrderChanged(LibrarySortOrder.SIZE)
                }
            }
        }

        if (!uiState.isLoading && uiState.items.isEmpty()) {
            item {
                if (uiState.searchQuery.isBlank()) {
                    EmptyStateCard(
                        icon = Icons.Default.VideoLibrary,
                        title = stringResource(R.string.library_empty_title),
                        description = stringResource(R.string.library_empty_body),
                    )
                } else {
                    EmptyStateCard(
                        icon = Icons.Default.VideoLibrary,
                        title = stringResource(R.string.library_search_empty_title),
                        description = stringResource(R.string.library_search_empty_body, uiState.searchQuery),
                    )
                }
            }
        }

        items(uiState.items, key = { it.entity.id }) { libraryItem ->
            LibraryItemCard(
                libraryItem = libraryItem,
                onPlay = onPlay,
                onShare = onShare,
                onExport = onExport,
                onRenameRequested = onRenameRequested,
                onDeleteRequested = onDeleteRequested,
                onDetailsRequested = onDetailsRequested,
            )
        }
    }
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun LibraryItemCard(
    libraryItem: LibraryItemUi,
    onPlay: (MediaItemEntity) -> Unit,
    onShare: (MediaItemEntity) -> Unit,
    onExport: (MediaItemEntity) -> Unit,
    onRenameRequested: (MediaItemEntity) -> Unit,
    onDeleteRequested: (MediaItemEntity) -> Unit,
    onDetailsRequested: (MediaItemEntity) -> Unit,
) {
    val item = libraryItem.entity
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = { if (!libraryItem.isMissing) onPlay(item) },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .width(80.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                if (item.thumbnailUrl != null) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = if (item.mediaType == MediaType.AUDIO) Icons.Default.Audiotrack else Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                Text(
                    text = libraryItemSubtitle(item),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (libraryItem.isMissing) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = stringResource(R.string.library_file_missing),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_action_play)) },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                        enabled = !libraryItem.isMissing,
                        onClick = { menuExpanded = false; onPlay(item) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_action_share)) },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        enabled = !libraryItem.isMissing,
                        onClick = { menuExpanded = false; onShare(item) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_action_export)) },
                        leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                        enabled = !libraryItem.isMissing,
                        onClick = { menuExpanded = false; onExport(item) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_action_rename)) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        enabled = !libraryItem.isMissing,
                        onClick = { menuExpanded = false; onRenameRequested(item) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_action_delete)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = { menuExpanded = false; onDeleteRequested(item) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_action_details)) },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        onClick = { menuExpanded = false; onDetailsRequested(item) },
                    )
                }
            }
        }
    }
}

private fun libraryItemSubtitle(item: MediaItemEntity): String {
    val duration = formatDurationLabel(item.durationMs?.let { it / 1000 })
    val size = formatFileSizeLabel(item.sizeBytes)
    val parts = listOfNotNull(duration, item.resolutionLabel, size)
    return parts.joinToString(" • ")
}

@Composable
private fun RenameDialog(initialTitle: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initialTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(stringResource(R.string.library_rename_hint)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.library_rename_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_dialog_cancel)) }
        },
    )
}

