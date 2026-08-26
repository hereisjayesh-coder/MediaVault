package com.mediavault.core.extractor.ytdlp.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A deliberately partial mirror of yt-dlp's `--dump-json` info-dict. yt-dlp's real output has
 * many more fields than this; unknown ones are ignored by the decoder (see [ytDlpJson]). Only
 * the fields MediaVault's UI actually needs are modeled here.
 */
@Serializable
data class YtDlpInfoJson(
    val id: String? = null,
    val title: String? = null,
    val duration: Double? = null,
    val thumbnail: String? = null,
    val thumbnails: List<YtDlpThumbnailJson>? = null,
    @SerialName("webpage_url") val webpageUrl: String? = null,
    val extractor: String? = null,
    @SerialName("extractor_key") val extractorKey: String? = null,
    val language: String? = null,
    val formats: List<YtDlpFormatJson>? = null,
    val subtitles: Map<String, List<YtDlpSubtitleEntryJson>>? = null,
    /** "playlist", "multi_video", "url", etc. Null/"video" means a single, fully-resolved item. */
    @SerialName("_type") val type: String? = null,
    /** Present (possibly containing `null`s for unavailable items) when [type] is a collection. */
    val entries: List<YtDlpEntryJson?>? = null,
    @SerialName("playlist_count") val playlistCount: Int? = null,
)

/** One lightweight entry inside a playlist/channel's `entries`, from flat extraction. */
@Serializable
data class YtDlpEntryJson(
    val id: String? = null,
    val title: String? = null,
    val duration: Double? = null,
    val thumbnail: String? = null,
    val thumbnails: List<YtDlpThumbnailJson>? = null,
    val url: String? = null,
    @SerialName("webpage_url") val webpageUrl: String? = null,
    /** e.g. "private", "needs_auth", "premium_only" — a reliable unavailability signal when present. */
    val availability: String? = null,
)

@Serializable
data class YtDlpThumbnailJson(
    val url: String? = null,
    val preference: Int? = null,
)

@Serializable
data class YtDlpFormatJson(
    @SerialName("format_id") val formatId: String? = null,
    val ext: String? = null,
    val resolution: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Double? = null,
    val vcodec: String? = null,
    val acodec: String? = null,
    val filesize: Long? = null,
    @SerialName("filesize_approx") val filesizeApprox: Long? = null,
    @SerialName("format_note") val formatNote: String? = null,
    val language: String? = null,
    /** Average audio bitrate in kbps — the field yt-dlp actually reports for audio-only formats. */
    val abr: Double? = null,
    /** Total (video+audio) bitrate in kbps — used as a fallback when [abr] isn't reported. */
    val tbr: Double? = null,
    /** e.g. "https", "m3u8_native", "http_dash_segments" — determines whether a paused download can safely resume. */
    val protocol: String? = null,
)

@Serializable
data class YtDlpSubtitleEntryJson(
    val url: String? = null,
    val ext: String? = null,
    val name: String? = null,
)
