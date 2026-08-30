package com.mediavault.core.extractor.instaloader.json

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Shared decoder — mirrors `core:extractor-ytdlp`'s `ytDlpJson` convention. */
val instaloaderJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

/** Raw shape returned by `mediavault_instaloader.py`'s `analyze()` — see that function's own docstring. */
@Serializable
data class InstaloaderPostJson(
    val id: String,
    val sourceName: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val webpageUrl: String? = null,
    val items: List<InstaloaderItemJson> = emptyList(),
)

@Serializable
data class InstaloaderItemJson(
    /** 1-based position within the post's own node list — stable across a mixed image/video carousel, so it stays the correct index for `download()`'s `format_id` regardless of any item's type or availability. */
    val index: Int,
    val isVideo: Boolean,
    /** Null exactly when Python couldn't resolve this one node's own URL — see `analyze()`'s per-item try/except. Maps to [com.mediavault.core.domain.extractor.MediaCollectionItem.isAvailable] being false, never a dropped item. */
    val imageUrl: String? = null,
    val thumbnailUrl: String? = null,
)
