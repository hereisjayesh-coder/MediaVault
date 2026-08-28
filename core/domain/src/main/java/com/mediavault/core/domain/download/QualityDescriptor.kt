package com.mediavault.core.domain.download

/**
 * A source-agnostic "what quality" fingerprint used to pick the matching [DownloadOption] on
 * each playlist item independently, since every item gets its own freshly-built
 * [buildDownloadOptions] list with its own option ids (an id from one item is meaningless on
 * another — see [DownloadOption.id]). Built once from the option the user picked on the first
 * resolved item; every other item matches against this same descriptor via [findMatching].
 *
 * Covers both a direct quality (muxed, audio-only, or a video-only format with no audio
 * anywhere — [requiresProcessing] false, matched by exact shape) and a merge-required, paired
 * quality ([requiresProcessing] true) — the same shape [DownloadOption] already models for the
 * single-item flow, reused here rather than re-deriving equivalent pairing logic per item.
 */
data class QualityDescriptor(
    val resolutionLabel: String?,
    val container: String,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    /** Mirrors [DownloadOption.requiresProcessing] — false for every quality selectable before merge support existed. */
    val requiresProcessing: Boolean = false,
    /**
     * The audio track's language, captured only when [requiresProcessing] is true — the specific
     * language the user paired at selection time, preserved across every item rather than left to
     * fall back to "whichever language pairing finds first" on each item. A null value means the
     * source reported no language for the chosen track and matches only another item whose own
     * pairing also lands on a no-reported-language track — never treated as "any language".
     */
    val audioLanguageCode: String? = null,
) {
    companion object {
        /** From a selected, [DownloadOption.isSelectable] option — the playlist-quality-picker's actual selection unit, direct or merge-required alike. */
        fun from(option: DownloadOption): QualityDescriptor {
            val primaryFormat = option.videoFormat ?: option.audioFormat
            return QualityDescriptor(
                resolutionLabel = primaryFormat?.resolutionLabel,
                container = option.outputContainer,
                hasVideo = option.videoFormat != null,
                hasAudio = option.videoFormat?.hasAudio == true || option.audioFormat != null,
                requiresProcessing = option.requiresProcessing,
                audioLanguageCode = option.audioFormat?.languageCode,
            )
        }
    }
}

/**
 * The option on this item's own freshly-built [buildDownloadOptions] list matching [descriptor]
 * — a direct option matched by exact shape (resolution/container/video-audio), or a
 * *selectable* paired option at the same video resolution paired with the same audio-language
 * identity as [QualityDescriptor.audioLanguageCode] (a same-language lookup, not a
 * bitrate/variant one — [compatibleAudioTracksFor] already keeps only the best variant per
 * language). Returns null — never substituting a different quality or language — when this item
 * genuinely doesn't offer it; callers must surface that as a clear per-item failure.
 */
fun List<DownloadOption>.findMatching(descriptor: QualityDescriptor): DownloadOption? = firstOrNull { option ->
    if (option.requiresProcessing != descriptor.requiresProcessing) return@firstOrNull false
    if (descriptor.requiresProcessing) {
        option.isSelectable &&
            option.videoFormat?.resolutionLabel == descriptor.resolutionLabel &&
            option.audioFormat?.languageCode == descriptor.audioLanguageCode
    } else {
        val format = option.videoFormat ?: option.audioFormat
        format?.resolutionLabel == descriptor.resolutionLabel &&
            option.outputContainer == descriptor.container &&
            (option.videoFormat != null) == descriptor.hasVideo &&
            (format?.hasAudio == true) == descriptor.hasAudio
    }
}
