package com.mediavault.core.domain.download

/**
 * A coarse, user-facing video-quality bucket. The format picker groups every video format a
 * source reports into one of these six tiers instead of showing each raw resolution/fps/codec
 * combination as its own row — a modern YouTube upload alone can report 30+ video-only formats
 * (see PROJECT_MASTER.md's format-selection redesign decision log entry), which is not a
 * meaningful set of choices for a person to scroll through.
 */
enum class QualityTier(val label: String) {
    UHD_4K("4K"),
    QHD_1440P("2K"),
    FULL_HD_1080P("1080p"),
    HD_720P("720p"),
    SD_480P("480p"),
    LOWER("Lower quality"),
    ;

    companion object {
        /** Buckets by reported pixel height — never a guess: a format with no known height always lands in [LOWER] rather than being assigned a resolution the source didn't report. */
        fun forHeight(heightPx: Int?): QualityTier = when {
            heightPx == null -> LOWER
            heightPx >= 2160 -> UHD_4K
            heightPx >= 1440 -> QHD_1440P
            heightPx >= 1080 -> FULL_HD_1080P
            heightPx >= 720 -> HD_720P
            heightPx >= 480 -> SD_480P
            else -> LOWER
        }
    }
}
