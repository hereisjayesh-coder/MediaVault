package com.mediavault.core.model

/**
 * A single selectable quality/format option surfaced by an [com.mediavault.core.domain.ExtractorEngine]
 * analysis result. Size and fps are estimates when the source does not report them exactly.
 */
data class MediaFormat(
    val formatId: String,
    val resolutionLabel: String?,
    val container: String,
    val videoCodec: String?,
    val audioCodec: String?,
    val fps: Int?,
    val estimatedSizeBytes: Long?,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    /** False for streaming protocols (HLS/DASH segments, ...) where a paused download can't safely continue from a byte offset. */
    val supportsResume: Boolean = false,
)
