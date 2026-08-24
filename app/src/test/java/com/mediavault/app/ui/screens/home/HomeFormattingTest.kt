package com.mediavault.app.ui.screens.home

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
}
