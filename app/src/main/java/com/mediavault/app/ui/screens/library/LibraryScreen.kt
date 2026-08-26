package com.mediavault.app.ui.screens.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mediavault.app.R
import com.mediavault.app.library.LibrarySortOrder
import com.mediavault.app.library.MediaOrigin
import com.mediavault.app.library.exportFileName
import com.mediavault.app.library.mimeTypeFor
import com.mediavault.app.library.origin
import com.mediavault.app.ui.components.EmptyStateCard
import com.mediavault.app.ui.components.MediaDetailsDialog
import com.mediavault.app.ui.components.MediaThumbnail
import com.mediavault.app.ui.components.MediaVaultTopBar
import com.mediavault.app.ui.screens.home.formatDurationLabel
import com.mediavault.app.ui.screens.home.formatFileSizeLabel
import com.mediavault.core.database.entity.MediaItemEntity
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

    // Explicit-action-only imports (never an automatic/background scan) — see MediaImportRepository.
    var showImportChooser by remember { mutableStateOf(false) }
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> viewModel.onImportFileSelected(uri) }
    val importFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> viewModel.onImportFolderSelected(uri) }

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
        onAddMediaClicked = { showImportChooser = true },
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
        onSaveToDeviceRequested = viewModel::onSaveToDeviceRequested,
        onRenameRequested = viewModel::onRenameRequested,
        onDeleteRequested = viewModel::onDeleteRequested,
        onDetailsRequested = viewModel::onDetailsRequested,
    )

    if (showImportChooser) {
        ImportChooserDialog(
            onImportFile = {
                showImportChooser = false
                importFileLauncher.launch(arrayOf("video/*", "audio/*"))
            },
            onImportFolder = {
                showImportChooser = false
                importFolderLauncher.launch(null)
            },
            onDismiss = { showImportChooser = false },
        )
    }

    val saveToDeviceTarget = uiState.saveToDeviceTarget
    if (saveToDeviceTarget != null) {
        SaveToDeviceDialog(
            onSaveToGallery = {
                viewModel.onSaveToDeviceDismissed()
                viewModel.saveToGallery(saveToDeviceTarget)
            },
            onSaveToFiles = {
                viewModel.onSaveToDeviceDismissed()
                exportTarget = saveToDeviceTarget
                exportLauncher.launch(exportFileName(saveToDeviceTarget))
            },
            onDismiss = viewModel::onSaveToDeviceDismissed,
        )
    }

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
        val isImported = deleteTarget.isImported
        AlertDialog(
            onDismissRequest = viewModel::onDeleteDismissed,
            title = { Text(stringResource(if (isImported) R.string.library_remove_title else R.string.library_delete_title)) },
            text = { Text(stringResource(if (isImported) R.string.library_remove_body else R.string.library_delete_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::onDeleteConfirmed) {
                    Text(
                        stringResource(if (isImported) R.string.library_remove_confirm else R.string.library_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
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

@Composable
private fun ImportChooserDialog(onImportFile: () -> Unit, onImportFolder: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_import_chooser_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ChooserOptionRow(Icons.Default.InsertDriveFile, stringResource(R.string.library_import_file), onImportFile)
                ChooserOptionRow(Icons.Default.FolderOpen, stringResource(R.string.library_import_folder), onImportFolder)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_dialog_cancel)) } },
    )
}

@Composable
private fun SaveToDeviceDialog(onSaveToGallery: () -> Unit, onSaveToFiles: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_save_chooser_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ChooserOptionRow(Icons.Default.CloudDone, stringResource(R.string.library_save_to_gallery), onSaveToGallery)
                ChooserOptionRow(Icons.Default.FileDownload, stringResource(R.string.library_save_to_files), onSaveToFiles)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_dialog_cancel)) } },
    )
}

@Composable
private fun ChooserOptionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun LibraryScreenContent(
    uiState: LibraryUiState,
    onSearchQueryChanged: (String) -> Unit,
    onSortOrderChanged: (LibrarySortOrder) -> Unit,
    onAddMediaClicked: () -> Unit,
    onPlay: (MediaItemEntity) -> Unit,
    onShare: (MediaItemEntity) -> Unit,
    onSaveToDeviceRequested: (MediaItemEntity) -> Unit,
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
        item {
            MediaVaultTopBar(
                title = stringResource(R.string.library_title),
                actions = {
                    IconButton(onClick = onAddMediaClicked) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.library_add_media))
                    }
                },
            )
        }

        if (uiState.isImporting) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.library_importing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

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
                onSaveToDeviceRequested = onSaveToDeviceRequested,
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
    onSaveToDeviceRequested: (MediaItemEntity) -> Unit,
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
            MediaThumbnail(thumbnailUrl = item.thumbnailUrl, mediaType = item.mediaType)

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                Text(
                    text = libraryItemSubtitle(item),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val originBadge = libraryOriginBadge(item)
                if (originBadge != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            imageVector = originBadge.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = stringResource(originBadge.labelRes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
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
                    // Both actions need a real app-private file MediaVault itself owns — neither
                    // works meaningfully on an imported/content:// item (there's no permission to
                    // rename someone else's document, and "save to device" is meaningless for a
                    // file that's already outside MediaVault). Hidden rather than shown-disabled:
                    // a new user shouldn't have to guess why a control doesn't work.
                    if (!item.isImported) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.library_action_save_to_device)) },
                            leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                            enabled = !libraryItem.isMissing,
                            onClick = { menuExpanded = false; onSaveToDeviceRequested(item) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.library_action_rename)) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            enabled = !libraryItem.isMissing,
                            onClick = { menuExpanded = false; onRenameRequested(item) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(if (item.isImported) R.string.library_action_remove else R.string.library_action_delete)) },
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

private data class OriginBadge(val icon: ImageVector, val labelRes: Int)

/**
 * Null for a plain MediaVault-private download — the common case needs no badge at all. Otherwise
 * distinguishes two different reasons [MediaItemEntity.isImported] is true: a file the user
 * imported from outside MediaVault entirely ([MediaItemEntity.sourceDownloadTaskId] null) versus
 * one MediaVault downloaded itself and the user later moved to the Gallery (still has its
 * `sourceDownloadTaskId`) — see [com.mediavault.app.library.LibraryRepository.saveToGallery].
 */
private fun libraryOriginBadge(item: MediaItemEntity): OriginBadge? = when (item.origin()) {
    MediaOrigin.DOWNLOADED -> null
    MediaOrigin.SAVED_TO_GALLERY -> OriginBadge(Icons.Default.CloudDone, R.string.library_badge_in_gallery)
    MediaOrigin.IMPORTED -> OriginBadge(Icons.Default.FolderOpen, R.string.library_badge_imported)
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

