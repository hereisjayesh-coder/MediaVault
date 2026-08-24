package com.mediavault.core.domain.extractor

/**
 * A playlist, channel "videos" tab, or similar ordered collection returned by [ExtractorEngine.analyze].
 *
 * Items are lightweight (title/thumbnail/duration/id) rather than fully-resolved
 * [MediaFormat] lists — resolving formats for one item means calling
 * [ExtractorEngine.analyze] again with that item's [PlaylistItem.url], on demand, when the
 * user actually picks it. Listing a 200-item playlist must not mean 200 full extractions.
 */
data class PlaylistAnalysisResult(
    val sourceName: String,
    val title: String,
    val thumbnailUrl: String?,
    val webpageUrl: String?,
    val collectionType: PlaylistCollectionType,
    /** Total item count reported by the source, when it reports one; may exceed `items.size`. */
    val itemCount: Int?,
    /** Order matches the source; each item also carries its own [PlaylistItem.index] as a stable position. */
    val items: List<PlaylistItem>,
)

enum class PlaylistCollectionType {
    PLAYLIST,
    CHANNEL,
    OTHER,
}

data class PlaylistItem(
    /**
     * Stable id for this item — the basis for "skip already downloaded" and for grouping a
     * future [com.mediavault.core.domain.download.DownloadRequest] back to its playlist entry.
     * Falls back to a position-based placeholder id only when the source gave us nothing
     * (see [isAvailable]).
     */
    val id: String,
    /** 1-based position in the playlist, preserved for ordered/range selection and download ordering. */
    val index: Int,
    val title: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
    /** URL to pass back into [ExtractorEngine.analyze] to fully resolve this single item. Null if unavailable. */
    val url: String?,
    /** False for entries the source reported as private/deleted/otherwise unreachable. */
    val isAvailable: Boolean,
)
