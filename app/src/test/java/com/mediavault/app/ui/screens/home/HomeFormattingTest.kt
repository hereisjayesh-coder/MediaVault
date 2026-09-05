package com.mediavault.app.ui.screens.home

import com.mediavault.core.domain.download.resolveSelection
import com.mediavault.core.model.MediaFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFormattingTest {

    @Test
    fun `duration under an hour omits the hours component`() {
        assertEquals("9:56", formatDurationLabel(596))
    }

    @Test
    fun `duration over an hour includes the hours component`() {
        assertEquals("1:02:05", formatDurationLabel(3725))
    }

    @Test
    fun `unknown duration formats to null`() {
        assertNull(formatDurationLabel(null))
    }

    @Test
    fun `file size formats to whole megabytes`() {
        assertEquals("843 MB", formatFileSizeLabel(884_000_000L))
    }

    @Test
    fun `video-only format summary flags it as video only`() {
        val format = MediaFormat(
            formatId = "137",
            resolutionLabel = "1080p",
            container = "mp4",
            videoCodec = "avc1",
            audioCodec = null,
            fps = 30,
            estimatedSizeBytes = 884_000_000L,
            hasVideo = true,
            hasAudio = false,
        )

        assertEquals("1080p 30fps • MP4 • avc1 • 843 MB • video only", formatFormatSummary(format))
    }

    @Test
    fun `muxed format summary shows the audio codec`() {
        val format = MediaFormat(
            formatId = "22",
            resolutionLabel = "720p",
            container = "mp4",
            videoCodec = "avc1",
            audioCodec = "aac",
            fps = 30,
            estimatedSizeBytes = 100_000_000L,
            hasVideo = true,
            hasAudio = true,
        )

        assertEquals("720p 30fps • MP4 • avc1 • 95 MB • with audio (aac)", formatFormatSummary(format))
    }

    @Test
    fun `audio-only format summary omits video-only fields`() {
        val format = MediaFormat(
            formatId = "140",
            resolutionLabel = null,
            container = "m4a",
            videoCodec = null,
            audioCodec = "aac",
            fps = null,
            estimatedSizeBytes = 5_000_000L,
            hasVideo = false,
            hasAudio = true,
        )

        assertEquals("M4A • 5 MB • audio only (aac)", formatFormatSummary(format))
    }

    // --- Quality-tier variant / audio-track row formatting ------------------------------

    @Test
    fun `variant title shows resolution and fps`() {
        val format = MediaFormat(
            formatId = "137", resolutionLabel = "1080p", container = "mp4", videoCodec = "avc1",
            audioCodec = null, fps = 60, estimatedSizeBytes = 100_000_000L, hasVideo = true, hasAudio = false,
        )

        assertEquals("1080p 60fps", videoVariantTitle(format))
    }

    @Test
    fun `variant subtitle shows container codec and size, never the audio question`() {
        val format = MediaFormat(
            formatId = "137", resolutionLabel = "1080p", container = "mp4", videoCodec = "avc1",
            audioCodec = null, fps = 30, estimatedSizeBytes = 100_000_000L, hasVideo = true, hasAudio = false,
        )

        assertEquals("MP4 • avc1 • 95 MB", videoVariantSubtitle(format))
    }

    @Test
    fun `audio language display name resolves a real name from the ISO code, never a raw code when one is resolvable`() {
        assertEquals("Hindi", audioLanguageDisplayName("hi"))
        assertEquals("English", audioLanguageDisplayName("en"))
    }

    @Test
    fun `audio language display name falls back to the raw code when Locale has no real name for it`() {
        // "xx" is a syntactically valid but unassigned ISO 639-1 code — Locale resolves no
        // localized name for it, so the raw code itself is the honest thing to show.
        assertEquals("xx", audioLanguageDisplayName("xx"))
    }

    @Test
    fun `audio language display name is a plain unknown label when the source reported no code at all`() {
        assertEquals("Unknown language", audioLanguageDisplayName(null))
    }

    @Test
    fun `audio track subtitle shows codec bitrate and size, language is the row's own title instead`() {
        val audio = MediaFormat(
            formatId = "140", resolutionLabel = null, container = "m4a", videoCodec = null,
            audioCodec = "aac", fps = null, estimatedSizeBytes = 5_000_000L, hasVideo = false, hasAudio = true,
            languageCode = "en", bitrateKbps = 128,
        )

        assertEquals("aac • 128 kbps • 5 MB", audioTrackSubtitle(audio))
    }

    @Test
    fun `selected quality label combines the video variant with every selected audio language`() {
        val video = MediaFormat(
            formatId = "137", resolutionLabel = "720p", container = "mp4", videoCodec = "avc1",
            audioCodec = null, fps = 30, estimatedSizeBytes = 147_000_000L, hasVideo = true, hasAudio = false,
        )
        val english = MediaFormat(
            formatId = "en", resolutionLabel = null, container = "m4a", videoCodec = null, audioCodec = "aac",
            fps = null, estimatedSizeBytes = 85_000_000L, hasVideo = false, hasAudio = true, languageCode = "en",
        )
        val hindi = english.copy(formatId = "hi", languageCode = "hi", estimatedSizeBytes = 88_000_000L)

        val selection = resolveSelection(video, listOf(english, hindi))

        assertEquals("720p 30fps + English, Hindi", selectedQualityLabel(selection))
        // formatFileSizeLabel uses binary (1024-based) MB, so 320,000,000 decimal bytes rounds to 305 MB, not 320.
        assertEquals("720p 30fps + English, Hindi • ≈305 MB", selectedQualitySummaryLabel(selection))
    }

    @Test
    fun `selected quality label for a direct pick omits the plus-audio suffix entirely`() {
        val video = MediaFormat(
            formatId = "137", resolutionLabel = "1080p", container = "mp4", videoCodec = "avc1",
            audioCodec = "aac", fps = 60, estimatedSizeBytes = 100_000_000L, hasVideo = true, hasAudio = true,
        )

        assertEquals("1080p 60fps", selectedQualityLabel(resolveSelection(video, emptyList())))
    }

    @Test
    fun `estimated playlist total is per-item size times item count`() {
        val format = MediaFormat(
            formatId = "137", resolutionLabel = "1080p", container = "mp4", videoCodec = "avc1",
            audioCodec = "aac", fps = 30, estimatedSizeBytes = 10_000_000L, hasVideo = true, hasAudio = true,
        )

        assertEquals(30_000_000L, estimatedPlaylistTotalSizeBytes(resolveSelection(format, emptyList()), 3))
    }

    @Test
    fun `estimated playlist total for a merge-required quality uses the combined video+audio size`() {
        val video = MediaFormat(
            formatId = "v1", resolutionLabel = "1080p", container = "mp4", videoCodec = "avc1",
            audioCodec = null, fps = 60, estimatedSizeBytes = 80_000_000L, hasVideo = true, hasAudio = false,
        )
        val audio = MediaFormat(
            formatId = "a1", resolutionLabel = null, container = "m4a", videoCodec = null,
            audioCodec = "aac", fps = null, estimatedSizeBytes = 4_000_000L, hasVideo = false, hasAudio = true,
        )

        // 84 MB combined per item, times 3 items — never the video-only size alone.
        assertEquals(252_000_000L, estimatedPlaylistTotalSizeBytes(resolveSelection(video, listOf(audio)), 3))
    }

    @Test
    fun `estimated playlist total is null when the chosen format's size is unknown`() {
        val format = MediaFormat(
            formatId = "137", resolutionLabel = "1080p", container = "mp4", videoCodec = "avc1",
            audioCodec = "aac", fps = 30, estimatedSizeBytes = null, hasVideo = true, hasAudio = true,
        )

        assertNull(estimatedPlaylistTotalSizeBytes(resolveSelection(format, emptyList()), 3))
    }

    // --- Collection titles / container detection ---------------------------------------

    private fun sampleCollection(title: String = "A caption") = com.mediavault.core.domain.extractor.MediaCollectionResult(
        id = "abc", sourceName = "Instagram", title = title, thumbnailUrl = null, webpageUrl = null, items = emptyList(),
    )

    private fun sampleItem(
        index: Int,
        mediaType: com.mediavault.core.model.MediaType = com.mediavault.core.model.MediaType.IMAGE,
        mediaUrl: String? = "https://cdn.example.com/i$index.jpg",
        isAvailable: Boolean = true,
    ) = com.mediavault.core.domain.extractor.MediaCollectionItem(
        id = "abc_$index", index = index, mediaType = mediaType, mediaUrl = mediaUrl, isAvailable = isAvailable, thumbnailUrl = null,
    )

    @Test
    fun `a single-image title uses the caption verbatim, with no numbering`() {
        assertEquals("A caption", collectionItemTitle(sampleCollection(), sampleItem(1), totalCount = 1))
    }

    @Test
    fun `a single image with no caption falls back to a generic title, never blank`() {
        assertEquals("Image", collectionItemTitle(sampleCollection(title = ""), sampleItem(1), totalCount = 1))
    }

    @Test
    fun `a carousel item title numbers the caption against the total item count`() {
        assertEquals("A caption (2/5)", collectionItemTitle(sampleCollection(), sampleItem(2), totalCount = 5))
    }

    @Test
    fun `a carousel item with no caption falls back to a generic numbered title`() {
        assertEquals("Image 3", collectionItemTitle(sampleCollection(title = "  "), sampleItem(3), totalCount = 5))
    }

    @Test
    fun `a video item's generic title says Video, never Image, matching its real media type`() {
        val video = sampleItem(3, mediaType = com.mediavault.core.model.MediaType.VIDEO)
        assertEquals("Video 3", collectionItemTitle(sampleCollection(title = "  "), video, totalCount = 5))
    }

    @Test
    fun `a single video with no caption falls back to a generic Video title`() {
        val video = sampleItem(1, mediaType = com.mediavault.core.model.MediaType.VIDEO)
        assertEquals("Video", collectionItemTitle(sampleCollection(title = ""), video, totalCount = 1))
    }

    @Test
    fun `a long caption is clipped to one short line, never overflowing the Downloads or Library row it becomes a title in`() {
        // Confirmed live on a Pixel 7a: a real NASA Instagram carousel's caption plus its own
        // appended "Image descriptions:" alt-text ran to several paragraphs and, before this
        // clip existed, became the stored task title verbatim — overflowing the entire
        // Downloads screen instead of a normal one-line row.
        val longCaption = "A".repeat(200)
        val title = collectionItemTitle(sampleCollection(title = longCaption), sampleItem(1), totalCount = 1)

        assertTrue(title.length <= 81) // 80 chars + the ellipsis character
        assertTrue(title.endsWith("…"))
    }

    @Test
    fun `a multi-line caption's title uses only the first non-blank line`() {
        val caption = "\n\nTwas the night before Christmas\nSecond paragraph goes on for a while here"
        val title = collectionItemTitle(sampleCollection(title = caption), sampleItem(1), totalCount = 1)

        assertEquals("Twas the night before Christmas", title)
    }

    @Test
    fun `image container is sniffed from a known extension in the direct URL`() {
        assertEquals("png", imageContainerFor("https://cdn.example.com/photo.png?w=1080"))
        assertEquals("webp", imageContainerFor("https://cdn.example.com/photo.webp"))
    }

    @Test
    fun `image container defaults to jpg when the URL has no recognizable extension`() {
        assertEquals("jpg", imageContainerFor("https://cdn.example.com/i/abc123def"))
    }

    @Test
    fun `video container is sniffed from a known extension in the direct URL`() {
        assertEquals("webm", videoContainerFor("https://cdn.example.com/clip.webm?w=1080"))
    }

    @Test
    fun `video container defaults to mp4 when the URL has no recognizable extension`() {
        assertEquals("mp4", videoContainerFor("https://cdn.example.com/v/abc123def"))
    }

    @Test
    fun `collectionItemContainer dispatches by the item's own media type`() {
        val image = sampleItem(1, mediaUrl = "https://cdn.example.com/photo.png")
        val video = sampleItem(2, mediaType = com.mediavault.core.model.MediaType.VIDEO, mediaUrl = "https://cdn.example.com/clip.mp4")

        assertEquals("png", collectionItemContainer(image))
        assertEquals("mp4", collectionItemContainer(video))
    }

    @Test
    fun `collectionItemContainer falls back to a sensible default when the item has no URL at all`() {
        val unavailableImage = sampleItem(1, mediaUrl = null, isAvailable = false)
        val unavailableVideo = sampleItem(2, mediaType = com.mediavault.core.model.MediaType.VIDEO, mediaUrl = null, isAvailable = false)

        assertEquals("jpg", collectionItemContainer(unavailableImage))
        assertEquals("mp4", collectionItemContainer(unavailableVideo))
    }

    // --- formatRelativeTimeLabel / recentActivitySubtitle (Recent Activity rows) ------------

    @Test
    fun `just under a minute ago reads as Just now`() {
        assertEquals("Just now", formatRelativeTimeLabel(epochMs = 999_500L, nowEpochMs = 1_000_000L))
    }

    @Test
    fun `minutes ago reads in whole minutes`() {
        assertEquals("5m ago", formatRelativeTimeLabel(epochMs = 0L, nowEpochMs = 5 * 60_000L))
    }

    @Test
    fun `hours ago reads in whole hours`() {
        assertEquals("3h ago", formatRelativeTimeLabel(epochMs = 0L, nowEpochMs = 3 * 3_600_000L))
    }

    @Test
    fun `days ago reads in whole days`() {
        assertEquals("2d ago", formatRelativeTimeLabel(epochMs = 0L, nowEpochMs = 2 * 86_400_000L))
    }

    @Test
    fun `a timestamp in the future never shows a negative duration`() {
        assertEquals("Just now", formatRelativeTimeLabel(epochMs = 10_000L, nowEpochMs = 0L))
    }

    @Test
    fun `recentActivitySubtitle names the media kind alongside the relative time`() {
        val video = com.mediavault.core.database.entity.MediaItemEntity(
            id = "v1",
            title = "A video",
            mediaUri = "file:///storage/v1.mp4",
            mediaType = com.mediavault.core.model.MediaType.VIDEO,
            durationMs = 1000L,
            sizeBytes = 1000L,
            container = "mp4",
            isImported = false,
            sourceDownloadTaskId = null,
            lastPlaybackPositionMs = 0L,
            isFavorite = false,
            addedAtEpochMs = 0L,
        )

        assertEquals("Video • 5m ago", recentActivitySubtitle(video, nowEpochMs = 5 * 60_000L))
        assertEquals("Audio • 5m ago", recentActivitySubtitle(video.copy(mediaType = com.mediavault.core.model.MediaType.AUDIO), nowEpochMs = 5 * 60_000L))
        assertEquals("Image • 5m ago", recentActivitySubtitle(video.copy(mediaType = com.mediavault.core.model.MediaType.IMAGE), nowEpochMs = 5 * 60_000L))
    }
}
