package com.mediavault.core.domain.download

import com.mediavault.core.model.MediaFormat

/**
 * One quality tier's selectable video variants — usually one, occasionally several when a
 * source offers the same resolution in more than one codec/container/frame-rate (e.g. a
 * YouTube upload's 1080p is typically available as H.264/MP4, VP9/WEBM, and AV1/MP4 alike).
 * [variants] is ordered with [bestVariant] first — the picker only ever needs to show the rest
 * when there genuinely is more than one, per this feature's "useful variants only when needed"
 * requirement.
 */
data class VideoQualityGroup(
    val tier: QualityTier,
    val variants: List<MediaFormat>,
) {
    val bestVariant: MediaFormat get() = variants.first()
}

/**
 * The picker's whole data set for one analyzed source: video grouped into tiers, and every
 * distinct audio language available to pair with a video-only tier — see [toFormatSelectionModel].
 */
data class FormatSelectionModel(
    val videoQualityGroups: List<VideoQualityGroup>,
    val audioTracks: List<MediaFormat>,
)

/**
 * Pure: turns a flat [MediaFormat] list (exactly what [com.mediavault.core.domain.extractor.MediaAnalysisResult.formats]
 * already provides) into the grouped video-quality-tier + per-language-audio-track shape the
 * picker actually shows. Reused as-is by both the single-item screen and the playlist quality
 * picker (see [resolveForPlaylist]) — there is exactly one place this grouping/pairing logic
 * lives.
 *
 * [FormatSelectionModel.audioTracks] is only ever populated when it's genuinely useful to pick
 * from: a source with at least one video-only format (nothing to play without picking an audio
 * track) *and* at least one separate audio-only format to pair it with. A muxed-only source (every
 * video format already carries its own audio) or a video-only source with literally no separate
 * audio anywhere (a silent clip) both report an empty [FormatSelectionModel.audioTracks] — there
 * is nothing meaningful to choose in either case, and the selected video variant is already the
 * complete file.
 */
fun List<MediaFormat>.toFormatSelectionModel(): FormatSelectionModel {
    val videoFormats = filter { it.hasVideo }
    val audioOnlyFormats = filter { it.hasAudio && !it.hasVideo }

    val videoQualityGroups = videoFormats
        .dedupeVariants()
        .groupBy { QualityTier.forHeight(it.heightPx) }
        .map { (tier, variants) -> VideoQualityGroup(tier, variants.sortedWith(variantOrdering)) }
        .sortedBy { it.tier.ordinal }

    val audioTracks = if (videoFormats.any { !it.hasAudio } && audioOnlyFormats.isNotEmpty()) {
        audioOnlyFormats.bestPerLanguage()
    } else {
        emptyList()
    }

    return FormatSelectionModel(videoQualityGroups, audioTracks)
}

/**
 * Some sources (confirmed live on YouTube) list the same height/fps/codec/container combination
 * twice under different format ids — one carrying real size data, one without (an apparent
 * duplicate/legacy listing). Keeps one row per distinct combination, preferring whichever copy
 * actually reports a size, so the picker never shows two visually-identical rows for the same
 * real quality.
 */
private fun List<MediaFormat>.dedupeVariants(): List<MediaFormat> =
    groupBy { listOf(it.heightPx, it.widthPx, it.fps, it.videoCodec, it.container, it.hasAudio) }
        .map { (_, dupes) -> dupes.firstOrNull { it.estimatedSizeBytes != null } ?: dupes.first() }

/** H.264/MP4 first — the broadest-compatibility default a picker should pre-select — then VP9, then AV1, then anything else; ties broken by the smallest file (best compression) so the default is never needlessly large. */
private val variantOrdering: Comparator<MediaFormat> =
    compareBy<MediaFormat> { format -> codecPreferenceRank(format.videoCodec) }
        .thenBy { it.estimatedSizeBytes ?: Long.MAX_VALUE }

private fun codecPreferenceRank(videoCodec: String?): Int = when {
    videoCodec == null -> 3
    videoCodec.startsWith("avc1", ignoreCase = true) || videoCodec.startsWith("h264", ignoreCase = true) -> 0
    videoCodec.startsWith("vp9", ignoreCase = true) || videoCodec.startsWith("vp09", ignoreCase = true) -> 1
    videoCodec.startsWith("av01", ignoreCase = true) -> 2
    else -> 3
}

/**
 * One row per distinct language — multiple bitrate variants of the same language (confirmed
 * live: a single YouTube upload can offer both a "low" and a "medium" bitrate track per
 * language) would just be noise in a picker whose whole point is not overwhelming the user, so
 * only the best (highest-bitrate) variant per language survives. A track the source reports no
 * language for is still its own row (grouped under a blank key) rather than dropped.
 */
