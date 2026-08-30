package com.mediavault.core.extractor.ytdlp.json

import com.mediavault.core.domain.extractor.ExtractionResult
import com.mediavault.core.domain.extractor.MediaAnalysisResult
import com.mediavault.core.domain.extractor.MediaCollectionItem
import com.mediavault.core.domain.extractor.MediaCollectionResult
import com.mediavault.core.domain.extractor.PlaylistAnalysisResult
import com.mediavault.core.domain.extractor.PlaylistCollectionType
import com.mediavault.core.domain.extractor.PlaylistItem
import com.mediavault.core.model.MediaFormat
import com.mediavault.core.model.MediaTrackInfo
import com.mediavault.core.model.MediaType
import com.mediavault.core.model.SubtitleTrackInfo
import kotlin.math.floor
import kotlin.math.roundToInt

/** Converts yt-dlp's raw info-dict shape into MediaVault's engine-agnostic domain model. */
fun YtDlpInfoJson.toExtractionResult(): ExtractionResult =
    when {
        imageUrl != null -> ExtractionResult.Collection(toMediaCollectionResult())
        entries != null -> ExtractionResult.Playlist(toPlaylistAnalysisResult())
        else -> ExtractionResult.Single(toMediaAnalysisResult())
    }

/**
 * A single-image Reddit post — always exactly one item (yt-dlp's Reddit extractor has no
 * reliable multi-image/gallery support; see `mediavault_ytdlp.py`'s own `analyze()` for why a
 * gallery post is rejected before it ever reaches here, rather than silently truncated to one
 * image). Same [MediaCollectionResult] shape a multi-image Instagram carousel uses — a
 * single-image post is just a one-item collection there too, so the rest of the app (download
 * queueing, Library, the image viewer) needs no Reddit-specific code at all.
 */
private fun YtDlpInfoJson.toMediaCollectionResult(): MediaCollectionResult {
    val bestThumbnail = thumbnail
        ?: thumbnails.orEmpty().maxByOrNull { it.preference ?: Int.MIN_VALUE }?.url
    val postId = id?.takeIf { it.isNotBlank() } ?: "unknown"
    return MediaCollectionResult(
        id = postId,
        sourceName = extractorKey ?: extractor ?: "Unknown",
        title = title?.takeIf { it.isNotBlank() }.orEmpty(),
        thumbnailUrl = bestThumbnail ?: imageUrl,
        webpageUrl = webpageUrl,
        items = listOf(
            MediaCollectionItem(
                id = "${postId}_1",
                index = 1,
                mediaType = MediaType.IMAGE,
                mediaUrl = requireNotNull(imageUrl),
                isAvailable = true,
                thumbnailUrl = bestThumbnail ?: imageUrl,
            ),
        ),
    )
}

private fun YtDlpInfoJson.toMediaAnalysisResult(): MediaAnalysisResult {
    val allFormats = formats.orEmpty()
    val bestThumbnail = thumbnail
        ?: thumbnails.orEmpty().maxByOrNull { it.preference ?: Int.MIN_VALUE }?.url

    return MediaAnalysisResult(
        id = id?.takeIf { it.isNotBlank() } ?: "unknown",
        sourceName = extractorKey ?: extractor ?: "Unknown",
        title = title?.takeIf { it.isNotBlank() } ?: id?.takeIf { it.isNotBlank() } ?: "Untitled",
        durationSeconds = duration?.let { floor(it).toLong() },
        thumbnailUrl = bestThumbnail,
        webpageUrl = webpageUrl,
        // Includes audio-only formats — they're a legitimate download choice (and, unlike
        // video-only formats, need no FFmpeg merge — see HomeViewModel.isSelectableForDownload).
        // Storyboard/thumbnail-scrubbing entries (vcodec=none, acodec=none) are excluded.
        formats = allFormats.filter { it.hasVideo() || it.hasAudio() }.map { it.toMediaFormat() },
        audioTracks = allFormats.filter { it.isAudioOnly() }.toAudioTracks(defaultLanguage = language),
        subtitleTracks = subtitles.toSubtitleTracks(),
    )
}

private fun YtDlpInfoJson.toPlaylistAnalysisResult(): PlaylistAnalysisResult {
    val bestThumbnail = thumbnail
        ?: thumbnails.orEmpty().maxByOrNull { it.preference ?: Int.MIN_VALUE }?.url
    val rawEntries = entries.orEmpty()
    val items = rawEntries.mapIndexed { position, entry -> entry.toPlaylistItem(index = position + 1) }

    return PlaylistAnalysisResult(
        sourceName = extractorKey ?: extractor ?: "Unknown",
        title = title?.takeIf { it.isNotBlank() } ?: id?.takeIf { it.isNotBlank() } ?: "Untitled playlist",
        thumbnailUrl = bestThumbnail,
        webpageUrl = webpageUrl,
        collectionType = classifyCollectionType(),
        itemCount = playlistCount ?: rawEntries.size.takeIf { it > 0 },
        items = items,
    )
}

