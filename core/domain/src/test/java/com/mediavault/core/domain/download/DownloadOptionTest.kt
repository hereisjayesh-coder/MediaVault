package com.mediavault.core.domain.download

import com.mediavault.core.model.MediaFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadOptionTest {

    private fun videoOnly(
        id: String,
        heightPx: Int,
        container: String = "mp4",
        sizeBytes: Long? = 100_000_000L,
    ) = MediaFormat(
        formatId = id,
        resolutionLabel = "${heightPx}p",
        container = container,
        videoCodec = "avc1",
        audioCodec = null,
        fps = 30,
        estimatedSizeBytes = sizeBytes,
        hasVideo = true,
        hasAudio = false,
        heightPx = heightPx,
        widthPx = heightPx * 16 / 9,
    )

    private fun audioOnly(
        id: String,
        container: String = "m4a",
        languageCode: String? = null,
        sizeBytes: Long? = 5_000_000L,
    ) = MediaFormat(
        formatId = id,
        resolutionLabel = null,
        container = container,
        videoCodec = null,
        audioCodec = "aac",
        fps = null,
        estimatedSizeBytes = sizeBytes,
        hasVideo = false,
        hasAudio = true,
        languageCode = languageCode,
    )

    private fun muxed(id: String, heightPx: Int = 360) = MediaFormat(
        formatId = id,
        resolutionLabel = "${heightPx}p",
        container = "mp4",
        videoCodec = "avc1",
        audioCodec = "aac",
        fps = 30,
        estimatedSizeBytes = 20_000_000L,
        hasVideo = true,
        hasAudio = true,
        heightPx = heightPx,
    )

    // --- Direct (unchanged) formats -------------------------------------------------------

    @Test
    fun `a muxed format becomes a direct, already-selectable option`() {
        val options = buildDownloadOptions(listOf(muxed("m1")))

        val option = options.single()
        assertEquals("m1", option.id)
        assertEquals(false, option.requiresProcessing)
        assertTrue(option.isSelectable)
        assertEquals(muxed("m1"), option.videoFormat)
        assertNull(option.audioFormat)
    }

    @Test
    fun `an audio-only format becomes a direct, already-selectable option`() {
        val audio = audioOnly("a1")
        val options = buildDownloadOptions(listOf(audio))

        val option = options.single()
        assertEquals("a1", option.id)
        assertEquals(false, option.requiresProcessing)
        assertTrue(option.isSelectable)
        assertNull(option.videoFormat)
        assertEquals(audio, option.audioFormat)
    }

    // --- Pairing ----------------------------------------------------------------------------

    @Test
    fun `a video-only format pairs with a same-family audio track into an mp4 option`() {
        val video = videoOnly("v1080", 1080, container = "mp4")
        val audio = audioOnly("a1", container = "m4a")

        val options = buildDownloadOptions(listOf(video, audio))

        val paired = options.single { it.requiresProcessing }
        assertEquals("v1080+a1", paired.id)
        assertTrue(paired.isSelectable)
        assertEquals("mp4", paired.outputContainer)
        assertEquals(video, paired.videoFormat)
        assertEquals(audio, paired.audioFormat)
    }

    @Test
    fun `webm video pairs with webm audio into a webm option`() {
        val video = videoOnly("v1", 1440, container = "webm")
        val audio = audioOnly("a1", container = "webm")

        val paired = buildDownloadOptions(listOf(video, audio)).single { it.requiresProcessing }

        assertEquals("webm", paired.outputContainer)
    }

    @Test
    fun `mismatched containers still pair, falling back to an mkv remux target`() {
        val video = videoOnly("v1", 2160, container = "webm")
        val audio = audioOnly("a1", container = "m4a")

        val paired = buildDownloadOptions(listOf(video, audio)).single { it.requiresProcessing }

        assertEquals("mkv", paired.outputContainer)
        assertTrue(paired.isSelectable)
    }

    @Test
    fun `combined estimated size is the sum of video and audio sizes`() {
        val video = videoOnly("v1", 1080, sizeBytes = 100_000_000L)
        val audio = audioOnly("a1", sizeBytes = 8_000_000L)

        val paired = buildDownloadOptions(listOf(video, audio)).single { it.requiresProcessing }

        assertEquals(108_000_000L, paired.combinedEstimatedSizeBytes)
    }

    @Test
    fun `combined size is null when either side's size is unknown, never a guess`() {
        val video = videoOnly("v1", 1080, sizeBytes = null)
        val audio = audioOnly("a1", sizeBytes = 8_000_000L)

        val paired = buildDownloadOptions(listOf(video, audio)).single { it.requiresProcessing }

        assertNull(paired.combinedEstimatedSizeBytes)
    }

    @Test
    fun `a video-only format with zero audio tracks anywhere is shown but marked unavailable`() {
        val video = videoOnly("v1", 1080)

        val options = buildDownloadOptions(listOf(video))

        val option = options.single()
        assertEquals(false, option.isSelectable)
        assertTrue(option.unavailableReason!!.isNotBlank())
        // Still shown with its real resolution/size — never hidden outright.
        assertEquals(video, option.videoFormat)
    }

    @Test
    fun `every distinct audio language produces its own selectable paired row, none dropped`() {
        val video = videoOnly("v1", 1080)
        val english = audioOnly("a-en", languageCode = "en")
        val spanish = audioOnly("a-es", languageCode = "es")

        val pairedOptions = buildDownloadOptions(listOf(video, english, spanish)).filter { it.requiresProcessing }

        assertEquals(2, pairedOptions.size)
        assertEquals(setOf("en", "es"), pairedOptions.mapNotNull { it.audioFormat?.languageCode }.toSet())
    }

    @Test
    fun `within one language, only the largest audio variant is offered, not every bitrate`() {
        val video = videoOnly("v1", 1080)
        val lowBitrate = audioOnly("a-low", languageCode = "en", sizeBytes = 2_000_000L)
        val highBitrate = audioOnly("a-high", languageCode = "en", sizeBytes = 9_000_000L)

        val pairedOptions = buildDownloadOptions(listOf(video, lowBitrate, highBitrate)).filter { it.requiresProcessing }

        assertEquals(1, pairedOptions.size)
        assertEquals("a-high", pairedOptions.single().audioFormat?.formatId)
    }

    @Test
    fun `never silently substitutes a different resolution's video for the one requested`() {
        val v720 = videoOnly("v720", 720)
        val v1080 = videoOnly("v1080", 1080)
        val audio = audioOnly("a1")

        val options = buildDownloadOptions(listOf(v720, v1080, audio)).filter { it.requiresProcessing }

        val resolutions = options.map { it.videoFormat?.heightPx }.toSet()
        assertEquals(setOf(720, 1080), resolutions)
        // Each paired id still points back at its own exact video format id.
        assertTrue(options.any { it.id == "v720+a1" })
        assertTrue(options.any { it.id == "v1080+a1" })
    }

    // --- No hardcoded resolution tiers ------------------------------------------------------

    @Test
    fun `every resolution the extractor reports gets a row, not just a fixed tier list`() {
        val heights = listOf(2160, 1440, 1080, 720, 480, 360, 240)
        val videos = heights.map { videoOnly("v$it", it) }
        val audio = audioOnly("a1")

        val options = buildDownloadOptions(videos + audio).filter { it.requiresProcessing }

        assertEquals(heights.toSet(), options.mapNotNull { it.videoFormat?.heightPx }.toSet())
    }

    // --- Sorting ------------------------------------------------------------------------------

    @Test
    fun `options are sorted by resolution descending`() {
        val v480 = videoOnly("v480", 480)
        val v1080 = videoOnly("v1080", 1080)
        val v720 = videoOnly("v720", 720)
        val audio = audioOnly("a1")

        val options = buildDownloadOptions(listOf(v480, v1080, v720, audio)).filter { it.requiresProcessing }

        assertEquals(listOf(1080, 720, 480), options.map { it.videoFormat?.heightPx })
    }

    @Test
    fun `an mp4-compatible pairing is preferred over other containers at the same height`() {
        val mp4Video = videoOnly("v-mp4", 1080, container = "mp4")
        val webmVideo = videoOnly("v-webm", 1080, container = "webm")
        val m4aAudio = audioOnly("a-m4a", container = "m4a")
        val webmAudio = audioOnly("a-webm", container = "webm")

        val options = buildDownloadOptions(listOf(mp4Video, webmVideo, m4aAudio, webmAudio)).filter { it.requiresProcessing }

        assertEquals("mp4", options.first().outputContainer)
    }
}
