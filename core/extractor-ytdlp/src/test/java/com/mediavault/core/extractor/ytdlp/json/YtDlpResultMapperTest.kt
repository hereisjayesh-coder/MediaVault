package com.mediavault.core.extractor.ytdlp.json

import com.mediavault.core.domain.extractor.ExtractionResult
import com.mediavault.core.domain.extractor.MediaAnalysisResult
import com.mediavault.core.domain.extractor.MediaCollectionResult
import com.mediavault.core.domain.extractor.PlaylistAnalysisResult
import com.mediavault.core.domain.extractor.PlaylistCollectionType
import com.mediavault.core.model.MediaType
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
    fun `video and audio-capable formats are exposed as formats, storyboards are not`() {
        val result = decodeSingle(SINGLE_VIDEO_JSON)

        // 137 (video-only), 18 (muxed), 140 (audio-only) — all three are legitimate download
        // choices; only vcodec=none/acodec=none storyboard entries would be excluded.
        assertEquals(3, result.formats.size)

        val hd = result.formats.first { it.formatId == "137" }
        assertEquals("1080p", hd.resolutionLabel)
        assertEquals("mp4", hd.container)
        assertEquals("avc1", hd.videoCodec)
        assertEquals(30, hd.fps)
        assertEquals(884_000_000L, hd.estimatedSizeBytes)
        assertTrue(hd.hasVideo)
        assertTrue(!hd.hasAudio)

        val muxed = result.formats.first { it.formatId == "18" }
        assertTrue(muxed.hasVideo)
        assertTrue(muxed.hasAudio)

        val audioOnly = result.formats.first { it.formatId == "140" }
        assertTrue(!audioOnly.hasVideo)
        assertTrue(audioOnly.hasAudio)
    }

    @Test
    fun `audio bitrate maps from abr, rounded to the nearest kbps`() {
        val result = decodeSingle(SINGLE_VIDEO_JSON)

        val audioOnly = result.formats.first { it.formatId == "140" }
        assertEquals(129, audioOnly.bitrateKbps)

        // The video-only format reports neither abr nor tbr in this fixture.
        val hd = result.formats.first { it.formatId == "137" }
        assertNull(hd.bitrateKbps)
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

    // --- Reddit single-image detection ------------------------------------------------

    @Test
    fun `a result with imageUrl is detected as a collection, not a single video or playlist`() {
        val extraction = decode(REDDIT_IMAGE_JSON)

        assertTrue(extraction is ExtractionResult.Collection)
    }

    @Test
    fun `an imageUrl result maps to a one-item collection with the resolved direct image url`() {
        val collection = decodeCollection(REDDIT_IMAGE_JSON)

        assertEquals("1w0mfi4", collection.id)
        assertEquals("Reddit", collection.sourceName)
        assertEquals("Sunrise on Lake Ontario", collection.title)
        assertEquals("https://www.reddit.com/r/pics/comments/1w0mfi4/sunrise_on_lake_ontario/", collection.webpageUrl)
        assertEquals(1, collection.items.size)

        val item = collection.items.single()
        assertEquals("1w0mfi4_1", item.id)
        assertEquals(1, item.index)
        assertEquals("https://i.redd.it/u6q1zo1jb3mh1.jpeg", item.mediaUrl)
        assertEquals(MediaType.IMAGE, item.mediaType)
        assertTrue(item.isAvailable)
    }

    @Test
    fun `an imageUrl result with no thumbnails falls back to the direct image url as its thumbnail`() {
        val collection = decodeCollection(REDDIT_IMAGE_NO_THUMBNAIL_JSON)

        assertEquals("https://i.redd.it/no-thumb.jpeg", collection.thumbnailUrl)
        assertEquals("https://i.redd.it/no-thumb.jpeg", collection.items.single().thumbnailUrl)
    }

    @Test
    fun `an imageUrl result prefers the highest-preference thumbnail over the direct image url`() {
        val collection = decodeCollection(REDDIT_IMAGE_JSON)

        assertEquals("https://preview.redd.it/best.jpeg", collection.thumbnailUrl)
    }

    private fun decode(json: String): ExtractionResult =
        ytDlpJson.decodeFromString(YtDlpInfoJson.serializer(), json).toExtractionResult()

    private fun decodeSingle(json: String): MediaAnalysisResult =
        (decode(json) as ExtractionResult.Single).media

    private fun decodePlaylist(json: String): PlaylistAnalysisResult =
        (decode(json) as ExtractionResult.Playlist).playlist

    private fun decodeCollection(json: String): MediaCollectionResult =
        (decode(json) as ExtractionResult.Collection).collection

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
                  "acodec": "mp4a",
                  "abr": 128.5
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

        // Shape mediavault_ytdlp.py's `_reddit_image_result` actually produces for a
        // single-image Reddit post (see mediavault_ytdlp.py's own docstring/verification).
        val REDDIT_IMAGE_JSON = """
            {
              "id": "1w0mfi4",
              "title": "Sunrise on Lake Ontario",
              "webpage_url": "https://www.reddit.com/r/pics/comments/1w0mfi4/sunrise_on_lake_ontario/",
              "extractor": "Reddit",
              "extractor_key": "Reddit",
              "thumbnails": [
                { "url": "https://preview.redd.it/worst.jpeg", "preference": -10 },
                { "url": "https://preview.redd.it/best.jpeg", "preference": 5 }
              ],
              "imageUrl": "https://i.redd.it/u6q1zo1jb3mh1.jpeg"
            }
        """.trimIndent()

        val REDDIT_IMAGE_NO_THUMBNAIL_JSON = """
            {
              "id": "abcd123",
              "title": "No thumbnail post",
              "webpage_url": "https://www.reddit.com/r/pics/comments/abcd123/no_thumbnail_post/",
              "extractor": "Reddit",
              "extractor_key": "Reddit",
              "imageUrl": "https://i.redd.it/no-thumb.jpeg"
            }
        """.trimIndent()
    }
}
