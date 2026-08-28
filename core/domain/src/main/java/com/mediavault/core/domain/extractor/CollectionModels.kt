package com.mediavault.core.domain.extractor

/**
 * An image post or image carousel returned by [ExtractorEngine.analyze] — the image-shaped
 * sibling of [PlaylistAnalysisResult]. Deliberately not built from [com.mediavault.core.model.MediaFormat]:
 * that type's codec/fps/bitrate fields describe a video/audio stream and mean nothing for a
 * static image, so reusing it here would be a modeling mismatch, not reuse.
 *
 * A single-image post and a multi-image carousel are the same shape — [items] just has one
 * entry — so callers don't need a separate "is this a carousel" branch to handle both.
 */
data class MediaCollectionResult(
    /** Extractor-assigned id, stable across analyses — the basis for future dedup checks. */
    val id: String,
    val sourceName: String,
    /** The post's caption, or blank if the source didn't provide one — never invented. */
    val title: String,
    val thumbnailUrl: String?,
    val webpageUrl: String?,
    /** Order matches the source; each item also carries its own [MediaCollectionItem.index] as a stable position. */
    val items: List<MediaCollectionItem>,
)

data class MediaCollectionItem(
    /** Stable id for this item — the basis for "skip already downloaded" and for grouping a [com.mediavault.core.domain.download.DownloadRequest] back to its collection. */
    val id: String,
    /** 1-based position within the collection, preserved for ordered/range selection and download ordering. */
    val index: Int,
    /** Direct, downloadable URL for this image — may be short-lived/signed, same caveat as [com.mediavault.core.model.MediaFormat]'s own direct-URL fields. */
    val imageUrl: String,
    /** A smaller preview URL when the source offers one distinct from [imageUrl]; falls back to [imageUrl] otherwise. */
    val thumbnailUrl: String?,
    /** Only ever what the source reports — usually unknown ahead of download for an image post, never guessed. */
    val estimatedSizeBytes: Long? = null,
)
