package com.mediavault.app.ui.screens.home

import com.mediavault.core.domain.download.DownloadOption
import com.mediavault.core.domain.extractor.MediaCollectionItem
import com.mediavault.core.domain.extractor.MediaCollectionResult
import com.mediavault.core.model.MediaFormat

/** "596" -> "9:56", "3725" -> "1:02:05". Returns null when there's nothing to show. */
fun formatDurationLabel(totalSeconds: Long?): String? {
    if (totalSeconds == null || totalSeconds < 0) return null
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/** Bytes to a short human-readable label, e.g. 884_000_000 -> "843 MB". Null when unknown. */
fun formatFileSizeLabel(bytes: Long?): String? {
    if (bytes == null || bytes <= 0) return null
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return if (unitIndex == 0) "$bytes B" else "%.0f %s".format(value, units[unitIndex])
}

/** e.g. "1080p 60fps • MP4 • H.264 • 844 MB • with audio (AAC)", omitting any part that isn't known. */
fun formatFormatSummary(format: MediaFormat): String {
    // Some sources (yt-dlp included) report the resolution field as the literal string
    // "audio only" for audio-only formats — that's already conveyed by audioLabel below.
    val resolutionAndFps = listOfNotNull(
        format.resolutionLabel?.takeIf { format.hasVideo },
        format.fps?.takeIf { it > 0 && format.hasVideo }?.let { "${it}fps" },
    ).joinToString(" ").ifBlank { null }

    val audioLabel = when {
        !format.hasVideo && format.hasAudio -> "audio only" + (format.audioCodec?.let { " ($it)" } ?: "")
        format.hasVideo && format.hasAudio -> "with audio" + (format.audioCodec?.let { " ($it)" } ?: "")
        format.hasVideo && !format.hasAudio -> "video only"
        else -> null
    }

    val parts = listOfNotNull(
        resolutionAndFps,
        format.container.takeIf { it != "unknown" }?.uppercase(),
        format.videoCodec?.takeIf { format.hasVideo },
        formatFileSizeLabel(format.estimatedSizeBytes),
        audioLabel,
    )
    return parts.joinToString(" • ")
}

/** Compact title for a VIDEO-section row, e.g. "2160p60", "1080p". Never invents a resolution the source didn't report. */
fun videoOptionTitle(option: DownloadOption): String {
    val video = option.videoFormat ?: return "Video"
    return listOfNotNull(
        video.resolutionLabel,
        video.fps?.takeIf { it > 0 }?.let { "${it}fps" },
    ).joinToString(" ").ifBlank { "Video" }
}

/**
 * Detail line for a VIDEO-section row: container, codec, the *final* size (already the
 * video+audio sum for a paired option — never re-derived here), and — per this screen's "never
 * silently hide audio availability" requirement — always ends with exactly what happens to audio.
 */
fun videoOptionSubtitle(option: DownloadOption): String {
    val video = option.videoFormat ?: return ""
    val parts = listOfNotNull(
        option.outputContainer.takeIf { it != "unknown" }?.uppercase(),
        video.videoCodec,
        formatFileSizeLabel(option.combinedEstimatedSizeBytes),
        audioAvailabilityLabel(option),
    )
    return parts.joinToString(" • ")
}

/**
 * One of three real states, always shown, never omitted: the video already has audio baked in
 * (a direct/muxed option), a separate track will be merged in by FFmpeg after download (a paired
 * option), or — for a video-only format with no compatible audio track anywhere — genuinely none.
 */
fun audioAvailabilityLabel(option: DownloadOption): String {
    val video = option.videoFormat
    val pairedAudio = option.audioFormat
    return when {
        video != null && video.hasAudio -> "Includes audio" + (video.audioCodec?.let { " ($it)" } ?: "")
        pairedAudio != null -> "+ audio" + (pairedAudio.audioCodec?.let { " ($it)" } ?: "") + languageSuffix(pairedAudio.languageCode)
        else -> "No audio available"
    }
}

/** Compact title for an AUDIO-section row: the container/format, e.g. "M4A", "OPUS". */
fun audioOptionTitle(option: DownloadOption): String {
    val audio = option.audioFormat ?: return "Audio"
    return audio.container.takeIf { it != "unknown" }?.uppercase() ?: "Audio"
}

/** Detail line for an AUDIO-section row: codec, bitrate, estimated size, and language when known. */
fun audioOptionSubtitle(option: DownloadOption): String {
    val audio = option.audioFormat ?: return ""
    val parts = listOfNotNull(
        audio.audioCodec,
        audio.bitrateKbps?.let { "$it kbps" },
        formatFileSizeLabel(option.combinedEstimatedSizeBytes),
        audio.languageCode?.let { "[$it]" },
    )
    return parts.joinToString(" • ")
}

/** Compact "what's selected" label for the persistent Download bar, e.g. "1080p60 • 92 MB" or "M4A • 5 MB". */
fun selectedOptionSummaryLabel(option: DownloadOption): String {
    val title = if (option.videoFormat != null) videoOptionTitle(option) else audioOptionTitle(option)
    val size = formatFileSizeLabel(option.combinedEstimatedSizeBytes)
    return listOfNotNull(title, size).joinToString(" • ")
}

/** Short quality label for a playlist quality-picker row — reuses the same title logic as the single-item picker's own rows, whichever section [option] belongs in. */
fun playlistQualityLabel(option: DownloadOption): String =
    if (option.videoFormat != null) videoOptionTitle(option) else audioOptionTitle(option)

/**
 * Rough aggregate estimate for the playlist download-setup bar: every item priced the same as
 * [option]'s own final size (already the video+audio sum for a merge-required option — see
 * [DownloadOption.combinedEstimatedSizeBytes]), since playlist items don't get their own
 * resolved format list until each is analyzed individually at download time — an estimate,
 * never a guarantee. Null when the chosen option's own size is unknown, never a guessed number.
 */
fun estimatedPlaylistTotalSizeBytes(option: DownloadOption?, itemCount: Int): Long? {
    val perItem = option?.combinedEstimatedSizeBytes ?: return null
    return perItem * itemCount
}

/** Never invents a language name — just shows the raw code the source reported, exactly like [com.mediavault.core.model.MediaTrackInfo]'s own contract. */
private fun languageSuffix(languageCode: String?): String = languageCode?.let { " [$it]" }.orEmpty()

/**
 * Title to store for one enqueued image download: the post's own caption when it has one
 * (numbered against [totalCount] so sibling images in the same carousel are distinguishable in
 * the Library/Downloads list), falling back to a generic "Image"/"Image N" — never blank, never
 * inventing wording the source didn't provide. [captionTitleLine] caps it to one short line —
 * a Downloads/Library row title is UI real estate, not a caption-display field, and a real
 * Instagram caption can run to several paragraphs (confirmed live: a NASA post's caption plus
 * its own appended accessibility alt-text overflowed the entire Downloads screen before this
 * cap existed). The full, untruncated caption stays intact on [MediaCollectionResult.title]
 * for wherever a caption genuinely should be shown in full (the analysis preview card).
 */
fun collectionItemTitle(collection: MediaCollectionResult, item: MediaCollectionItem, totalCount: Int): String {
    val caption = captionTitleLine(collection.title)
    return when {
        totalCount <= 1 && caption.isNotEmpty() -> caption
        totalCount <= 1 -> "Image"
        caption.isNotEmpty() -> "$caption (${item.index}/$totalCount)"
        else -> "Image ${item.index}"
    }
}

private const val MAX_CAPTION_TITLE_LENGTH = 80

/** The caption's first line, further capped to [MAX_CAPTION_TITLE_LENGTH] characters with an ellipsis — never a guess at what the rest says, just a visible clip of what's actually there. */
private fun captionTitleLine(caption: String): String {
    val firstLine = caption.trim().lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    return if (firstLine.length > MAX_CAPTION_TITLE_LENGTH) {
        firstLine.take(MAX_CAPTION_TITLE_LENGTH).trimEnd() + "…"
    } else {
        firstLine
    }
}

private val KNOWN_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic")

/** The saved file's extension for a downloaded image: sniffed from the direct URL's own path when it looks like a real image extension, otherwise "jpg" — the container every image source tested so far actually serves, never a guess dressed up as certainty. */
fun imageContainerFor(imageUrl: String): String {
    val path = imageUrl.substringBefore('?').substringBefore('#')
    val extension = path.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return extension.takeIf { it in KNOWN_IMAGE_EXTENSIONS } ?: "jpg"
}
