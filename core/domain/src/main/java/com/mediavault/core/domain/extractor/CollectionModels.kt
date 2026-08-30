package com.mediavault.core.domain.extractor

import com.mediavault.core.model.MediaType

/**
 * A post or carousel of individually-downloadable items returned by [ExtractorEngine.analyze] —
 * the item-per-entry sibling of [PlaylistAnalysisResult]. Each [MediaCollectionItem] carries its
 * own [MediaCollectionItem.mediaType] — a carousel is very often mixed (some image items, some
 * video items, in the source's own order), not necessarily all-image, so this deliberately isn't
 * an "image collection" type. Not built from [com.mediavault.core.model.MediaFormat]: that type's
 * codec/fps/bitrate fields describe a *choice between* video/audio streams for one piece of
 * media, which doesn't apply here — a collection item is already the one thing to download, not
 * a menu of quality options.
 *
 * A single-item post and a multi-item carousel are the same shape — [items] just has one entry —
 * so callers don't need a separate "is this a carousel" branch to handle both.
 */
data class MediaCollectionResult(
    /** Extractor-assigned id, stable across analyses — the basis for future dedup checks. */
    val id: String,
    val sourceName: String,
    /** The post's caption, or blank if the source didn't provide one — never invented. */
    val title: String,
    val thumbnailUrl: String?,
    val webpageUrl: String?,
    /** Order matches the source; each item also carries its own [MediaCollectionItem.index] as a stable position. Includes every item the source reported, available or not — see [MediaCollectionItem.isAvailable] — so the collection's real total item count is always `items.size`, never silently short. */
    val items: List<MediaCollectionItem>,
)

data class MediaCollectionItem(
    /** Stable id for this item — the basis for "skip already downloaded" and for grouping a [com.mediavault.core.domain.download.DownloadRequest] back to its collection. */
    val id: String,
    /** 1-based position within the collection, preserved for ordered/range selection and download ordering — never renumbered after an unavailable item is filtered out elsewhere, so this always reflects the item's real position in the source carousel. */
    val index: Int,
    /** What this specific item actually is — a mixed carousel can (and often does) hold both [MediaType.IMAGE] and [MediaType.VIDEO] items side by side, each routed to its own existing download/viewer path by this field, not by the collection as a whole. Never [MediaType.AUDIO] — no supported source currently returns an audio-only collection item. */
    val mediaType: MediaType,
    /** Direct, downloadable URL for this item — may be short-lived/signed, same caveat as [com.mediavault.core.model.MediaFormat]'s own direct-URL fields. Null exactly when [isAvailable] is false. */
    val mediaUrl: String?,
    /** False for an item the source reported but couldn't resolve a usable direct URL for — kept in [MediaCollectionResult.items] (so position/count/order stay honest) but not selectable or downloadable, same contract as [PlaylistItem.isAvailable]. */
    val isAvailable: Boolean,
    /** A smaller preview URL when the source offers one distinct from [mediaUrl]; falls back to [mediaUrl] otherwise. */
    val thumbnailUrl: String?,
    /** Only ever what the source reports — usually unknown ahead of download for a collection item, never guessed. */
    val estimatedSizeBytes: Long? = null,
)
