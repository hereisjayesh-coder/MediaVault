package com.mediavault.app.ui.screens.home

import com.mediavault.app.util.NetworkStatus
import com.mediavault.core.domain.download.DownloadOption
import com.mediavault.core.domain.extractor.ExtractionResult
import com.mediavault.core.domain.extractor.PlaylistItem

data class HomeUiState(
    val url: String = "",
    val isAnalyzing: Boolean = false,
    val errorMessage: String? = null,
    /** Non-error status feedback, e.g. confirming a selection action that can't actually download yet. */
    val infoMessage: String? = null,
    val result: ExtractionResult? = null,
    /** Multi-select state for [ExtractionResult.Playlist] *and* [ExtractionResult.Collection] alike — the same toggle/range-select/skip-already-downloaded concept applies to both, just over different item types. */
    val playlistSelection: PlaylistSelectionState = PlaylistSelectionState(),
    /** Real device status (storage free space, network transport) — read once when Home loads. */
    val freeStorageBytes: Long? = null,
    val networkStatus: NetworkStatus? = null,
    /** [ExtractionResult.Single.media.formats] paired/flattened into what the screen actually shows — see `buildDownloadOptions`. */
    val downloadOptions: List<DownloadOption> = emptyList(),
    /** The [DownloadOption.id] the user has chosen for a [ExtractionResult.Single] result, if any. */
    val selectedFormatId: String? = null,
    /** True once a download has just been queued, so the screen can offer to jump to Downloads. */
    val justQueued: Boolean = false,
    /** Non-null while the user is choosing one quality to apply to a playlist download — see [HomeViewModel]. */
    val playlistDownloadSetup: PlaylistDownloadSetupState? = null,
    /** Non-null while [com.mediavault.core.domain.network.NetworkPolicyManager] has flagged a download as merely risky (not blocked) and is waiting for the user to confirm or cancel it — see [HomeViewModel]. */
    val networkWarning: NetworkWarning? = null,
)

/** A [com.mediavault.core.domain.network.NetworkPolicyDecision.Warn] the user must explicitly confirm before it proceeds — never applied silently. */
sealed class NetworkWarning {
    abstract val reason: String
    data class Single(override val reason: String) : NetworkWarning()
    data class Playlist(override val reason: String) : NetworkWarning()
    data class Collection(override val reason: String) : NetworkWarning()
}

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
 * resolved from the *first* selected item's own format list, paired into [DownloadOption]s the
 * same way the single-item screen does — including merge-required video+audio pairing — via
 * [com.mediavault.core.domain.download.buildDownloadOptions]. Every other item is matched
 * against that same chosen option's [com.mediavault.core.domain.download.QualityDescriptor]
 * independently once queued.
 */
data class PlaylistDownloadSetupState(
    val items: List<PlaylistItem>,
    val isResolvingFormats: Boolean = true,
    val downloadOptions: List<DownloadOption> = emptyList(),
    /** The [DownloadOption.id] the user has chosen, if any. */
    val selectedFormatId: String? = null,
    val errorMessage: String? = null,
)
