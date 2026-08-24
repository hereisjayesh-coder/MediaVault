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
    fun `format summary matches the product spec example`() {
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

        assertEquals("1080p • MP4 • 843 MB", formatFormatSummary(format))
    }
}
