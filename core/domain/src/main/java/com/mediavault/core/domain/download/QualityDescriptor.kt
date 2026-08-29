package com.mediavault.core.domain.download

import com.mediavault.core.model.MediaFormat

/**
 * A source-agnostic "what quality" fingerprint used to reproduce the same pick on every other
 * playlist item independently, since each item gets its own freshly-built [FormatSelectionModel]
 * with its own format ids (an id from one item is meaningless on another). Built once from the
 * option the user chose on the first resolved item; every other item resolves against this same
 * descriptor via [resolveForPlaylist] — never a raw format id, which would only ever coincidentally
 * exist on a second item.
 */
data class QualityDescriptor(
    /** Null means the picked quality has no video at all — a direct audio-only download. */
    val tier: QualityTier?,
    /**
     * The exact set of audio languages the user chose, in no particular order — empty when no
     * separate audio track was picked (a muxed video, or a video-only source with no audio
     * anywhere). Never re-derives "whichever audio this item happens to offer": each language in
     * this set must be matched on every item, or that item fails outright — see
     * [resolveForPlaylist].
     */
    val audioLanguageCodes: List<String>,
) {
    companion object {
        fun from(videoFormat: MediaFormat?, audioFormats: List<MediaFormat>): QualityDescriptor = QualityDescriptor(
            tier = videoFormat?.let { QualityTier.forHeight(it.heightPx) },
            audioLanguageCodes = audioFormats.mapNotNull { it.languageCode },
        )
    }
}

/**
 * Resolves [descriptor] against this item's own format list — a video-quality-tier match (its
 * [VideoQualityGroup.bestVariant]) plus an exact match for every requested audio language. Returns
 * null — never substituting a different quality or language — the moment either isn't genuinely
 * available on this item; callers must surface that as a clear per-item failure, per this
 * feature's "if a requested language is missing for one item, fail that item clearly, never
 * silently substitute another language" requirement (which applies equally to the video tier
 * itself: a missing quality is not rounded to the nearest available one).
 */
fun List<MediaFormat>.resolveForPlaylist(descriptor: QualityDescriptor): ResolvedSelection? {
    val model = toFormatSelectionModel()

    val video = if (descriptor.tier != null) {
        model.videoQualityGroups.firstOrNull { it.tier == descriptor.tier }?.bestVariant ?: return null
    } else {
        null
    }

    val audios = descriptor.audioLanguageCodes.map { language ->
        model.audioTracks.firstOrNull { it.languageCode == language } ?: return null
    }

    return resolveSelection(video, audios)
}