/**
 * Best-effort classification from the extractor's own name — yt-dlp does not expose a
 * clean, extractor-independent "is this a channel" flag, so this only recognizes the
 * common naming pattern ("...Channel...", "...Playlist...") rather than guaranteeing
 * correctness across all ~1700 extractors.
 */
private fun YtDlpInfoJson.classifyCollectionType(): PlaylistCollectionType {
    val signal = (extractorKey ?: extractor ?: "").lowercase()
    return when {
        "channel" in signal -> PlaylistCollectionType.CHANNEL
        "playlist" in signal -> PlaylistCollectionType.PLAYLIST
        else -> PlaylistCollectionType.OTHER
    }
}

private val UNAVAILABLE_AVAILABILITY = setOf("private", "needs_auth", "premium_only", "subscriber_only")
private val UNAVAILABLE_TITLE_MARKERS = listOf("[private video]", "[deleted video]", "[unavailable]")

/** A `null` entry means yt-dlp couldn't produce anything at all for that playlist slot. */
private fun YtDlpEntryJson?.toPlaylistItem(index: Int): PlaylistItem {
    if (this == null) {
        return PlaylistItem(
            id = "unavailable-$index",
            index = index,
            title = "Unavailable item",
            thumbnailUrl = null,
            durationSeconds = null,
            url = null,
            isAvailable = false,
        )
    }

    val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: "Untitled"
    val availableByAvailability = availability == null || availability !in UNAVAILABLE_AVAILABILITY
    val availableByTitle = resolvedTitle.lowercase() !in UNAVAILABLE_TITLE_MARKERS
    val bestThumbnail = thumbnail
        ?: thumbnails.orEmpty().maxByOrNull { it.preference ?: Int.MIN_VALUE }?.url

    return PlaylistItem(
        id = id?.takeIf { it.isNotBlank() } ?: "unavailable-$index",
        index = index,
        title = resolvedTitle,
        thumbnailUrl = bestThumbnail,
        durationSeconds = duration?.let { floor(it).toLong() },
        url = (webpageUrl ?: url)?.takeIf { it.isNotBlank() },
        isAvailable = availableByAvailability && availableByTitle,
    )
}

private fun YtDlpFormatJson.hasVideo(): Boolean = vcodec != null && vcodec != "none"

private fun YtDlpFormatJson.hasAudio(): Boolean = acodec != null && acodec != "none"

private fun YtDlpFormatJson.isAudioOnly(): Boolean = hasAudio() && !hasVideo()

private fun YtDlpFormatJson.toMediaFormat(): MediaFormat {
    val isVideo = hasVideo()
    val isAudio = hasAudio()
    return MediaFormat(
        formatId = formatId ?: "unknown",
        resolutionLabel = resolution ?: height?.let { "${it}p" },
        container = ext ?: "unknown",
        videoCodec = vcodec.takeIf { isVideo },
        audioCodec = acodec.takeIf { isAudio },
        fps = fps?.roundToInt(),
        estimatedSizeBytes = filesize ?: filesizeApprox,
        hasVideo = isVideo,
        hasAudio = isAudio,
        supportsResume = protocol == "http" || protocol == "https",
        heightPx = height?.takeIf { isVideo },
        widthPx = width?.takeIf { isVideo },
        languageCode = language,
        bitrateKbps = (abr ?: tbr)?.roundToInt(),
    )
}

/**
 * Groups audio-only formats into distinct tracks by language. Formats are typically
 * offered at several bitrates per language, so grouping by bitrate would produce
 * misleading duplicate "tracks" — language is the only signal that reliably distinguishes
 * one real audio track from another quality variant of the same track.
 */
private fun List<YtDlpFormatJson>.toAudioTracks(defaultLanguage: String?): List<MediaTrackInfo> {
    if (isEmpty()) return emptyList()
    val languages = mapNotNull { it.language }.distinct()
    if (languages.isEmpty()) {
        // No language metadata anywhere: there is exactly one (unlabeled) audio track.
        return listOf(MediaTrackInfo(id = "default", languageCode = null, label = null, isDefault = true))
    }
    return languages.map { lang ->
        MediaTrackInfo(
            id = lang,
            languageCode = lang,
            label = null,
            isDefault = lang == defaultLanguage,
        )
    }.let { tracks ->
        if (tracks.none { it.isDefault }) {
            tracks.mapIndexed { index, track -> if (index == 0) track.copy(isDefault = true) else track }
        } else {
            tracks
        }
    }
}

private fun Map<String, List<YtDlpSubtitleEntryJson>>?.toSubtitleTracks(): List<SubtitleTrackInfo> =
    this?.keys.orEmpty().sorted().map { lang ->
        SubtitleTrackInfo(id = lang, languageCode = lang, label = null, isForced = false)
    }
