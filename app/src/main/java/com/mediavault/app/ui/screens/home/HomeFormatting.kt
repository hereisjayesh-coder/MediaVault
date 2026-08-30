package com.mediavault.app.ui.screens.home

import com.mediavault.core.domain.download.ResolvedSelection
import com.mediavault.core.domain.extractor.MediaCollectionItem
import com.mediavault.core.domain.extractor.MediaCollectionResult
import com.mediavault.core.model.MediaFormat
import com.mediavault.core.model.MediaType
import java.util.Locale

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

/** Compact title for a quality-tier variant row, e.g. "2160p60", "1080p". Never invents a resolution the source didn't report. */
fun videoVariantTitle(format: MediaFormat): String =
    listOfNotNull(format.resolutionLabel, format.fps?.takeIf { it > 0 }?.let { "${it}fps" })
        .joinToString(" ").ifBlank { "Video" }

/** Detail line for a variant row: container, codec, and this variant's own size — the audio question is handled by the separate Audio section now, never folded into this line. */
fun videoVariantSubtitle(format: MediaFormat): String {
    val parts = listOfNotNull(
        format.container.takeIf { it != "unknown" }?.uppercase(),
        format.videoCodec,
        formatFileSizeLabel(format.estimatedSizeBytes),
    )
    return parts.joinToString(" • ")
}

/**
 * Real display name for an audio track's language, from the JDK's own locale data — e.g. "hi"
 * -> "Hindi", "zh-Hans" -> "Chinese (Simplified)" — never a hand-maintained guess table. Falls
 * back to the raw code when [Locale] can't resolve a name for it (still real, source-reported
 * data, just not translatable to a name), and only says "Unknown language" when the source
 * reported no code at all.
 */
fun audioLanguageDisplayName(languageCode: String?): String {
    if (languageCode.isNullOrBlank()) return "Unknown language"
    val displayName = runCatching { Locale.forLanguageTag(languageCode).displayName }.getOrNull()
    return displayName?.takeIf { it.isNotBlank() && !it.equals(languageCode, ignoreCase = true) } ?: languageCode
}

/** Detail line for an audio-track row: codec, bitrate, and estimated size — language is the row's own title (see [audioLanguageDisplayName]), never repeated here. */
fun audioTrackSubtitle(format: MediaFormat): String {
    val parts = listOfNotNull(
        format.audioCodec,
        format.bitrateKbps?.let { "$it kbps" },
        formatFileSizeLabel(format.estimatedSizeBytes),
    )
    return parts.joinToString(" • ")
}

/**
 * The "what you've picked" half of a selection summary, e.g. `"1080p + Hindi, English"` or
 * `"720p"` for a muxed/no-extra-audio pick, or just the language for a bare audio-only pick.
 * Used alone by the playlist bar (which shows its own *total*-across-every-item size
 * separately — see [estimatedPlaylistTotalSizeBytes]) and combined with [selection]'s own size
 * by [selectedQualitySummaryLabel] for the single-item bar.
 */
fun selectedQualityLabel(selection: ResolvedSelection): String {
    val video = selection.videoFormat
    return when {
        video != null && selection.audioFormats.isNotEmpty() ->
            "${videoVariantTitle(video)} + " + selection.audioFormats.joinToString(", ") { audioLanguageDisplayName(it.languageCode) }
        video != null -> videoVariantTitle(video)
        else -> selection.audioFormats.firstOrNull()?.let { audioLanguageDisplayName(it.languageCode) } ?: "Audio"
    }
}

/**
 * The persistent Download bar's live "what you've picked, and how big it'll be" line, e.g.
 * `"1080p + Hindi, English • ≈320 MB"` — recomputed on every selection change from a fresh
 * [ResolvedSelection] (see `HomeViewModel.currentResolvedSelection`), never a value that can
 * drift out of sync with what's actually selected.
 */
fun selectedQualitySummaryLabel(selection: ResolvedSelection): String {
    val sizePart = formatFileSizeLabel(selection.combinedEstimatedSizeBytes)?.let { "≈$it" }
    return listOfNotNull(selectedQualityLabel(selection), sizePart).joinToString(" • ")
}

/**
 * Rough aggregate estimate for the playlist download-setup bar: every item priced the same as
 * [selection]'s own final size (already the video+every-selected-audio-track sum — see
 * [ResolvedSelection.combinedEstimatedSizeBytes]), since playlist items don't get their own
 * resolved format list until each is analyzed individually at download time — an estimate,
 * never a guarantee. Null when the chosen selection's own size is unknown, never a guessed number.
 */
fun estimatedPlaylistTotalSizeBytes(selection: ResolvedSelection?, itemCount: Int): Long? {
    val perItem = selection?.combinedEstimatedSizeBytes ?: return null
    return perItem * itemCount
}

/**
 * Title to store for one enqueued collection-item download: the post's own caption when it has
 * one (numbered against [totalCount] so sibling items in the same carousel are distinguishable in
 * the Library/Downloads list), falling back to a generic "Image"/"Video" (or numbered
 * "Image N"/"Video N") matching [item]'s own [MediaCollectionItem.mediaType] — never blank, never
 * inventing wording the source didn't provide. [captionTitleLine] caps it to one short line —
 * a Downloads/Library row title is UI real estate, not a caption-display field, and a real
 * Instagram caption can run to several paragraphs (confirmed live: a NASA post's caption plus
 * its own appended accessibility alt-text overflowed the entire Downloads screen before this
 * cap existed). The full, untruncated caption stays intact on [MediaCollectionResult.title]
 * for wherever a caption genuinely should be shown in full (the analysis preview card).
 */
fun collectionItemTitle(collection: MediaCollectionResult, item: MediaCollectionItem, totalCount: Int): String {
    val caption = captionTitleLine(collection.title)
    val genericLabel = if (item.mediaType == MediaType.VIDEO) "Video" else "Image"
    return when {
        totalCount <= 1 && caption.isNotEmpty() -> caption
        totalCount <= 1 -> genericLabel
        caption.isNotEmpty() -> "$caption (${item.index}/$totalCount)"
        else -> "$genericLabel ${item.index}"
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
private val KNOWN_VIDEO_EXTENSIONS = setOf("mp4", "mov", "webm", "mkv")

/** The saved file's extension for a downloaded image: sniffed from the direct URL's own path when it looks like a real image extension, otherwise "jpg" — the container every image source tested so far actually serves, never a guess dressed up as certainty. */
fun imageContainerFor(imageUrl: String): String {
    val path = imageUrl.substringBefore('?').substringBefore('#')
    val extension = path.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return extension.takeIf { it in KNOWN_IMAGE_EXTENSIONS } ?: "jpg"
}

/** Same sniffing rule as [imageContainerFor], for a downloaded collection video's own direct URL — sniffed extension when it's a real video container, otherwise "mp4" (what every Instagram video item seen so far actually serves). */
fun videoContainerFor(videoUrl: String): String {
    val path = videoUrl.substringBefore('?').substringBefore('#')
    val extension = path.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return extension.takeIf { it in KNOWN_VIDEO_EXTENSIONS } ?: "mp4"
}

/** Dispatches to [imageContainerFor] or [videoContainerFor] by [item]'s own [MediaCollectionItem.mediaType] — the one place a caller needs to know a collection item's container, without repeating the image/video branch itself. */
fun collectionItemContainer(item: MediaCollectionItem): String {
    val url = item.mediaUrl ?: return if (item.mediaType == MediaType.VIDEO) "mp4" else "jpg"
    return if (item.mediaType == MediaType.VIDEO) videoContainerFor(url) else imageContainerFor(url)
}
