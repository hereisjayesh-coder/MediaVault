package com.mediavault.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediavault.app.util.DeviceStatusProvider
import com.mediavault.core.common.AppResult
import com.mediavault.core.domain.download.DownloadEngine
import com.mediavault.core.domain.download.DownloadOption
import com.mediavault.core.domain.download.DownloadRequest
import com.mediavault.core.domain.download.PlaylistDownloadItem
import com.mediavault.core.domain.download.PlaylistDownloadRequest
import com.mediavault.core.domain.download.QualityDescriptor
import com.mediavault.core.domain.download.buildDownloadOptions
import com.mediavault.core.domain.extractor.ExtractionResult
import com.mediavault.core.domain.extractor.ExtractorEngine
import com.mediavault.core.domain.extractor.MediaAnalysisResult
import com.mediavault.core.domain.extractor.PlaylistAnalysisResult
import com.mediavault.core.domain.extractor.PlaylistItem
import com.mediavault.core.domain.network.NetworkPolicyDecision
import com.mediavault.core.domain.network.NetworkPolicyManager
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

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val extractorEngine: ExtractorEngine,
    private val deviceStatusProvider: DeviceStatusProvider,
    private val downloadEngine: DownloadEngine,
    private val networkPolicyManager: NetworkPolicyManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var analyzeJob: Job? = null
    private var activeTaskId: String? = null
    private var formatResolutionJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val freeBytes = deviceStatusProvider.freeStorageBytes()
            val networkStatus = deviceStatusProvider.networkStatus()
            _uiState.update { it.copy(freeStorageBytes = freeBytes, networkStatus = networkStatus) }
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
                downloadOptions = emptyList(),
                playlistSelection = PlaylistSelectionState(),
                selectedFormatId = null,
                justQueued = false,
            )
        }

        analyzeJob = viewModelScope.launch {
            when (val outcome = extractorEngine.analyze(url, taskId)) {
                is AppResult.Success -> _uiState.update {
                    val options = (outcome.data as? ExtractionResult.Single)?.media?.formats?.let(::buildDownloadOptions).orEmpty()
                    it.copy(isAnalyzing = false, result = outcome.data, downloadOptions = options, errorMessage = null)
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

    fun onDownloadOptionSelected(option: DownloadOption) {
        if (!option.isSelectable) return
        _uiState.update { it.copy(selectedFormatId = option.id, justQueued = false) }
    }

    /** Called by the screen when Download is tapped. Downloads are app-private by default — no folder picker needed. */
    fun onDownloadClicked() {
        if (_uiState.value.selectedFormatId == null) return
        beginEnqueueSelectedFormat(bypassNetworkCheck = false)
    }

    fun consumeJustQueued() {
        _uiState.update { it.copy(justQueued = false) }
    }

    /** Called when the user confirms the "download anyway" warning — see [NetworkWarning]. */
    fun onNetworkWarningConfirmed() {
        when (_uiState.value.networkWarning) {
            is NetworkWarning.Single -> {
                _uiState.update { it.copy(networkWarning = null) }
                beginEnqueueSelectedFormat(bypassNetworkCheck = true)
            }
            is NetworkWarning.Playlist -> {
                _uiState.update { it.copy(networkWarning = null) }
                confirmPlaylistQueue(bypassNetworkCheck = true)
            }
            null -> Unit
        }
    }

    fun onNetworkWarningDismissed() {
        _uiState.update { it.copy(networkWarning = null) }
    }

    /**
     * Every enqueue — single or playlist — goes through [NetworkPolicyManager.evaluate] first, so
     * the user learns about a mobile-data block/warning at the moment they tap Download, not only
     * later in the Downloads list. [NetworkPolicyManager] stays the sole owner of the actual
     * budget/limit logic (see its own KDoc); this only reacts to the [NetworkPolicyDecision] it
     * returns. A blocked download is never silently downgraded to a smaller quality — it's simply
     * not queued, with the reason shown to the user.
     */
    private fun beginEnqueueSelectedFormat(bypassNetworkCheck: Boolean) {
        val media = (_uiState.value.result as? ExtractionResult.Single)?.media ?: return
        val optionId = _uiState.value.selectedFormatId ?: return
        val option = _uiState.value.downloadOptions.firstOrNull { it.id == optionId } ?: return
        // Direct: whichever of the two is present (a muxed format sets only videoFormat; an
        // audio-only format sets only audioFormat). Paired: always the video-only format —
        // DownloadRequest.formatId is documented as "the video format when audioFormatId is set".
        val primaryFormat = option.videoFormat ?: option.audioFormat ?: return
        val sourceUrl = media.webpageUrl ?: _uiState.value.url.trim()
        val estimatedSizeBytes = option.combinedEstimatedSizeBytes ?: 0L

        viewModelScope.launch {
            if (!bypassNetworkCheck) {
                when (val decision = networkPolicyManager.evaluate(estimatedSizeBytes)) {
                    is NetworkPolicyDecision.Block -> {
                        _uiState.update { it.copy(errorMessage = decision.reason) }
                        return@launch
                    }
                    is NetworkPolicyDecision.Warn -> {
                        _uiState.update { it.copy(networkWarning = NetworkWarning.Single(decision.reason)) }
                        return@launch
                    }
                    is NetworkPolicyDecision.QueueForWifi -> {
                        enqueueDownloadRequest(media, option, primaryFormat, sourceUrl)
                        _uiState.update { it.copy(justQueued = true, infoMessage = "Waiting for Wi-Fi — this exceeds your per-download mobile-data limit.") }
                        return@launch
                    }
                    NetworkPolicyDecision.Allow -> Unit
                }
            }
            enqueueDownloadRequest(media, option, primaryFormat, sourceUrl)
            _uiState.update { it.copy(justQueued = true, infoMessage = null) }
        }
    }

    private fun enqueueDownloadRequest(
        media: MediaAnalysisResult,
        option: DownloadOption,
        primaryFormat: MediaFormat,
        sourceUrl: String,
    ) {
        downloadEngine.enqueue(
            DownloadRequest(
                taskId = UUID.randomUUID().toString(),
                sourceUrl = sourceUrl,
                formatId = primaryFormat.formatId,
                audioFormatId = option.audioFormat?.formatId.takeIf { option.requiresProcessing },
                title = media.title,
                sourceName = media.sourceName,
                thumbnailUrl = media.thumbnailUrl,
                container = option.outputContainer,
                mediaType = if (option.videoFormat != null) MediaType.VIDEO else MediaType.AUDIO,
                expectedSizeBytes = option.combinedEstimatedSizeBytes,
                durationSeconds = media.durationSeconds,
                resolutionLabel = option.videoFormat?.resolutionLabel,
                // A split video+audio task is never byte-offset-resumable — see MediaVaultDownloadEngine's pause/cancel handling.
                canResume = if (option.requiresProcessing) false else primaryFormat.supportsResume,
                sourceMediaId = media.id,
            ),
        )
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
                            state.copy(playlistDownloadSetup = setup.copy(isResolvingFormats = false, downloadOptions = buildDownloadOptions(media.formats)))
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

    fun onPlaylistOptionSelected(option: DownloadOption) {
        if (!option.isSelectable) return
        _uiState.update { state ->
            val setup = state.playlistDownloadSetup ?: return@update state
            state.copy(playlistDownloadSetup = setup.copy(selectedFormatId = option.id))
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
        confirmPlaylistQueue(bypassNetworkCheck = false)
    }

    /** Same [NetworkPolicyManager] gate as the single-item path, priced against the whole batch — see [beginEnqueueSelectedFormat]. */
    private fun confirmPlaylistQueue(bypassNetworkCheck: Boolean) {
        val playlist = (_uiState.value.result as? ExtractionResult.Playlist)?.playlist ?: return
        val setup = _uiState.value.playlistDownloadSetup ?: return
        val optionId = setup.selectedFormatId ?: return
        val option = setup.downloadOptions.firstOrNull { it.id == optionId } ?: return

        val items = setup.items.mapNotNull { item ->
            val url = item.url ?: return@mapNotNull null
            PlaylistDownloadItem(
                sourceUrl = url,
                sourceMediaId = item.id,
                itemIndex = item.index,
                title = item.title,
                thumbnailUrl = item.thumbnailUrl,
                durationSeconds = item.durationSeconds,
            )
        }
        if (items.isEmpty()) return

        val estimatedTotalBytes = estimatedPlaylistTotalSizeBytes(option, items.size) ?: 0L

        viewModelScope.launch {
            if (!bypassNetworkCheck) {
                when (val decision = networkPolicyManager.evaluate(estimatedTotalBytes)) {
                    is NetworkPolicyDecision.Block -> {
                        _uiState.update { it.copy(errorMessage = decision.reason) }
                        return@launch
                    }
                    is NetworkPolicyDecision.Warn -> {
                        _uiState.update { it.copy(networkWarning = NetworkWarning.Playlist(decision.reason)) }
                        return@launch
                    }
                    is NetworkPolicyDecision.QueueForWifi -> {
                        enqueuePlaylistRequest(playlist, option, items)
                        _uiState.update {
                            it.copy(
                                playlistDownloadSetup = null,
                                playlistSelection = PlaylistSelectionState(),
                                justQueued = true,
                                infoMessage = "Waiting for Wi-Fi — this exceeds your per-download mobile-data limit.",
                            )
                        }
                        return@launch
                    }
                    NetworkPolicyDecision.Allow -> Unit
                }
            }
            enqueuePlaylistRequest(playlist, option, items)
            _uiState.update {
                it.copy(playlistDownloadSetup = null, playlistSelection = PlaylistSelectionState(), justQueued = true, infoMessage = null)
            }
        }
    }

    private fun enqueuePlaylistRequest(playlist: PlaylistAnalysisResult, option: DownloadOption, items: List<PlaylistDownloadItem>) {
        downloadEngine.enqueuePlaylist(
            PlaylistDownloadRequest(
                playlistId = UUID.randomUUID().toString(),
                playlistTitle = playlist.title,
                playlistThumbnailUrl = playlist.thumbnailUrl,
                sourceName = playlist.sourceName,
                qualityDescriptor = QualityDescriptor.from(option),
                skipAlreadyDownloaded = _uiState.value.playlistSelection.skipAlreadyDownloaded,
                items = items,
            ),
        )
    }

    private fun currentPlaylistItems(): List<PlaylistItem> =
        (_uiState.value.result as? ExtractionResult.Playlist)?.playlist?.items.orEmpty()

    /**
     * Resets everything about an in-progress or completed link analysis back to the clean
     * default — called whenever `HomeScreen` is freshly (re-)entered (see its own
     * `remember(Unit)`), since Home's `NavBackStackEntry` (and this ViewModel) is never
     * destroyed by tab switches — it's the nav graph's start destination, so
     * `MediaVaultNavHost`'s popUpTo always excludes it. Without an explicit reset, a stale
     * analysis result would still be showing the next time the user tapped the Home tab.
     * Cancels any in-flight analysis/format-resolution first so a late result can't land after
     * the reset. `freeStorageBytes`/`networkStatus` are deliberately kept, not re-fetched — that
     * device status hasn't gone stale just because the user switched tabs.
     */
    fun resetToCleanState() {
        cancelInFlightAnalysis()
        formatResolutionJob?.cancel()
        formatResolutionJob = null
        _uiState.update { HomeUiState(freeStorageBytes = it.freeStorageBytes, networkStatus = it.networkStatus) }
    }

    override fun onCleared() {
        cancelInFlightAnalysis()
        formatResolutionJob?.cancel()
        super.onCleared()
    }
}