private fun List<MediaFormat>.bestPerLanguage(): List<MediaFormat> =
    groupBy { it.languageCode ?: "" }
        .values
        .map { sameLanguage -> sameLanguage.maxByOrNull { it.bitrateKbps ?: 0 } ?: sameLanguage.first() }

/** One format id per selected audio track, paired with the language the source reported for it (never guessed) — see [MediaProcessor's][com.mediavault.core.domain.processing.MediaProcessor] merge tagging. */
data class SelectedAudioTrack(val formatId: String, val languageCode: String?)

/**
 * The fully-resolved outcome of a picker selection: which video variant (if any), which audio
 * track(s) (zero, one, or many), whether [MediaProcessor][com.mediavault.core.domain.processing.MediaProcessor]
 * needs to combine them, the container the combined/direct file will actually have, and its
 * estimated total size. This is the one shape both the single-item enqueue path and the
 * playlist per-item resolution path build a [com.mediavault.core.domain.download.DownloadRequest]/
 * task update from — see [resolveSelection].
 */
data class ResolvedSelection(
    val videoFormat: MediaFormat?,
    val audioFormats: List<MediaFormat>,
    val requiresProcessing: Boolean,
    val outputContainer: String,
    val combinedEstimatedSizeBytes: Long?,
) {
    /** The id [com.mediavault.core.domain.download.DownloadRequest.formatId] should carry — the video when one is selected, otherwise the sole direct audio format. */
    val primaryFormatId: String? get() = videoFormat?.formatId ?: audioFormats.firstOrNull()?.formatId
}

/**
 * Builds the final, downloadable outcome of picking [videoFormat] (or none, for a direct
 * audio-only download) plus zero or more [audioFormats] to merge in. Merging is needed exactly
 * when a video was picked and at least one separate audio track was too — a muxed video, a
 * video-only source with no separate audio anywhere, and a plain audio-only pick are all
 * "direct" (no processing) by the same rule that already governed
 * `DownloadOption.requiresProcessing` before this redesign. Multi-audio-track selection is only
 * ever offered alongside a video pick (see [FormatSelectionModel]'s own KDoc) — a bare
 * audio-only download always resolves a single track, matching this project's existing,
 * unchanged audio-only behavior.
 */
fun resolveSelection(videoFormat: MediaFormat?, audioFormats: List<MediaFormat>): ResolvedSelection {
    val requiresProcessing = videoFormat != null && audioFormats.isNotEmpty()
    return ResolvedSelection(
        videoFormat = videoFormat,
        audioFormats = audioFormats,
        requiresProcessing = requiresProcessing,
        outputContainer = mergeOutputContainer(videoFormat, audioFormats),
        combinedEstimatedSizeBytes = combinedEstimatedSize(videoFormat, audioFormats),
    )
}

private fun combinedEstimatedSize(video: MediaFormat?, audios: List<MediaFormat>): Long? {
    val sizes = (listOfNotNull(video) + audios).map { it.estimatedSizeBytes }
    return if (sizes.isEmpty() || sizes.any { it == null }) null else sizes.filterNotNull().sum()
}

/**
 * Engineering judgment call for the merged/direct output container:
 * - No video (a direct audio-only pick): whatever container that one audio format already is.
 * - No audio selected: whatever container the video already is (muxed, or video-only with no
 *   audio anywhere — either way it's the complete file untouched).
 * - Exactly one audio track to merge in: MP4 when both streams are already MP4-family
 *   (MP4 video + M4A/MP4 audio), WEBM when both are WEBM — the same stream-copy-safe pairing
 *   this project has always preferred — MKV otherwise, the universal remux-safe fallback.
 * - **Two or more** audio tracks to merge in: always MKV. MP4's own multi-audio-track support is
 *   real but inconsistent across the codec combinations a source can actually offer (e.g. mixing
 *   an AAC track with an Opus track), and MediaVault only ever remuxes — it never transcodes to
 *   force compatibility. MKV natively supports any number of tracks with per-track language
 *   metadata and is fully supported by Media3's own Matroska extractor, so this never has to
 *   choose between dropping a requested track and re-encoding one — exactly this feature's own
 *   "choose the appropriate alternative rather than silently dropping tracks" requirement.
 */
internal fun mergeOutputContainer(video: MediaFormat?, audios: List<MediaFormat>): String = when {
    video == null -> audios.firstOrNull()?.container ?: "mkv"
    audios.isEmpty() -> video.container
    audios.size == 1 -> singlePairContainer(video, audios.single())
    else -> "mkv"
}

private fun singlePairContainer(video: MediaFormat, audio: MediaFormat): String = when {
    video.container == "mp4" && (audio.container == "m4a" || audio.container == "mp4") -> "mp4"
    video.container == "webm" && audio.container == "webm" -> "webm"
    else -> "mkv"
}
