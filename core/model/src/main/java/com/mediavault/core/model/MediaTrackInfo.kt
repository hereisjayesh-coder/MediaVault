package com.mediavault.core.model

/**
 * Language metadata is only ever what the source reports; when unavailable, callers must
 * fall back to a generic label rather than guessing a language.
 */
data class MediaTrackInfo(
    val id: String,
    val languageCode: String?,
    val label: String?,
    val isDefault: Boolean,
)

data class SubtitleTrackInfo(
    val id: String,
    val languageCode: String?,
    val label: String?,
    val isForced: Boolean,
)
