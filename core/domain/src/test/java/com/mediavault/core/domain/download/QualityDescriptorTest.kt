package com.mediavault.core.domain.download

import com.mediavault.core.model.MediaFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QualityDescriptorTest {

    private fun format(
        formatId: String,
        resolutionLabel: String? = "1080p",
        container: String = "mp4",
        hasVideo: Boolean = true,
        hasAudio: Boolean = true,
    ) = MediaFormat(
        formatId = formatId,
        resolutionLabel = resolutionLabel,
        container = container,
        videoCodec = if (hasVideo) "avc1" else null,
        audioCodec = if (hasAudio) "aac" else null,
        fps = if (hasVideo) 30 else null,
        estimatedSizeBytes = 100_000L,
        hasVideo = hasVideo,
        hasAudio = hasAudio,
    )

    @Test
    fun `from derives a descriptor from a format's shape, ignoring its formatId`() {
        val descriptor = QualityDescriptor.from(format("137"))

        assertEquals(QualityDescriptor("1080p", "mp4", hasVideo = true, hasAudio = true), descriptor)
    }

    @Test
    fun `findMatching returns the format whose shape matches exactly`() {
        val target = QualityDescriptor("1080p", "mp4", hasVideo = true, hasAudio = true)
        val formats = listOf(
            format("1", resolutionLabel = "720p"),
            format("2", resolutionLabel = "1080p"),
            format("3", resolutionLabel = "1080p", container = "webm"),
        )

        assertEquals("2", formats.findMatching(target)?.formatId)
    }

    @Test
    fun `findMatching returns null rather than substituting a different quality`() {
        val target = QualityDescriptor("4K", "mp4", hasVideo = true, hasAudio = true)
        val formats = listOf(format("1", resolutionLabel = "1080p"), format("2", resolutionLabel = "720p"))

        assertNull(formats.findMatching(target))
    }

    @Test
    fun `findMatching distinguishes audio-only from muxed at the same resolution label`() {
        val target = QualityDescriptor(resolutionLabel = null, container = "m4a", hasVideo = false, hasAudio = true)
        val formats = listOf(
            format("video", resolutionLabel = null, container = "m4a", hasVideo = true, hasAudio = true),
            format("audio", resolutionLabel = null, container = "m4a", hasVideo = false, hasAudio = true),
        )

        assertEquals("audio", formats.findMatching(target)?.formatId)
    }

    @Test
    fun `findMatching on an empty list returns null`() {
        val target = QualityDescriptor("1080p", "mp4", hasVideo = true, hasAudio = true)

        assertNull(emptyList<MediaFormat>().findMatching(target))
    }
}
