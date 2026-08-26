package com.mediavault.core.domain.download

import com.mediavault.core.model.MediaFormat

/**
 * One selectable row for the single-item download flow: either a format MediaVault can save
 * directly (muxed video+audio, or audio-only — [requiresProcessing] false, unchanged since the
 * real `DownloadEngine` stage), or a video-only format paired with a compatible audio-only
 * format that [MediaProcessor][com.mediavault.core.domain.processing.MediaProcessor] will remux
 * together after both download ([requiresProcessing] true). See `buildDownloadOptions`.
 */
data class DownloadOption(
    /** Stable across rebuilds of the same format list: the format id for a direct option, `"videoId+audioId"` for a paired one. */
    val id: String,
    val videoFormat: MediaFormat?,
    val audioFormat: MediaFormat?,
    val requiresProcessing: Boolean,
    /** File extension the final file will have — the source format's own container for direct options, the remux target for paired ones. */
    val outputContainer: String,
    val combinedEstimatedSizeBytes: Long?,
    /**
     * Set only when this option can be displayed (so the user can see the resolution exists at
     * all — "never silently hide") but not actually selected — e.g. a video-only format with no
     * audio-only track available anywhere to pair it with. Null means selectable.
     */
    val unavailableReason: String? = null,
) {
    val isSelectable: Boolean get() = unavailableReason == null
}

/**
 * Pure: turns a flat [MediaFormat] list (exactly what [com.mediavault.core.domain.extractor.MediaAnalysisResult.formats]
 * already provides) into the rows the single-item download screen shows.
 *
 * - Muxed and audio-only formats pass through unchanged as direct options — no processing, same
 *   behavior as before FFmpeg existed.
 * - Every video-only format becomes one *or more* paired options, one per distinct audio
 *   language available to combine it with (never just the "best" one — the caller/UI decides
 *   which language row to surface or let the user pick, but no language is silently dropped).
 * - A video-only format with no audio-only track anywhere in [formats] still gets exactly one
 *   row, marked [DownloadOption.unavailableReason] — shown, not hidden, but not selectable.
 *
 * No resolution tier is hardcoded (4K/1440p/1080p/720p/...) — whatever heights the extractor
 * reports become whatever paired rows exist, so a source offering *more* than that list still
 * works without a code change.
 */
fun buildDownloadOptions(formats: List<MediaFormat>): List<DownloadOption> {
    val directOptions = formats
        .filterNot { it.hasVideo && !it.hasAudio }
        .map { format ->
            DownloadOption(
                id = format.formatId,
                videoFormat = format.takeIf { it.hasVideo },
                audioFormat = format.takeIf { it.hasAudio && !it.hasVideo },
                requiresProcessing = false,
                outputContainer = format.container,
                combinedEstimatedSizeBytes = format.estimatedSizeBytes,
            )
        }

    val videoOnlyFormats = formats.filter { it.hasVideo && !it.hasAudio }
    val audioOnlyFormats = formats.filter { it.hasAudio && !it.hasVideo }

    val pairedOptions = videoOnlyFormats.flatMap { video ->
        val compatibleAudio = compatibleAudioTracksFor(video, audioOnlyFormats)
        if (compatibleAudio.isEmpty()) {
            listOf(
                DownloadOption(
                    id = video.formatId,
                    videoFormat = video,
                    audioFormat = null,
                    requiresProcessing = true,
                    outputContainer = video.container,
                    combinedEstimatedSizeBytes = video.estimatedSizeBytes,
                    unavailableReason = "No audio track is available to merge with this resolution.",
                ),
            )
        } else {
            compatibleAudio.map { audio ->
                val outputContainer = mergeOutputContainer(video, audio)
                DownloadOption(
                    id = "${video.formatId}+${audio.formatId}",
                    videoFormat = video,
                    audioFormat = audio,
                    requiresProcessing = true,
                    outputContainer = outputContainer,
                    combinedEstimatedSizeBytes = combinedSize(video, audio),
                )
            }
        }
    }

    return (directOptions + pairedOptions).sortedWith(
        compareByDescending<DownloadOption> { it.videoFormat?.heightPx ?: -1 }
            .thenBy { it.unavailableReason != null }
            .thenByDescending { it.outputContainer == "mp4" }
            .thenByDescending { it.combinedEstimatedSizeBytes ?: 0L },
    )
}

private fun combinedSize(video: MediaFormat, audio: MediaFormat): Long? {
    val videoSize = video.estimatedSizeBytes ?: return null
    val audioSize = audio.estimatedSizeBytes ?: return null
    return videoSize + audioSize
}

/**
 * One row per distinct language among formats that can be remuxed with [video] without
 * transcoding — same-container-family audio if any exists (MP4 video pairs with M4A audio,
 * WEBM video pairs with WEBM audio), otherwise every available audio track (still remuxable,
 * just into an MKV container — see [mergeOutputContainer]). Never returns an empty list unless
 * [audioFormats] itself is empty. Within one language, keeps only the largest/best variant —
 * multiple bitrates of the same language would just be noise in the picker.
 */
private fun compatibleAudioTracksFor(video: MediaFormat, audioFormats: List<MediaFormat>): List<MediaFormat> {
    if (audioFormats.isEmpty()) return emptyList()
    val sameFamily = audioFormats.filter { audioFamilyMatches(video.container, it.container) }
    val candidates = sameFamily.ifEmpty { audioFormats }
    return candidates
        .groupBy { it.languageCode ?: "" }
        .values
        .map { sameLanguage -> sameLanguage.maxByOrNull { it.estimatedSizeBytes ?: 0L } ?: sameLanguage.first() }
}

private fun audioFamilyMatches(videoContainer: String, audioContainer: String): Boolean = when (videoContainer) {
    "mp4" -> audioContainer == "m4a" || audioContainer == "mp4"
    "webm" -> audioContainer == "webm"
    else -> false
}

/** MP4-compatible pairs are preferred per the milestone's requirement; MKV is the universal stream-copy-safe fallback for any other combination — never a transcode. */
internal fun mergeOutputContainer(video: MediaFormat, audio: MediaFormat): String = when {
    video.container == "mp4" && (audio.container == "m4a" || audio.container == "mp4") -> "mp4"
    video.container == "webm" && audio.container == "webm" -> "webm"
    else -> "mkv"
}

/** Which section of the format picker a [DownloadOption] belongs in — see [DownloadOption.section]. */
enum class DownloadOptionSection { VIDEO, AUDIO, OTHER }

/**
 * VIDEO for anything with a video component (muxed direct, or a video-only/paired option —
 * selectable or not), AUDIO for a direct audio-only option, OTHER as a catch-all so a future
 * format shape [buildDownloadOptions] doesn't yet anticipate is still shown rather than
 * silently dropped from the picker.
 */
val DownloadOption.section: DownloadOptionSection
    get() = when {
        videoFormat != null -> DownloadOptionSection.VIDEO
        audioFormat != null -> DownloadOptionSection.AUDIO
        else -> DownloadOptionSection.OTHER
    }

/** Splits an already-[buildDownloadOptions]-ordered list into its three display sections, preserving each section's relative order (video sorted highest-to-lowest resolution). */
fun List<DownloadOption>.groupedBySection(): Map<DownloadOptionSection, List<DownloadOption>> = groupBy { it.section }
