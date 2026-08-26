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
    /** Numeric height/width, when the source reports them — used to sort/group resolutions (4K/1440p/1080p/...) reliably, unlike [resolutionLabel] which is just display text. Null for audio-only formats. */
    val heightPx: Int? = null,
    val widthPx: Int? = null,
    /** Only ever what the source reports — never guessed. See [MediaTrackInfo]'s same contract. */
    val languageCode: String? = null,
    /** Audio bitrate in kbps when the source reports it (yt-dlp's `abr`, falling back to `tbr` for audio-only formats). Null when unknown — never estimated. */
    val bitrateKbps: Int? = null,
)
