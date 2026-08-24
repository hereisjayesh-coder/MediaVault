package com.mediavault.app.ui.screens.home

import com.mediavault.core.domain.extractor.ExtractionResult

data class HomeUiState(
    val url: String = "",
    val isAnalyzing: Boolean = false,
    val errorMessage: String? = null,
    /** Non-error status feedback, e.g. confirming a selection action that can't actually download yet. */
    val infoMessage: String? = null,
    val result: ExtractionResult? = null,
    val playlistSelection: PlaylistSelectionState = PlaylistSelectionState(),
)

/**
 * Selection state for a [ExtractionResult.Playlist] result. Selection is purely a UI
 * concept at this stage — nothing here starts a download; see [HomeViewModel].
 */
data class PlaylistSelectionState(
    val selectedItemIds: Set<String> = emptySet(),
    val isRangeSelectionActive: Boolean = false,
    /** First item tapped after entering range-selection mode; null while waiting for it. */
    val rangeAnchorId: String? = null,
)
