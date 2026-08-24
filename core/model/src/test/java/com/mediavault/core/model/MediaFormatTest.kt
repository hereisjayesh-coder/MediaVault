package com.mediavault.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFormatTest {

    @Test
    fun `download status enum covers the full lifecycle`() {
        val expected = setOf(
            "QUEUED", "ANALYZING", "DOWNLOADING", "PROCESSING",
            "MERGING", "COMPLETED", "PAUSED", "CANCELLED", "FAILED",
        )
        assertEquals(expected, DownloadStatus.entries.map { it.name }.toSet())
    }

    @Test
    fun `media format equality is value-based`() {
        val a = MediaFormat(
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
        val b = a.copy()

        assertEquals(a, b)
        assertTrue(a.hasVideo)
        assertTrue(!a.hasAudio)
    }
}
