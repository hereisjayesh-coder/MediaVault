package com.mediavault.app.ui.screens.home

import com.mediavault.core.domain.download.buildDownloadOptions
import com.mediavault.core.model.MediaFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    // --- Download option rows (video/audio section formatting) ------------------------

    @Test
    fun `a muxed direct option correctly reports its own embedded audio, not 'video only'`() {
        val muxed = MediaFormat(
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

        val option = buildDownloadOptions(listOf(muxed)).single()

        assertEquals("Includes audio (aac)", audioAvailabilityLabel(option))
        assertEquals("720p 30fps", videoOptionTitle(option))
        assertEquals("MP4 • avc1 • 95 MB • Includes audio (aac)", videoOptionSubtitle(option))
    }

    @Test
    fun `a paired video-only option reports the merged-in audio track`() {
        val video = MediaFormat(
            formatId = "137", resolutionLabel = "1080p", container = "mp4", videoCodec = "avc1",
            audioCodec = null, fps = 30, estimatedSizeBytes = 80_000_000L, hasVideo = true, hasAudio = false,
        )
        val audio = MediaFormat(
            formatId = "140", resolutionLabel = null, container = "m4a", videoCodec = null,
            audioCodec = "aac", fps = null, estimatedSizeBytes = 4_000_000L, hasVideo = false, hasAudio = true,
            languageCode = "en",
        )

        val option = buildDownloadOptions(listOf(video, audio)).single { it.requiresProcessing }

        assertEquals("+ audio (aac) [en]", audioAvailabilityLabel(option))
    }

    @Test
    fun `an unavailable video-only option reports no audio available`() {
        val video = MediaFormat(
            formatId = "137", resolutionLabel = "1080p", container = "mp4", videoCodec = "avc1",
            audioCodec = null, fps = 30, estimatedSizeBytes = 80_000_000L, hasVideo = true, hasAudio = false,
        )

        val option = buildDownloadOptions(listOf(video)).single()

        assertEquals("No audio available", audioAvailabilityLabel(option))
    }

    @Test
    fun `an audio-only option shows format, bitrate, size, and language`() {
        val audio = MediaFormat(
            formatId = "140", resolutionLabel = null, container = "m4a", videoCodec = null,
            audioCodec = "aac", fps = null, estimatedSizeBytes = 5_000_000L, hasVideo = false, hasAudio = true,
            languageCode = "en", bitrateKbps = 128,
        )

        val option = buildDownloadOptions(listOf(audio)).single()

        assertEquals("M4A", audioOptionTitle(option))
        assertEquals("aac • 128 kbps • 5 MB • [en]", audioOptionSubtitle(option))
    }

    @Test
    fun `playlist quality label shows resolution and fps for video, container for audio`() {
        val video = MediaFormat(
            formatId = "137", resolutionLabel = "1080p", container = "mp4", videoCodec = "avc1",
            audioCodec = "aac", fps = 60, estimatedSizeBytes = 100_000_000L, hasVideo = true, hasAudio = true,
        )
        val audio = MediaFormat(
            formatId = "140", resolutionLabel = null, container = "m4a", videoCodec = null,
            audioCodec = "aac", fps = null, estimatedSizeBytes = 5_000_000L, hasVideo = false, hasAudio = true,
        )
        val videoOption = buildDownloadOptions(listOf(video)).single()
        val audioOption = buildDownloadOptions(listOf(audio)).single()

        assertEquals("1080p 60fps", playlistQualityLabel(videoOption))
        assertEquals("M4A", playlistQualityLabel(audioOption))
    }

    @Test
    fun `estimated playlist total is per-item size times item count`() {
        val format = MediaFormat(
            formatId = "137", resolutionLabel = "1080p", container = "mp4", videoCodec = "avc1",
            audioCodec = "aac", fps = 30, estimatedSizeBytes = 10_000_000L, hasVideo = true, hasAudio = true,
        )
        val option = buildDownloadOptions(listOf(format)).single()

        assertEquals(30_000_000L, estimatedPlaylistTotalSizeBytes(option, 3))
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
        val paired = buildDownloadOptions(listOf(video, audio)).single { it.requiresProcessing }

        // 84 MB combined per item, times 3 items — never the video-only size alone.
        assertEquals(252_000_000L, estimatedPlaylistTotalSizeBytes(paired, 3))
    }

    @Test
    fun `selected option summary combines title and final size`() {
        val video = MediaFormat(
            formatId = "137", resolutionLabel = "1080p", container = "mp4", videoCodec = "avc1",
            audioCodec = null, fps = 60, estimatedSizeBytes = 80_000_000L, hasVideo = true, hasAudio = false,
        )
        val audio = MediaFormat(
            formatId = "140", resolutionLabel = null, container = "m4a", videoCodec = null,
            audioCodec = "aac", fps = null, estimatedSizeBytes = 4_000_000L, hasVideo = false, hasAudio = true,
        )
        val option = buildDownloadOptions(listOf(video, audio)).single { it.requiresProcessing }

        assertEquals("1080p 60fps • 80 MB", selectedOptionSummaryLabel(option))
    }

    @Test
    fun `estimated playlist total is null when the chosen format's size is unknown`() {
        val format = MediaFormat(
            formatId = "137", resolutionLabel = "1080p", container = "mp4", videoCodec = "avc1",
            audioCodec = "aac", fps = 30, estimatedSizeBytes = null, hasVideo = true, hasAudio = true,
        )
        val option = buildDownloadOptions(listOf(format)).single()

        assertNull(estimatedPlaylistTotalSizeBytes(option, 3))
    }
}
