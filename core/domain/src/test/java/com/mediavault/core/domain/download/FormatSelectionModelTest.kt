package com.mediavault.core.domain.download

import com.mediavault.core.model.MediaFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatSelectionModelTest {

    private fun video(
        id: String,
        heightPx: Int,
        container: String = "mp4",
        videoCodec: String? = "avc1",
        fps: Int? = 30,
        sizeBytes: Long? = 100_000_000L,
        hasAudio: Boolean = false,
    ) = MediaFormat(
        formatId = id,
        resolutionLabel = "${heightPx}p",
        container = container,
        videoCodec = videoCodec,
        audioCodec = if (hasAudio) "aac" else null,
        fps = fps,
        estimatedSizeBytes = sizeBytes,
        hasVideo = true,
        hasAudio = hasAudio,
        heightPx = heightPx,
        widthPx = heightPx * 16 / 9,
    )

    private fun audio(
        id: String,
        container: String = "m4a",
        languageCode: String? = null,
        bitrateKbps: Int? = 128,
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
        bitrateKbps = bitrateKbps,
    )

    private fun muxed(id: String, heightPx: Int) = video(id, heightPx, hasAudio = true)

    // --- Quality tier grouping ---------------------------------------------------------------

    @Test
    fun `formats are grouped into the correct quality tier by height`() {
        val formats = listOf(video("4k", 2160), video("1440", 1440), video("1080", 1080), video("720", 720), video("480", 480), video("240", 240))

        val model = formats.toFormatSelectionModel()

        val tiers = model.videoQualityGroups.associate { it.tier to it.bestVariant.formatId }
        assertEquals("4k", tiers[QualityTier.UHD_4K])
        assertEquals("1440", tiers[QualityTier.QHD_1440P])
        assertEquals("1080", tiers[QualityTier.FULL_HD_1080P])
        assertEquals("720", tiers[QualityTier.HD_720P])
        assertEquals("480", tiers[QualityTier.SD_480P])
        assertEquals("240", tiers[QualityTier.LOWER])
    }

    @Test
    fun `tiers are ordered highest quality first`() {
        val formats = listOf(video("480", 480), video("4k", 2160), video("720", 720))

        val model = formats.toFormatSelectionModel()

        assertEquals(listOf(QualityTier.UHD_4K, QualityTier.HD_720P, QualityTier.SD_480P), model.videoQualityGroups.map { it.tier })
    }

    @Test
    fun `a real modern source's 30+ raw video formats collapse into a handful of tiers, not one row each`() {
        // Mirrors the real shape confirmed live against a YouTube upload: three codecs at every
        // resolution from 4K down to 144p — the exact scenario this redesign exists to fix.
        val heights = listOf(2160, 1440, 1080, 720, 480, 360, 240, 144)
        val codecs = listOf("avc1", "vp9", "av01")
        val formats = heights.flatMap { height -> codecs.map { codec -> video("$height-$codec", height, videoCodec = codec) } }

        val model = formats.toFormatSelectionModel()

        assertEquals(24, formats.size)
        // 8 distinct resolutions collapse into 6 tiers — 360p/240p/144p all bucket into LOWER,
        // so that one tier alone carries 9 variants (3 heights x 3 codecs); every other tier
        // maps to exactly one height, so 3 variants (one per codec) each.
        assertEquals(6, model.videoQualityGroups.size)
        val byTier = model.videoQualityGroups.associate { it.tier to it.variants.size }
        assertEquals(3, byTier[QualityTier.UHD_4K])
        assertEquals(3, byTier[QualityTier.QHD_1440P])
        assertEquals(3, byTier[QualityTier.FULL_HD_1080P])
        assertEquals(3, byTier[QualityTier.HD_720P])
        assertEquals(3, byTier[QualityTier.SD_480P])
        assertEquals(9, byTier[QualityTier.LOWER])
    }

    // --- Variant dedup and ordering ------------------------------------------------------------

    @Test
    fun `duplicate rows at the same height fps codec and container collapse into one, preferring the one with a known size`() {
        val withSize = video("with-size", 1080, sizeBytes = 100_000_000L)
        val withoutSize = video("no-size", 1080, sizeBytes = null)

        val model = listOf(withoutSize, withSize).toFormatSelectionModel()

        val group = model.videoQualityGroups.single()
        assertEquals(1, group.variants.size)
        assertEquals("with-size", group.variants.single().formatId)
    }

    @Test
    fun `the default variant prefers H264 over VP9 over AV1`() {
        val av1 = video("av1", 1080, videoCodec = "av01.0.08M.08")
        val vp9 = video("vp9", 1080, videoCodec = "vp9")
        val h264 = video("h264", 1080, videoCodec = "avc1.4d4020")

        val group = listOf(av1, vp9, h264).toFormatSelectionModel().videoQualityGroups.single()

        assertEquals("h264", group.bestVariant.formatId)
    }

    // --- Audio track grouping ------------------------------------------------------------------

    @Test
    fun `audio tracks are offered only when a video-only format exists and separate audio exists to pair with it`() {
        val model = listOf(video("v1", 1080, hasAudio = false), audio("a-en", languageCode = "en")).toFormatSelectionModel()

        assertEquals(1, model.audioTracks.size)
    }

    @Test
    fun `no audio tracks are offered for a muxed-only source`() {
        val model = listOf(muxed("m1", 1080)).toFormatSelectionModel()

        assertTrue(model.audioTracks.isEmpty())
    }

    @Test
    fun `no audio tracks are offered for a video-only source with no separate audio anywhere`() {
        val model = listOf(video("v1", 1080, hasAudio = false)).toFormatSelectionModel()

        assertTrue(model.audioTracks.isEmpty())
    }

    @Test
    fun `every distinct language becomes its own audio track, none dropped`() {
        val model = listOf(
            video("v1", 1080, hasAudio = false),
            audio("a-en", languageCode = "en"),
            audio("a-hi", languageCode = "hi"),
            audio("a-es", languageCode = "es"),
        ).toFormatSelectionModel()

        assertEquals(setOf("en", "hi", "es"), model.audioTracks.map { it.languageCode }.toSet())
    }

    @Test
    fun `within one language, only the highest-bitrate variant is offered, not every quality tier`() {
        val model = listOf(
            video("v1", 1080, hasAudio = false),
            audio("a-low", languageCode = "en", bitrateKbps = 48),
            audio("a-high", languageCode = "en", bitrateKbps = 128),
        ).toFormatSelectionModel()

        assertEquals(1, model.audioTracks.size)
        assertEquals("a-high", model.audioTracks.single().formatId)
    }

    // --- resolveSelection: container/processing logic ------------------------------------------

    @Test
    fun `no audio selected keeps the video's own container, no processing`() {
        val video = video("v1", 1080, hasAudio = true)

        val selection = resolveSelection(video, emptyList())

        assertEquals(false, selection.requiresProcessing)
        assertEquals("mp4", selection.outputContainer)
    }

    @Test
    fun `one mp4-compatible audio track merges into mp4`() {
        val selection = resolveSelection(video("v1", 1080, container = "mp4"), listOf(audio("a1", container = "m4a")))

        assertEquals(true, selection.requiresProcessing)
        assertEquals("mp4", selection.outputContainer)
    }

    @Test
    fun `mismatched single-track containers fall back to mkv`() {
        val selection = resolveSelection(video("v1", 1080, container = "webm"), listOf(audio("a1", container = "m4a")))

        assertEquals("mkv", selection.outputContainer)
    }

    @Test
    fun `two or more audio tracks always mux into mkv, even when every track is mp4-compatible`() {
        val selection = resolveSelection(
            video("v1", 1080, container = "mp4"),
            listOf(audio("a-en", container = "m4a", languageCode = "en"), audio("a-hi", container = "m4a", languageCode = "hi")),
        )

        assertEquals(true, selection.requiresProcessing)
        assertEquals("mkv", selection.outputContainer)
    }

    @Test
    fun `combined size is the video plus every selected audio track's size`() {
        val selection = resolveSelection(
            video("v1", 1080, sizeBytes = 147_000_000L),
            listOf(audio("a-en", sizeBytes = 85_000_000L), audio("a-hi", sizeBytes = 88_000_000L)),
        )

        assertEquals(320_000_000L, selection.combinedEstimatedSizeBytes)
    }

    @Test
    fun `combined size is null when any selected component's size is unknown, never a guess`() {
        val selection = resolveSelection(video("v1", 1080, sizeBytes = null), listOf(audio("a1", sizeBytes = 5_000_000L)))

        assertNull(selection.combinedEstimatedSizeBytes)
    }

    @Test
    fun `primaryFormatId is the video when present, otherwise the sole direct audio format`() {
        val withVideo = resolveSelection(video("v1", 1080), listOf(audio("a1")))
        val audioOnly = resolveSelection(null, listOf(audio("a1")))

        assertEquals("v1", withVideo.primaryFormatId)
        assertEquals("a1", audioOnly.primaryFormatId)
    }
}
