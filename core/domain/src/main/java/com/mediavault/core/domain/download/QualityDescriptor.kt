package com.mediavault.core.domain.download

import com.mediavault.core.model.MediaFormat

/**
 * A source-agnostic "what quality" fingerprint — resolution/container/video-audio shape —
 * used to pick the matching [MediaFormat] on each playlist item independently, since every
 * item has its own format list with its own format ids (a raw `formatId` from one item is
 * meaningless on another). Deliberately just these four fields, not a raw [MediaFormat]:
 * they're the only ones that mean the same thing across different items of the same source.
 */
data class QualityDescriptor(
    val resolutionLabel: String?,
    val container: String,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
) {
    companion object {
        fun from(format: MediaFormat): QualityDescriptor =
            QualityDescriptor(format.resolutionLabel, format.container, format.hasVideo, format.hasAudio)
    }
}

private fun MediaFormat.matches(descriptor: QualityDescriptor): Boolean =
    resolutionLabel == descriptor.resolutionLabel &&
        container == descriptor.container &&
        hasVideo == descriptor.hasVideo &&
        hasAudio == descriptor.hasAudio

/**
 * The format on this item matching [descriptor] exactly, or null if this item genuinely
 * doesn't offer that quality — callers must surface that as a clear per-item failure
 * ("not available for this item"), never silently substitute a different quality.
 */
fun List<MediaFormat>.findMatching(descriptor: QualityDescriptor): MediaFormat? =
    firstOrNull { it.matches(descriptor) }
