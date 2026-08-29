package com.mediavault.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediavault.app.util.DeviceStatusProvider
import com.mediavault.core.common.AppResult
import com.mediavault.core.domain.download.DownloadEngine
import com.mediavault.core.domain.download.DownloadRequest
import com.mediavault.core.domain.download.FormatSelectionModel
import com.mediavault.core.domain.download.PlaylistDownloadContext
import com.mediavault.core.domain.download.PlaylistDownloadItem
import com.mediavault.core.domain.download.PlaylistDownloadRequest
import com.mediavault.core.domain.download.QualityDescriptor
import com.mediavault.core.domain.download.QualityTier
import com.mediavault.core.domain.download.ResolvedSelection
import com.mediavault.core.domain.download.SelectedAudioTrack
import com.mediavault.core.domain.download.resolveSelection
import com.mediavault.core.domain.download.toFormatSelectionModel
import com.mediavault.core.domain.extractor.ExtractionResult
import com.mediavault.core.domain.extractor.ExtractorEngine
import com.mediavault.core.domain.extractor.MediaAnalysisResult
import com.mediavault.core.domain.extractor.MediaCollectionItem
import com.mediavault.core.domain.extractor.MediaCollectionResult
import com.mediavault.core.domain.extractor.PlaylistAnalysisResult
import com.mediavault.core.domain.extractor.PlaylistItem
import com.mediavault.core.domain.network.NetworkPolicyDecision
import com.mediavault.core.domain.network.NetworkPolicyManager
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
                formatSelection = null,
                playlistSelection = PlaylistSelectionState(),
                selectedQuality = SelectedQualityState(),
                justQueued = false,
            )
        }

        analyzeJob = viewModelScope.launch {
            when (val outcome = extractorEngine.analyze(url, taskId)) {
                is AppResult.Success -> _uiState.update {
                    val model = (outcome.data as? ExtractionResult.Single)?.media?.formats?.toFormatSelectionModel()
                    it.copy(isAnalyzing = false, result = outcome.data, formatSelection = model, errorMessage = null)
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

    fun onQualityTierSelected(tier: QualityTier) {
        val model = _uiState.value.formatSelection ?: return
        _uiState.update { it.copy(selectedQuality = it.selectedQuality.withTierSelected(tier, model), justQueued = false) }
    }

    fun onVideoVariantSelected(formatId: String) {
        _uiState.update { it.copy(selectedQuality = it.selectedQuality.copy(videoVariantFormatId = formatId), justQueued = false) }
    }

    fun onIncludeMultipleAudioToggled(enabled: Boolean) {
        _uiState.update {
            val current = it.selectedQuality
            // Turning multi-select off collapses back to at most one track rather than clearing
            // the pick entirely — the user's most recent single choice is the least surprising
            // thing to keep selected.
            val trimmed = if (enabled) current.selectedAudioFormatIds else current.selectedAudioFormatIds.take(1).toSet()
            it.copy(selectedQuality = current.copy(includeMultipleAudio = enabled, selectedAudioFormatIds = trimmed))
        }
    }

    /** Single-select (radio) while [SelectedQualityState.includeMultipleAudio] is off — matches the pre-redesign audio picker's own behavior; toggling it on switches this to checkbox-style multi-select. */
    fun onAudioTrackToggled(formatId: String) {
        _uiState.update {
            val current = it.selectedQuality
            val updatedIds = if (current.includeMultipleAudio) {
                if (formatId in current.selectedAudioFormatIds) current.selectedAudioFormatIds - formatId else current.selectedAudioFormatIds + formatId
            } else {
                setOf(formatId)
            }
            it.copy(selectedQuality = current.copy(selectedAudioFormatIds = updatedIds), justQueued = false)
        }
    }

    private fun currentResolvedSelection(): ResolvedSelection? =
        _uiState.value.formatSelection?.resolve(_uiState.value.selectedQuality)

    /** Called by the screen when Download is tapped. Downloads are app-private by default — no folder picker needed. */
    fun onDownloadClicked() {
        if (currentResolvedSelection() == null) return
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
            is NetworkWarning.Collection -> {
                _uiState.update { it.copy(networkWarning = null) }
                confirmCollectionQueue(pendingCollectionDownloadItems, bypassNetworkCheck = true)
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
        val selection = currentResolvedSelection() ?: return
        val sourceUrl = media.webpageUrl ?: _uiState.value.url.trim()
        val estimatedSizeBytes = selection.combinedEstimatedSizeBytes ?: 0L

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
                        enqueueDownloadRequest(media, selection, sourceUrl)
                        _uiState.update { it.copy(justQueued = true, infoMessage = "Waiting for Wi-Fi — this exceeds your per-download mobile-data limit.") }
                        return@launch
                    }
                    NetworkPolicyDecision.Allow -> Unit
                }
            }
            enqueueDownloadRequest(media, selection, sourceUrl)
            _uiState.update { it.copy(justQueued = true, infoMessage = null) }
        }
    }

    private fun enqueueDownloadRequest(media: MediaAnalysisResult, selection: ResolvedSelection, sourceUrl: String) {
        val primaryFormatId = selection.primaryFormatId ?: return
        downloadEngine.enqueue(
            DownloadRequest(
                taskId = UUID.randomUUID().toString(),
                sourceUrl = sourceUrl,
                formatId = primaryFormatId,
                audioTracks = selection.audioFormats.map { SelectedAudioTrack(it.formatId, it.languageCode) }.takeIf { selection.requiresProcessing }.orEmpty(),
                title = media.title,
                sourceName = media.sourceName,
                thumbnailUrl = media.thumbnailUrl,
                container = selection.outputContainer,
                mediaType = if (selection.videoFormat != null) MediaType.VIDEO else MediaType.AUDIO,
                expectedSizeBytes = selection.combinedEstimatedSizeBytes,
                durationSeconds = media.durationSeconds,
                resolutionLabel = selection.videoFormat?.resolutionLabel,
                // A split video+audio task is never byte-offset-resumable — see MediaVaultDownloadEngine's pause/cancel handling.
                canResume = if (selection.requiresProcessing) false else selection.videoFormat?.supportsResume ?: selection.audioFormats.firstOrNull()?.supportsResume ?: false,
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
        handleItemTapped(item.id, currentPlaylistItems().filter { it.isAvailable }.map { it.id })
    }

    /** Same toggle/range-select behavior as [onPlaylistItemTapped], for an image collection's items — every collection item is always available (an unresolvable one simply never appears in [MediaCollectionResult.items]), unlike a playlist entry. */
    fun onCollectionItemTapped(item: MediaCollectionItem) {
        handleItemTapped(item.id, currentCollectionItems().map { it.id })
    }

    /**
     * Shared toggle/range-selection logic for both playlist and image-collection multi-select
     * — the algorithm (toggle outside range mode; anchor, then select the enclosed span, once
     * range mode is active) doesn't depend on what kind of item is being selected, only on
     * stable ids and their order within [selectableIds].
     */
    private fun handleItemTapped(itemId: String, selectableIds: List<String>) {
        val selection = _uiState.value.playlistSelection
        if (!selection.isRangeSelectionActive) {
            toggleItemSelected(itemId)
            return
        }

        val anchorId = selection.rangeAnchorId
        if (anchorId == null) {
            _uiState.update { it.copy(playlistSelection = selection.copy(rangeAnchorId = itemId)) }
            return
        }

        val anchorIndex = selectableIds.indexOf(anchorId)
        val endIndex = selectableIds.indexOf(itemId)
        if (anchorIndex == -1 || endIndex == -1) {
            cancelSelection()
            return
        }

        val (from, to) = if (anchorIndex <= endIndex) anchorIndex to endIndex else endIndex to anchorIndex
        val rangeIds = selectableIds.subList(from, to + 1).toSet()

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
                            state.copy(playlistDownloadSetup = setup.copy(isResolvingFormats = false, formatSelection = media.formats.toFormatSelectionModel()))
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

    fun onPlaylistQualityTierSelected(tier: QualityTier) {
        _uiState.update { state ->
            val setup = state.playlistDownloadSetup ?: return@update state
            val model = setup.formatSelection ?: return@update state
            state.copy(playlistDownloadSetup = setup.copy(selectedQuality = setup.selectedQuality.withTierSelected(tier, model)))
        }
    }

    fun onPlaylistVideoVariantSelected(formatId: String) {
        _uiState.update { state ->
            val setup = state.playlistDownloadSetup ?: return@update state
            state.copy(playlistDownloadSetup = setup.copy(selectedQuality = setup.selectedQuality.copy(videoVariantFormatId = formatId)))
        }
    }

    fun onPlaylistIncludeMultipleAudioToggled(enabled: Boolean) {
        _uiState.update { state ->
            val setup = state.playlistDownloadSetup ?: return@update state
            val current = setup.selectedQuality
            val trimmed = if (enabled) current.selectedAudioFormatIds else current.selectedAudioFormatIds.take(1).toSet()
            state.copy(playlistDownloadSetup = setup.copy(selectedQuality = current.copy(includeMultipleAudio = enabled, selectedAudioFormatIds = trimmed)))
        }
    }

    fun onPlaylistAudioTrackToggled(formatId: String) {
        _uiState.update { state ->
            val setup = state.playlistDownloadSetup ?: return@update state
            val current = setup.selectedQuality
            val updatedIds = if (current.includeMultipleAudio) {
                if (formatId in current.selectedAudioFormatIds) current.selectedAudioFormatIds - formatId else current.selectedAudioFormatIds + formatId
            } else {
                setOf(formatId)
            }
            state.copy(playlistDownloadSetup = setup.copy(selectedQuality = current.copy(selectedAudioFormatIds = updatedIds)))
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
        if (setup.formatSelection?.resolve(setup.selectedQuality) == null) return
        confirmPlaylistQueue(bypassNetworkCheck = false)
    }

    /** Same [NetworkPolicyManager] gate as the single-item path, priced against the whole batch — see [beginEnqueueSelectedFormat]. */
    private fun confirmPlaylistQueue(bypassNetworkCheck: Boolean) {
        val playlist = (_uiState.value.result as? ExtractionResult.Playlist)?.playlist ?: return
        val setup = _uiState.value.playlistDownloadSetup ?: return
        val model = setup.formatSelection ?: return
        val selection = model.resolve(setup.selectedQuality) ?: return

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

        val estimatedTotalBytes = estimatedPlaylistTotalSizeBytes(selection, items.size) ?: 0L

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
                        enqueuePlaylistRequest(playlist, selection, items)
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
            enqueuePlaylistRequest(playlist, selection, items)
            _uiState.update {
                it.copy(playlistDownloadSetup = null, playlistSelection = PlaylistSelectionState(), justQueued = true, infoMessage = null)
            }
        }
    }

    private fun enqueuePlaylistRequest(playlist: PlaylistAnalysisResult, selection: ResolvedSelection, items: List<PlaylistDownloadItem>) {
        downloadEngine.enqueuePlaylist(
            PlaylistDownloadRequest(
                playlistId = UUID.randomUUID().toString(),
                playlistTitle = playlist.title,
                playlistThumbnailUrl = playlist.thumbnailUrl,
                sourceName = playlist.sourceName,
                qualityDescriptor = QualityDescriptor.from(selection.videoFormat, selection.audioFormats),
                skipAlreadyDownloaded = _uiState.value.playlistSelection.skipAlreadyDownloaded,
                items = items,
            ),
        )
    }

    private fun currentPlaylistItems(): List<PlaylistItem> =
        (_uiState.value.result as? ExtractionResult.Playlist)?.playlist?.items.orEmpty()

    // --- Image collection (single image or carousel) download -------------------------
    // Unlike a video playlist, every item's direct URL is already known from the one
    // analyze() call that produced the ExtractionResult.Collection — there is no per-item
    // "resolve its own format list later" step, so this reuses DownloadEngine.enqueue()
    // (the plain single-item entry point) once per selected image, grouped via
    // DownloadRequest.playlistContext, rather than DownloadEngine.enqueuePlaylist (built
    // for video items that each need independent resolution). See PROJECT_MASTER.md's
    // Instagram image support decision log entry for the full reasoning.

    private fun currentCollectionItems(): List<MediaCollectionItem> =
        (_uiState.value.result as? ExtractionResult.Collection)?.collection?.items.orEmpty()

    /** A single-image post is just a one-item collection — the same entry point handles both, no separate "Download" action needed. */
    fun downloadEntireCollection() {
        beginCollectionDownload(currentCollectionItems())
    }

    fun downloadSelectedCollectionItems() {
        val selectedIds = _uiState.value.playlistSelection.selectedItemIds
        if (selectedIds.isEmpty()) {
            _uiState.update { it.copy(infoMessage = "Select at least one item first.") }
            return
        }
        beginCollectionDownload(currentCollectionItems().filter { it.id in selectedIds })
    }

    /** Remembers the pending batch across a [NetworkWarning.Collection] confirmation — mirrors `analyzeJob`/`activeTaskId` as plain ViewModel bookkeeping the UI never renders, rather than growing [HomeUiState]. */
    private var pendingCollectionDownloadItems: List<MediaCollectionItem> = emptyList()

    private fun beginCollectionDownload(items: List<MediaCollectionItem>) {
        if (items.isEmpty()) return
        pendingCollectionDownloadItems = items
        confirmCollectionQueue(items, bypassNetworkCheck = false)
    }

    /** Same [NetworkPolicyManager] gate as the single-item/playlist paths — see [beginEnqueueSelectedFormat]. Image sizes are usually unknown ahead of download (no guessed number), so this most often evaluates against a 0-byte estimate and simply allows — a real block/warn still applies whenever a size is actually known. */
    private fun confirmCollectionQueue(items: List<MediaCollectionItem>, bypassNetworkCheck: Boolean) {
        val collection = (_uiState.value.result as? ExtractionResult.Collection)?.collection ?: return
        val estimatedTotalBytes = items.sumOf { it.estimatedSizeBytes ?: 0L }

        viewModelScope.launch {
            if (!bypassNetworkCheck) {
                when (val decision = networkPolicyManager.evaluate(estimatedTotalBytes)) {
                    is NetworkPolicyDecision.Block -> {
                        _uiState.update { it.copy(errorMessage = decision.reason) }
                        return@launch
                    }
                    is NetworkPolicyDecision.Warn -> {
                        _uiState.update { it.copy(networkWarning = NetworkWarning.Collection(decision.reason)) }
                        return@launch
                    }
                    is NetworkPolicyDecision.QueueForWifi -> {
                        enqueueCollectionItems(collection, items)
                        _uiState.update {
                            it.copy(
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
            enqueueCollectionItems(collection, items)
            _uiState.update { it.copy(playlistSelection = PlaylistSelectionState(), justQueued = true, infoMessage = null) }
        }
    }

    /**
     * Enqueues one plain [DownloadRequest] per image via the existing single-item
     * [DownloadEngine.enqueue] — no new download-engine method. A genuine multi-image
     * carousel groups its tasks via [PlaylistDownloadContext] (reusing the exact same
     * grouping/progress-aggregation the Downloads screen's "Playlists" section already
     * renders); a single image enqueues as an ordinary standalone task instead, since a
     * "playlist of one" would show a group header for nothing. Skips an item already
     * downloaded before when the same toggle used for playlists is on, checked one at a
     * time so a partially-duplicate batch still queues the genuinely-new items.
     */
    private suspend fun enqueueCollectionItems(collection: MediaCollectionResult, items: List<MediaCollectionItem>) {
        val groupId = if (items.size > 1) UUID.randomUUID().toString() else null
        val skipAlreadyDownloaded = _uiState.value.playlistSelection.skipAlreadyDownloaded
        val sourceUrl = collection.webpageUrl ?: _uiState.value.url.trim()

        for (item in items) {
            if (skipAlreadyDownloaded && downloadEngine.isAlreadyDownloaded(item.id)) continue
            downloadEngine.enqueue(
                DownloadRequest(
                    taskId = UUID.randomUUID().toString(),
                    sourceUrl = sourceUrl,
                    formatId = item.index.toString(),
                    // Numbered against the *full* collection's item count, not the size of
                    // this particular download batch — downloading only items 2 and 4 out of
                    // 5 must still label them "(2/5)"/"(4/5)", never "(2/2)"/"(4/2)" against a
                    // batch size that has nothing to do with the post's actual shape.
                    title = collectionItemTitle(collection, item, collection.items.size),
                    sourceName = collection.sourceName,
                    thumbnailUrl = item.thumbnailUrl,
                    container = imageContainerFor(item.imageUrl),
                    mediaType = MediaType.IMAGE,
                    expectedSizeBytes = item.estimatedSizeBytes,
                    canResume = false,
                    sourceMediaId = item.id,
                    playlistContext = groupId?.let {
                        PlaylistDownloadContext(
                            playlistId = it,
                            itemIndex = item.index,
                            playlistTitle = collection.title.ifBlank { null },
                            playlistThumbnailUrl = collection.thumbnailUrl,
                        )
                    },
                ),
            )
        }
    }

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
