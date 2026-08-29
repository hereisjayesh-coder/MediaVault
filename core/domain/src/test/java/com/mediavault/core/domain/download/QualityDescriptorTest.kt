package com.mediavault.core.domain.download

import com.mediavault.core.model.MediaFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QualityDescriptorTest {

    private fun video(id: String, heightPx: Int = 1080, hasAudio: Boolean = false) = MediaFormat(
        formatId = id,
        resolutionLabel = "${heightPx}p",
        container = "mp4",
        videoCodec = "avc1",
        audioCodec = if (hasAudio) "aac" else null,
        fps = 30,
        estimatedSizeBytes = 100_000L,
        hasVideo = true,
        hasAudio = hasAudio,
        heightPx = heightPx,
    )

    private fun audio(id: String, languageCode: String? = null) = MediaFormat(
        formatId = id,
        resolutionLabel = null,
        container = "m4a",
        videoCodec = null,
        audioCodec = "aac",
        fps = null,
        estimatedSizeBytes = 10_000L,
        hasVideo = false,
        hasAudio = true,
        languageCode = languageCode,
    )

    // --- from() --------------------------------------------------------------------------------

    @Test
    fun `from a direct muxed pick derives the tier with no audio languages`() {
        val descriptor = QualityDescriptor.from(video("m1", heightPx = 1080, hasAudio = true), emptyList())

        assertEquals(QualityDescriptor(QualityTier.FULL_HD_1080P, emptyList()), descriptor)
    }

    @Test
    fun `from a direct audio-only pick has a null tier`() {
        val descriptor = QualityDescriptor.from(null, listOf(audio("a1", languageCode = "en")))

        assertNull(descriptor.tier)
        assertEquals(listOf("en"), descriptor.audioLanguageCodes)
    }

    @Test
    fun `from a multi-audio pick carries every selected language`() {
        val descriptor = QualityDescriptor.from(video("v1"), listOf(audio("a-en", "en"), audio("a-hi", "hi")))

        assertEquals(QualityTier.FULL_HD_1080P, descriptor.tier)
        assertEquals(setOf("en", "hi"), descriptor.audioLanguageCodes.toSet())
    }

    // --- resolveForPlaylist: video tier matching -----------------------------------------------

    @Test
    fun `resolveForPlaylist finds the same tier on another item`() {
        val target = QualityDescriptor(QualityTier.FULL_HD_1080P, emptyList())
        val formats = listOf(video("1", heightPx = 720, hasAudio = true), video("2", heightPx = 1080, hasAudio = true))

        val resolved = formats.resolveForPlaylist(target)

        assertEquals("2", resolved?.videoFormat?.formatId)
    }

    @Test
    fun `resolveForPlaylist returns null rather than substituting a different tier`() {
        val target = QualityDescriptor(QualityTier.UHD_4K, emptyList())
        val formats = listOf(video("1", heightPx = 1080, hasAudio = true), video("2", heightPx = 720, hasAudio = true))

        assertNull(formats.resolveForPlaylist(target))
    }

    @Test
    fun `resolveForPlaylist on an empty format list returns null for a video quality`() {
        val target = QualityDescriptor(QualityTier.FULL_HD_1080P, emptyList())

        assertNull(emptyList<MediaFormat>().resolveForPlaylist(target))
    }

    // --- resolveForPlaylist: audio language matching -------------------------------------------

    @Test
    fun `resolveForPlaylist matches every requested audio language independently on another item`() {
        val target = QualityDescriptor(QualityTier.FULL_HD_1080P, listOf("en", "hi"))
        val formats = listOf(video("v1", hasAudio = false), audio("a-en", "en"), audio("a-hi", "hi"), audio("a-es", "es"))

        val resolved = formats.resolveForPlaylist(target)

        assertEquals(setOf("en", "hi"), resolved?.audioFormats?.map { it.languageCode }?.toSet())
    }

    @Test
    fun `resolveForPlaylist fails the whole item when even one requested language is missing, never substituting another`() {
        val target = QualityDescriptor(QualityTier.FULL_HD_1080P, listOf("en", "hi"))
        // This item only offers English — Hindi genuinely isn't available here.
        val formats = listOf(video("v1", hasAudio = false), audio("a-en", "en"))

        assertNull(formats.resolveForPlaylist(target))
    }

    @Test
    fun `resolveForPlaylist never substitutes a direct pick for a merge-required one or vice versa`() {
        val target = QualityDescriptor(QualityTier.FULL_HD_1080P, listOf("en"))
        // This item's 1080p is muxed already — no separate audio track exists to merge in.
        val formats = listOf(video("v1", hasAudio = true))

        assertNull(formats.resolveForPlaylist(target))
    }

    @Test
    fun `resolveForPlaylist with no requested audio languages resolves a direct pick`() {
        val target = QualityDescriptor(QualityTier.FULL_HD_1080P, emptyList())
        val formats = listOf(video("v1", hasAudio = true))

        val resolved = formats.resolveForPlaylist(target)

        assertEquals(false, resolved?.requiresProcessing)
    }
}
