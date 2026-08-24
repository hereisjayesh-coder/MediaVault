package com.mediavault.app.ui.screens.home

import com.mediavault.app.util.NetworkStatus
import com.mediavault.core.domain.extractor.ExtractionResult
import com.mediavault.core.domain.extractor.PlaylistItem
import com.mediavault.core.model.MediaFormat

data class HomeUiState(
    val url: String = "",
    val isAnalyzing: Boolean = false,
    val errorMessage: String? = null,
    /** Non-error status feedback, e.g. confirming a selection action that can't actually download yet. */
    val infoMessage: String? = null,
    val result: ExtractionResult? = null,
    val playlistSelection: PlaylistSelectionState = PlaylistSelectionState(),
    /** Real device status (storage free space, network transport) — read once when Home loads. */
    val freeStorageBytes: Long? = null,
    val networkStatus: NetworkStatus? = null,
    /** The one format id the user has chosen for a [ExtractionResult.Single] result, if any. */
    val selectedFormatId: String? = null,
    /** SAF tree URI of the folder downloads are saved to; null until the user has picked one. */
    val destinationTreeUri: String? = null,
    /** True right after Download is tapped with no destination set yet — the screen must launch the folder picker. */
    val awaitingDestinationPick: Boolean = false,
    /** True once a download has just been queued, so the screen can offer to jump to Downloads. */
    val justQueued: Boolean = false,
    /** Non-null while the user is choosing one quality to apply to a playlist download — see [HomeViewModel]. */
    val playlistDownloadSetup: PlaylistDownloadSetupState? = null,
)

/**
 * Selection state for a [ExtractionResult.Playlist] result. Selection is a real, persisted
 * UI concept; actually starting a download goes through [PlaylistDownloadSetupState] first
 * so the user can pick one quality for every selected item.
 */
data class PlaylistSelectionState(
    val selectedItemIds: Set<String> = emptySet(),
    val isRangeSelectionActive: Boolean = false,
    /** First item tapped after entering range-selection mode; null while waiting for it. */
    val rangeAnchorId: String? = null,
    /** When true, an item already downloaded successfully before is skipped rather than re-queued. */
    val skipAlreadyDownloaded: Boolean = true,
)

/**
 * The "pick one quality, then queue" step between selecting playlist items and actually
 * calling [com.mediavault.core.domain.download.DownloadEngine.enqueuePlaylist]. Quality is
 * resolved from the *first* selected item's own format list (playlist items don't carry
 * formats up front); every other item is matched against that same quality independently
 * once queued.
 */
data class PlaylistDownloadSetupState(
    val items: List<PlaylistItem>,
    val isResolvingFormats: Boolean = true,
    val formatOptions: List<MediaFormat> = emptyList(),
    val selectedFormatId: String? = null,
    val errorMessage: String? = null,
)
