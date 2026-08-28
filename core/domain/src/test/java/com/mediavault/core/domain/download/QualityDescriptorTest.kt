package com.mediavault.core.domain.download

import com.mediavault.core.model.MediaFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QualityDescriptorTest {

    private fun video(
        id: String,
        resolutionLabel: String? = "1080p",
        container: String = "mp4",
        hasAudio: Boolean = true,
    ) = MediaFormat(
        formatId = id,
        resolutionLabel = resolutionLabel,
        container = container,
        videoCodec = "avc1",
        audioCodec = if (hasAudio) "aac" else null,
        fps = 30,
        estimatedSizeBytes = 100_000L,
        hasVideo = true,
        hasAudio = hasAudio,
    )

    private fun audio(id: String, container: String = "m4a", languageCode: String? = null) = MediaFormat(
        formatId = id,
        resolutionLabel = null,
        container = container,
        videoCodec = null,
        audioCodec = "aac",
        fps = null,
        estimatedSizeBytes = 10_000L,
        hasVideo = false,
        hasAudio = true,
        languageCode = languageCode,
    )

    // --- from(DownloadOption) ---------------------------------------------------------------

    @Test
    fun `from a direct muxed option derives a descriptor from its shape`() {
        val option = buildDownloadOptions(listOf(video("m1"))).single()

        val descriptor = QualityDescriptor.from(option)

        assertEquals(QualityDescriptor("1080p", "mp4", hasVideo = true, hasAudio = true, requiresProcessing = false, audioLanguageCode = null), descriptor)
    }

    @Test
    fun `from a merge-required paired option carries requiresProcessing and the paired audio language`() {
        val options = buildDownloadOptions(listOf(video("v1", hasAudio = false), audio("a-en", languageCode = "en")))
        val paired = options.single { it.requiresProcessing }

        val descriptor = QualityDescriptor.from(paired)

        assertEquals("1080p", descriptor.resolutionLabel)
        assertEquals(true, descriptor.requiresProcessing)
        assertEquals("en", descriptor.audioLanguageCode)
        assertEquals(true, descriptor.hasVideo)
        assertEquals(true, descriptor.hasAudio)
    }

    // --- findMatching: direct qualities --------------------------------------------------

    @Test
    fun `findMatching returns the direct option whose shape matches exactly`() {
        val target = QualityDescriptor("1080p", "mp4", hasVideo = true, hasAudio = true)
        val options = buildDownloadOptions(
            listOf(video("1", resolutionLabel = "720p"), video("2", resolutionLabel = "1080p"), video("3", resolutionLabel = "1080p", container = "webm")),
        )

        assertEquals("2", options.findMatching(target)?.id)
    }

    @Test
    fun `findMatching returns null rather than substituting a different resolution`() {
        val target = QualityDescriptor("4K", "mp4", hasVideo = true, hasAudio = true)
        val options = buildDownloadOptions(listOf(video("1", resolutionLabel = "1080p"), video("2", resolutionLabel = "720p")))

        assertNull(options.findMatching(target))
    }

    @Test
    fun `findMatching on an empty list returns null`() {
        val target = QualityDescriptor("1080p", "mp4", hasVideo = true, hasAudio = true)

        assertNull(emptyList<DownloadOption>().findMatching(target))
    }

    // --- findMatching: merge-required qualities -------------------------------------------

    @Test
    fun `findMatching pairs a merge-required quality with the same resolution and audio language on another item`() {
        val target = QualityDescriptor("1080p", "mp4", hasVideo = true, hasAudio = true, requiresProcessing = true, audioLanguageCode = "en")
        val options = buildDownloadOptions(
            listOf(video("v1", hasAudio = false), audio("a-en", languageCode = "en"), audio("a-es", languageCode = "es")),
        )

        val matched = options.findMatching(target)

        assertEquals("en", matched?.audioFormat?.languageCode)
    }

    @Test
    fun `findMatching never falls back to a different audio language for a merge-required quality`() {
        val target = QualityDescriptor("1080p", "mp4", hasVideo = true, hasAudio = true, requiresProcessing = true, audioLanguageCode = "en")
        // This item only offers Spanish audio — "en" genuinely isn't available here.
        val options = buildDownloadOptions(listOf(video("v1", hasAudio = false), audio("a-es", languageCode = "es")))

        assertNull(options.findMatching(target))
    }

    @Test
    fun `findMatching never substitutes a direct option for a merge-required quality or vice versa`() {
        val direct = QualityDescriptor("1080p", "mp4", hasVideo = true, hasAudio = true, requiresProcessing = false)
        // This item's 1080p is muxed already — no paired option exists for it at all.
        val options = buildDownloadOptions(listOf(video("v1", hasAudio = true)))

        val target = direct.copy(requiresProcessing = true)

        assertNull(options.findMatching(target))
    }

    @Test
    fun `findMatching skips an unselectable video-only option with no compatible audio`() {
        val target = QualityDescriptor("1080p", "mp4", hasVideo = true, hasAudio = true, requiresProcessing = true, audioLanguageCode = null)
        // No audio-only format anywhere on this item -> the video-only format becomes a direct
        // (not paired) option, so a merge-required target must not match it.
        val options = buildDownloadOptions(listOf(video("v1", hasAudio = false)))

        assertNull(options.findMatching(target))
    }
}
