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
    /** 1-based position within the post's own node list — stable across image/video mixed carousels, so it stays the correct index for `download()`'s `format_id` even after Kotlin-side filtering. */
    val index: Int,
    val isVideo: Boolean,
    val imageUrl: String,
    val thumbnailUrl: String? = null,
)
