package com.mediavault.core.extractor.ytdlp.json

import com.mediavault.core.domain.extractor.ExtractionResult
import com.mediavault.core.domain.extractor.MediaAnalysisResult
import com.mediavault.core.domain.extractor.PlaylistAnalysisResult
import com.mediavault.core.domain.extractor.PlaylistCollectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpResultMapperTest {

    @Test
    fun `maps a typical single-video result`() {
        val result = decodeSingle(SINGLE_VIDEO_JSON)

        assertEquals("abc123", result.id)
        assertEquals("Youtube", result.sourceName)
        assertEquals("Test video title", result.title)
        assertEquals(596L, result.durationSeconds)
        assertEquals("https://example.com/thumb-hq.jpg", result.thumbnailUrl)
        assertEquals("https://example.com/watch?v=abc123", result.webpageUrl)
    }

    @Test
    fun `only video-capable formats are exposed as formats`() {
        val result = decodeSingle(SINGLE_VIDEO_JSON)

        assertEquals(2, result.formats.size)
        assertTrue(result.formats.all { it.hasVideo })
        val hd = result.formats.first { it.formatId == "137" }
        assertEquals("1080p", hd.resolutionLabel)
        assertEquals("mp4", hd.container)
        assertEquals("avc1", hd.videoCodec)
        assertEquals(30, hd.fps)
        assertEquals(884_000_000L, hd.estimatedSizeBytes)
        assertTrue(hd.hasVideo)
        assertTrue(!hd.hasAudio)
    }

    @Test
    fun `audio-only formats become audio tracks grouped by language`() {
        val tracks = decodeSingle(MULTI_AUDIO_JSON).audioTracks

        assertEquals(2, tracks.size)
        assertEquals(setOf("en", "hi"), tracks.map { it.languageCode }.toSet())
        assertEquals(1, tracks.count { it.isDefault })
        assertEquals("en", tracks.first { it.isDefault }.languageCode)
    }

    @Test
    fun `no language metadata collapses audio formats into a single default track`() {
        val tracks = decodeSingle(SINGLE_VIDEO_JSON).audioTracks

        assertEquals(1, tracks.size)
        assertNull(tracks.first().languageCode)
        assertNull(tracks.first().label)
        assertTrue(tracks.first().isDefault)
    }

    @Test
    fun `subtitle languages are surfaced without fabricated labels`() {
        val subtitles = decodeSingle(MULTI_AUDIO_JSON).subtitleTracks

        assertEquals(listOf("en", "ja"), subtitles.map { it.languageCode })
        assertTrue(subtitles.all { it.label == null && !it.isForced })
    }

    @Test
    fun `missing title falls back to the id rather than a blank string`() {
        assertEquals("abc123", decodeSingle(NO_TITLE_JSON).title)
    }

    @Test
    fun `unknown fields in yt-dlp's real output do not break decoding`() {
        val info = ytDlpJson.decodeFromString(YtDlpInfoJson.serializer(), EXTRA_FIELDS_JSON)

        assertEquals("Still works", info.title)
    }

    // --- Playlist detection ---------------------------------------------------------

    @Test
    fun `a result with entries is detected as a playlist, not a single video`() {
        val extraction = decode(PLAYLIST_JSON)

        assertTrue(extraction is ExtractionResult.Playlist)
    }

    @Test
    fun `a result without entries is detected as a single video`() {
        val extraction = decode(SINGLE_VIDEO_JSON)

        assertTrue(extraction is ExtractionResult.Single)
    }

    @Test
    fun `maps playlist-level title, thumbnail, source, and item count`() {
        val playlist = decodePlaylist(PLAYLIST_JSON)

        assertEquals("My Favorites", playlist.title)
        assertEquals("YoutubePlaylist", playlist.sourceName)
        assertEquals("https://example.com/playlist-thumb.jpg", playlist.thumbnailUrl)
        assertEquals(3, playlist.itemCount)
        assertEquals(PlaylistCollectionType.PLAYLIST, playlist.collectionType)
    }

    @Test
    fun `playlist items preserve order and carry title, thumbnail, duration, url, and id`() {
        val items = decodePlaylist(PLAYLIST_JSON).items

        assertEquals(3, items.size)
        assertEquals(listOf(1, 2, 3), items.map { it.index })
        val first = items[0]
        assertEquals("vid1", first.id)
        assertEquals("First video", first.title)
        assertEquals("https://example.com/vid1-thumb.jpg", first.thumbnailUrl)
        assertEquals(120L, first.durationSeconds)
        assertEquals("https://example.com/watch?v=vid1", first.url)
        assertTrue(first.isAvailable)
    }

    @Test
    fun `falls back to entries size for item count when the source reports none`() {
        val playlist = decodePlaylist(PLAYLIST_NO_COUNT_JSON)

        assertEquals(2, playlist.itemCount)
    }

    @Test
    fun `an empty playlist maps to zero items without failing`() {
        val playlist = decodePlaylist(EMPTY_PLAYLIST_JSON)

        assertTrue(playlist.items.isEmpty())
        assertNull(playlist.itemCount)
        assertEquals("Empty playlist", playlist.title)
    }

    @Test
    fun `mixed unavailable items are flagged, not dropped, and keep their position`() {
        val items = decodePlaylist(MIXED_AVAILABILITY_PLAYLIST_JSON).items

        assertEquals(4, items.size)
        assertEquals(listOf(1, 2, 3, 4), items.map { it.index })

        val available = items[0]
        assertTrue(available.isAvailable)

        val nullEntry = items[1]
        assertTrue(!nullEntry.isAvailable)
        assertEquals("unavailable-2", nullEntry.id)
        assertNull(nullEntry.url)

        val privateByAvailability = items[2]
        assertTrue(!privateByAvailability.isAvailable)
        assertEquals("vid-private", privateByAvailability.id)

        val deletedByTitle = items[3]
        assertTrue(!deletedByTitle.isAvailable)
        assertEquals("[Deleted video]", deletedByTitle.title)
    }

    @Test
    fun `channel extractor key is classified as a channel collection`() {
        val playlist = decodePlaylist(CHANNEL_JSON)

        assertEquals(PlaylistCollectionType.CHANNEL, playlist.collectionType)
    }

    private fun decode(json: String): ExtractionResult =
        ytDlpJson.decodeFromString(YtDlpInfoJson.serializer(), json).toExtractionResult()

    private fun decodeSingle(json: String): MediaAnalysisResult =
        (decode(json) as ExtractionResult.Single).media

    private fun decodePlaylist(json: String): PlaylistAnalysisResult =
        (decode(json) as ExtractionResult.Playlist).playlist

    private companion object {
        val SINGLE_VIDEO_JSON = """
            {
              "id": "abc123",
              "title": "Test video title",
              "duration": 596.7,
              "thumbnail": "https://example.com/thumb-hq.jpg",
              "webpage_url": "https://example.com/watch?v=abc123",
              "extractor": "youtube",
              "extractor_key": "Youtube",
              "formats": [
                {
                  "format_id": "137",
                  "ext": "mp4",
                  "resolution": "1080p",
                  "height": 1080,
                  "fps": 30.0,
                  "vcodec": "avc1",
                  "acodec": "none",
                  "filesize": 884000000
                },
                {
                  "format_id": "18",
                  "ext": "mp4",
                  "height": 360,
                  "fps": 30.0,
                  "vcodec": "avc1",
                  "acodec": "mp4a",
                  "filesize_approx": 25000000
                },
                {
                  "format_id": "140",
                  "ext": "m4a",
                  "vcodec": "none",
                  "acodec": "mp4a"
                }
              ]
            }
        """.trimIndent()

        val MULTI_AUDIO_JSON = """
            {
              "id": "xyz789",
              "title": "Dubbed video",
              "language": "en",
              "formats": [
                { "format_id": "233-en", "ext": "m4a", "vcodec": "none", "acodec": "mp4a", "language": "en" },
                { "format_id": "233-hi", "ext": "m4a", "vcodec": "none", "acodec": "mp4a", "language": "hi" }
              ],
              "subtitles": {
                "en": [ { "url": "https://example.com/en.vtt", "ext": "vtt" } ],
                "ja": [ { "url": "https://example.com/ja.vtt", "ext": "vtt" } ]
              }
            }
        """.trimIndent()

        val NO_TITLE_JSON = """{ "id": "abc123", "formats": [] }"""

        val EXTRA_FIELDS_JSON = """
            {
              "id": "abc123",
              "title": "Still works",
              "some_field_ytdlp_added_later": { "nested": [1, 2, 3] },
              "another_unknown_field": "value",
              "formats": []
            }
        """.trimIndent()

        val PLAYLIST_JSON = """
            {
              "_type": "playlist",
              "id": "PL123",
              "title": "My Favorites",
              "thumbnail": "https://example.com/playlist-thumb.jpg",
              "extractor": "youtube:playlist",
              "extractor_key": "YoutubePlaylist",
              "playlist_count": 3,
              "entries": [
                {
                  "id": "vid1",
                  "title": "First video",
                  "duration": 120.0,
                  "thumbnail": "https://example.com/vid1-thumb.jpg",
                  "webpage_url": "https://example.com/watch?v=vid1"
                },
                {
                  "id": "vid2",
                  "title": "Second video",
                  "duration": 200.0,
                  "webpage_url": "https://example.com/watch?v=vid2"
                },
                {
                  "id": "vid3",
                  "title": "Third video",
                  "duration": 90.0,
                  "webpage_url": "https://example.com/watch?v=vid3"
                }
              ]
            }
        """.trimIndent()

        val PLAYLIST_NO_COUNT_JSON = """
            {
              "_type": "playlist",
              "id": "PL456",
              "title": "No declared count",
              "extractor_key": "YoutubePlaylist",
              "entries": [
                { "id": "a", "title": "A" },
                { "id": "b", "title": "B" }
              ]
            }
        """.trimIndent()

        val EMPTY_PLAYLIST_JSON = """
            {
              "_type": "playlist",
              "id": "PL789",
              "title": "Empty playlist",
              "extractor_key": "YoutubePlaylist",
              "entries": []
            }
        """.trimIndent()

        val MIXED_AVAILABILITY_PLAYLIST_JSON = """
            {
              "_type": "playlist",
              "id": "PLmix",
              "title": "Mixed availability",
              "extractor_key": "YoutubePlaylist",
              "entries": [
                { "id": "vid-ok", "title": "Available video", "webpage_url": "https://example.com/watch?v=vid-ok" },
                null,
                { "id": "vid-private", "title": "Some title", "availability": "private" },
                { "id": "vid-deleted", "title": "[Deleted video]" }
              ]
            }
        """.trimIndent()

        val CHANNEL_JSON = """
            {
              "_type": "playlist",
              "id": "UC123",
              "title": "Some Channel - Videos",
              "extractor": "youtube:tab",
              "extractor_key": "YoutubeChannel",
              "entries": [
                { "id": "vid1", "title": "Upload 1" }
              ]
            }
        """.trimIndent()
    }
}
