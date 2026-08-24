package com.mediavault.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediavault.core.common.AppResult
import com.mediavault.core.domain.extractor.ExtractionResult
import com.mediavault.core.domain.extractor.ExtractorEngine
import com.mediavault.core.domain.extractor.PlaylistItem
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val extractorEngine: ExtractorEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var analyzeJob: Job? = null
    private var activeTaskId: String? = null

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

    fun downloadEntirePlaylist() {
        val availableCount = currentPlaylistItems().count { it.isAvailable }
        _uiState.update {
            it.copy(infoMessage = "Downloading isn't implemented yet — would queue all $availableCount available item(s).")
        }
    }

    fun downloadSelectedItems() {
        val selectedCount = _uiState.value.playlistSelection.selectedItemIds.size
        if (selectedCount == 0) {
            _uiState.update { it.copy(infoMessage = "Select at least one item first.") }
            return
        }
        _uiState.update {
            it.copy(infoMessage = "Downloading isn't implemented yet — would queue $selectedCount selected item(s).")
        }
    }

    private fun currentPlaylistItems(): List<PlaylistItem> =
        (_uiState.value.result as? ExtractionResult.Playlist)?.playlist?.items.orEmpty()

    override fun onCleared() {
        cancelInFlightAnalysis()
        super.onCleared()
    }
}
