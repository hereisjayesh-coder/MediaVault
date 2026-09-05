package com.mediavault.app.ui.screens.home

import com.mediavault.app.util.NetworkStatus
import com.mediavault.core.database.entity.MediaItemEntity
import com.mediavault.core.domain.download.FormatSelectionModel
import com.mediavault.core.domain.download.QualityTier
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
    /** [ExtractionResult.Single.media.formats] grouped into quality tiers + audio tracks — see `toFormatSelectionModel`. */
    val formatSelection: FormatSelectionModel? = null,
    /** What the user has picked from [formatSelection] so far, for a [ExtractionResult.Single] result. */
    val selectedQuality: SelectedQualityState = SelectedQualityState(),
    /** True once a download has just been queued, so the screen can offer to jump to Downloads. */
    val justQueued: Boolean = false,
    /** Non-null while the user is choosing one quality to apply to a playlist download — see [HomeViewModel]. */
    val playlistDownloadSetup: PlaylistDownloadSetupState? = null,
    /** Non-null while [com.mediavault.core.domain.network.NetworkPolicyManager] has flagged a download as merely risky (not blocked) and is waiting for the user to confirm or cancel it — see [HomeViewModel]. */
    val networkWarning: NetworkWarning? = null,
    /** The most recent completed downloads/media additions, newest first, capped to a short
     * preview list — sourced from the exact same [com.mediavault.app.library.LibraryRepository]
     * Room-backed flow Library itself renders (see [HomeViewModel]'s init block), never a
     * separate history table. Empty until at least one download has actually completed. */
    val recentActivity: List<MediaItemEntity> = emptyList(),
)

/**
 * The picker's current selection within a [FormatSelectionModel] — deliberately UI-local state,
 * not persisted or handed to the download engine directly (see `HomeViewModel.currentResolvedSelection`,
 * which turns this plus the model into a real [com.mediavault.core.domain.download.ResolvedSelection]
 * only once both sides are available). Nothing is pre-selected on a fresh analysis — the user
 * always makes an active choice, same as the format picker always has.
 */
data class SelectedQualityState(
    /** Null until the user taps a quality tier — or permanently null for an audio-only source, which has no video tiers to pick from at all. */
    val tier: QualityTier? = null,
    /** Which variant within [tier] — null means "the tier's own best/default variant," so a tier with only one variant never needs this set explicitly. */
    val videoVariantFormatId: String? = null,
    /** Whether the audio section is in multi-select (checkbox) mode — only ever meaningful once a video tier is picked; see `FormatSelectionModel`'s own KDoc for why a bare audio-only source stays single-select. */
    val includeMultipleAudio: Boolean = false,
    /** At most one entry unless [includeMultipleAudio] is true. */
    val selectedAudioFormatIds: Set<String> = emptySet(),
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
 * resolved from the *first* selected item's own format list, grouped into a
 * [FormatSelectionModel] the same way the single-item screen does — including multi-audio-track
 * selection. Every other item is matched against that same chosen quality's
 * [com.mediavault.core.domain.download.QualityDescriptor] independently once queued (see
 * `QualityDescriptor.resolveForPlaylist`), never re-using this item's own specific format ids,
 * which are meaningless on any other item.
 */
data class PlaylistDownloadSetupState(
    val items: List<PlaylistItem>,
    val isResolvingFormats: Boolean = true,
    val formatSelection: FormatSelectionModel? = null,
    val selectedQuality: SelectedQualityState = SelectedQualityState(),
    val errorMessage: String? = null,
)
