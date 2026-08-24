package com.mediavault.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediavault.app.storage.DownloadDestinationProvider
import com.mediavault.app.util.DeviceStatusProvider
import com.mediavault.core.common.AppResult
import com.mediavault.core.domain.download.DownloadEngine
import com.mediavault.core.domain.download.DownloadRequest
import com.mediavault.core.domain.download.PlaylistDownloadItem
import com.mediavault.core.domain.download.PlaylistDownloadRequest
import com.mediavault.core.domain.download.QualityDescriptor
import com.mediavault.core.domain.extractor.ExtractionResult
import com.mediavault.core.domain.extractor.ExtractorEngine
import com.mediavault.core.domain.extractor.MediaAnalysisResult
import com.mediavault.core.domain.extractor.PlaylistItem
import com.mediavault.core.model.MediaFormat
import com.mediavault.core.model.MediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A format that would leave video and audio in separate streams needs an FFmpeg merge MediaVault doesn't do yet. */
fun MediaFormat.isSelectableForDownload(): Boolean = hasAudio && !(hasVideo && !hasAudio)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val extractorEngine: ExtractorEngine,
    private val deviceStatusProvider: DeviceStatusProvider,
    private val downloadEngine: DownloadEngine,
    private val destinationStore: DownloadDestinationProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var analyzeJob: Job? = null
    private var activeTaskId: String? = null
    private var formatResolutionJob: Job? = null

    /** Which flow requested the SAF folder picker — resolved once [onDestinationFolderPicked] fires. */
    private var pendingDestinationAction: PendingDestinationAction? = null

    private enum class PendingDestinationAction { SINGLE, PLAYLIST }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val freeBytes = deviceStatusProvider.freeStorageBytes()
            val networkStatus = deviceStatusProvider.networkStatus()
            val destinationUri = destinationStore.currentTreeUri()
            _uiState.update {
                it.copy(freeStorageBytes = freeBytes, networkStatus = networkStatus, destinationTreeUri = destinationUri)
            }
        }
    }

    fun onUrlChanged(url: String) {
        _uiState.update { it.copy(url = url, errorMessage = null) }
    }

    fun analyze() {
        val url = _uiState.value.url.trim()
        if (url.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Paste a link first.") }
            return
        }

        cancelInFlightAnalysis()

        val taskId = UUID.randomUUID().toString()
        activeTaskId = taskId
        _uiState.update {
            it.copy(
                isAnalyzing = true,
                errorMessage = null,
                infoMessage = null,
                result = null,
                playlistSelection = PlaylistSelectionState(),
                selectedFormatId = null,
                justQueued = false,
            )
        }

        analyzeJob = viewModelScope.launch {
            when (val outcome = extractorEngine.analyze(url, taskId)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(isAnalyzing = false, result = outcome.data, errorMessage = null)
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(isAnalyzing = false, errorMessage = outcome.error.message)
                }
            }
        }
    }

    fun cancelInFlightAnalysis() {
        analyzeJob?.cancel()
        analyzeJob = null
        val taskId = activeTaskId
        activeTaskId = null
        if (taskId != null) {
            viewModelScope.launch { extractorEngine.cancel(taskId) }
            _uiState.update { it.copy(isAnalyzing = false) }
        }
    }

    // --- Single-item format selection & download --------------------------------------

    fun onFormatSelected(format: MediaFormat) {
        if (!format.isSelectableForDownload()) return
        _uiState.update { it.copy(selectedFormatId = format.formatId, justQueued = false) }
    }

    /** Called by the screen when Download is tapped. Triggers the SAF folder picker if no destination is set yet. */
    fun onDownloadClicked() {
        val state = _uiState.value
        if (state.selectedFormatId == null) return
        pendingDestinationAction = PendingDestinationAction.SINGLE
        if (state.destinationTreeUri == null) {
            _uiState.update { it.copy(awaitingDestinationPick = true) }
            return
        }
        enqueueSelectedFormat(state.destinationTreeUri)
    }

    fun onDestinationPickerDismissed() {
        pendingDestinationAction = null
        _uiState.update { it.copy(awaitingDestinationPick = false) }
    }

    /** Called by the screen once the user has picked a folder via ACTION_OPEN_DOCUMENT_TREE. */
    fun onDestinationFolderPicked(treeUri: String) {
        viewModelScope.launch {
            destinationStore.setTreeUri(treeUri)
            _uiState.update { it.copy(destinationTreeUri = treeUri, awaitingDestinationPick = false) }
            when (pendingDestinationAction) {
                PendingDestinationAction.SINGLE -> enqueueSelectedFormat(treeUri)
                PendingDestinationAction.PLAYLIST -> confirmPlaylistQueue(treeUri)
                null -> Unit
            }
            pendingDestinationAction = null
        }
    }

    fun consumeJustQueued() {
        _uiState.update { it.copy(justQueued = false) }
    }

    private fun enqueueSelectedFormat(destinationTreeUri: String) {
        val media = (_uiState.value.result as? ExtractionResult.Single)?.media ?: return
        val formatId = _uiState.value.selectedFormatId ?: return
        val format = media.formats.firstOrNull { it.formatId == formatId } ?: return
        val sourceUrl = media.webpageUrl ?: _uiState.value.url.trim()

        downloadEngine.enqueue(
            DownloadRequest(
                taskId = UUID.randomUUID().toString(),
                sourceUrl = sourceUrl,
                formatId = format.formatId,
                title = media.title,
                sourceName = media.sourceName,
                thumbnailUrl = media.thumbnailUrl,
                container = format.container,
                destinationTreeUri = destinationTreeUri,
                mediaType = if (format.hasVideo) MediaType.VIDEO else MediaType.AUDIO,
                expectedSizeBytes = format.estimatedSizeBytes,
                canResume = format.supportsResume,
                sourceMediaId = media.id,
            ),
        )

        _uiState.update { it.copy(justQueued = true, infoMessage = null) }
    }

    // --- Playlist selection ---------------------------------------------------------
    // Selection is real, functioning state; the "download" actions below only ever
    // report what *would* happen, since DownloadEngine has no implementation yet.

    /** Toggles one item, or — while range selection is active — supplies the range's start/end. */
    fun onPlaylistItemTapped(item: PlaylistItem) {
        if (!item.isAvailable) return

        val selection = _uiState.value.playlistSelection
        if (!selection.isRangeSelectionActive) {
            toggleItemSelected(item.id)
            return
        }

        val anchorId = selection.rangeAnchorId
        if (anchorId == null) {
            _uiState.update { it.copy(playlistSelection = selection.copy(rangeAnchorId = item.id)) }
            return
        }

        val items = currentPlaylistItems()
        val anchorIndex = items.indexOfFirst { it.id == anchorId }
        val endIndex = items.indexOfFirst { it.id == item.id }
        if (anchorIndex == -1 || endIndex == -1) {
            cancelSelection()
            return
        }

        val (from, to) = if (anchorIndex <= endIndex) anchorIndex to endIndex else endIndex to anchorIndex
        val rangeIds = items.subList(from, to + 1).filter { it.isAvailable }.map { it.id }.toSet()

        _uiState.update {
            it.copy(
                playlistSelection = PlaylistSelectionState(
                    selectedItemIds = it.playlistSelection.selectedItemIds + rangeIds,
                ),
            )
        }
    }

    private fun toggleItemSelected(itemId: String) {
        _uiState.update {
            val current = it.playlistSelection.selectedItemIds
            val updated = if (itemId in current) current - itemId else current + itemId
            it.copy(playlistSelection = it.playlistSelection.copy(selectedItemIds = updated))
        }
    }

    fun beginRangeSelection() {
        _uiState.update {
            it.copy(playlistSelection = it.playlistSelection.copy(isRangeSelectionActive = true, rangeAnchorId = null))
        }
    }

    fun cancelSelection() {
        _uiState.update { it.copy(playlistSelection = PlaylistSelectionState(), infoMessage = null) }
    }

    fun onSkipAlreadyDownloadedToggled(value: Boolean) {
        _uiState.update { it.copy(playlistSelection = it.playlistSelection.copy(skipAlreadyDownloaded = value)) }
    }

    fun downloadEntirePlaylist() {
        val items = currentPlaylistItems().filter { it.isAvailable }
        beginPlaylistDownloadSetup(items)
    }

    fun downloadSelectedItems() {
        val selectedIds = _uiState.value.playlistSelection.selectedItemIds
        if (selectedIds.isEmpty()) {
            _uiState.update { it.copy(infoMessage = "Select at least one item first.") }
            return
        }
        val items = currentPlaylistItems().filter { it.id in selectedIds }
        beginPlaylistDownloadSetup(items)
    }

    /** Resolves the first item's own format list so the user can pick one quality for the whole batch. */
    private fun beginPlaylistDownloadSetup(items: List<PlaylistItem>) {
        val firstWithUrl = items.firstOrNull { it.url != null }
        if (firstWithUrl == null) {
            _uiState.update { it.copy(infoMessage = "None of the selected items can be downloaded.") }
            return
        }

        _uiState.update {
            it.copy(playlistDownloadSetup = PlaylistDownloadSetupState(items = items, isResolvingFormats = true), infoMessage = null)
        }

        formatResolutionJob?.cancel()
        formatResolutionJob = viewModelScope.launch {
            val taskId = UUID.randomUUID().toString()
            when (val outcome = extractorEngine.analyze(firstWithUrl.url!!, taskId)) {
                is AppResult.Success -> {
                    val media = (outcome.data as? ExtractionResult.Single)?.media
                    _uiState.update { state ->
                        val setup = state.playlistDownloadSetup ?: return@update state
                        if (media == null) {
                            state.copy(playlistDownloadSetup = setup.copy(isResolvingFormats = false, errorMessage = "That item couldn't be resolved."))
                        } else {
                            state.copy(playlistDownloadSetup = setup.copy(isResolvingFormats = false, formatOptions = media.formats))
                        }
                    }
                }

                is AppResult.Failure -> _uiState.update { state ->
                    val setup = state.playlistDownloadSetup ?: return@update state
                    state.copy(playlistDownloadSetup = setup.copy(isResolvingFormats = false, errorMessage = outcome.error.message))
                }
            }
        }
    }

    fun onPlaylistFormatSelected(format: MediaFormat) {
        if (!format.isSelectableForDownload()) return
        _uiState.update { state ->
            val setup = state.playlistDownloadSetup ?: return@update state
            state.copy(playlistDownloadSetup = setup.copy(selectedFormatId = format.formatId))
        }
    }

    fun cancelPlaylistDownloadSetup() {
        formatResolutionJob?.cancel()
        formatResolutionJob = null
        _uiState.update { it.copy(playlistDownloadSetup = null) }
    }

    /** Called by the screen when Queue is tapped in the playlist setup step. */
    fun onQueuePlaylistClicked() {
        val setup = _uiState.value.playlistDownloadSetup ?: return
        if (setup.selectedFormatId == null) return
        pendingDestinationAction = PendingDestinationAction.PLAYLIST
        val destination = _uiState.value.destinationTreeUri
        if (destination == null) {
            _uiState.update { it.copy(awaitingDestinationPick = true) }
            return
        }
        confirmPlaylistQueue(destination)
    }

    private fun confirmPlaylistQueue(destinationTreeUri: String) {
        val playlist = (_uiState.value.result as? ExtractionResult.Playlist)?.playlist ?: return
        val setup = _uiState.value.playlistDownloadSetup ?: return
        val formatId = setup.selectedFormatId ?: return
        val format = setup.formatOptions.firstOrNull { it.formatId == formatId } ?: return

        val items = setup.items.mapNotNull { item ->
            val url = item.url ?: return@mapNotNull null
            PlaylistDownloadItem(
                sourceUrl = url,
                sourceMediaId = item.id,
                itemIndex = item.index,
                title = item.title,
                thumbnailUrl = item.thumbnailUrl,
            )
        }
        if (items.isEmpty()) return

        downloadEngine.enqueuePlaylist(
            PlaylistDownloadRequest(
                playlistId = UUID.randomUUID().toString(),
                playlistTitle = playlist.title,
                playlistThumbnailUrl = playlist.thumbnailUrl,
                sourceName = playlist.sourceName,
                qualityDescriptor = QualityDescriptor.from(format),
                destinationTreeUri = destinationTreeUri,
                skipAlreadyDownloaded = _uiState.value.playlistSelection.skipAlreadyDownloaded,
                items = items,
            ),
        )

        _uiState.update {
            it.copy(playlistDownloadSetup = null, playlistSelection = PlaylistSelectionState(), justQueued = true)
        }
    }

    private fun currentPlaylistItems(): List<PlaylistItem> =
        (_uiState.value.result as? ExtractionResult.Playlist)?.playlist?.items.orEmpty()

    override fun onCleared() {
        cancelInFlightAnalysis()
        formatResolutionJob?.cancel()
        super.onCleared()
    }
}
