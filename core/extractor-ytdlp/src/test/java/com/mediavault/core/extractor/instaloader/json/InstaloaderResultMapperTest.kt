package com.mediavault.core.extractor.instaloader.json

import com.mediavault.core.domain.extractor.ExtractionResult
import com.mediavault.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstaloaderResultMapperTest {

    private fun item(
        index: Int,
        isVideo: Boolean = false,
        thumbnailUrl: String? = "https://cdn.example.com/t$index.jpg",
        imageUrl: String? = "https://cdn.example.com/i$index.jpg",
    ) = InstaloaderItemJson(index = index, isVideo = isVideo, imageUrl = imageUrl, thumbnailUrl = thumbnailUrl)

    private fun post(items: List<InstaloaderItemJson>, id: String = "abc123", title: String = "A caption") = InstaloaderPostJson(
        id = id,
        sourceName = "Instagram",
        title = title,
        thumbnailUrl = "https://cdn.example.com/post-thumb.jpg",
        webpageUrl = "https://www.instagram.com/p/$id/",
        items = items,
    )

    @Test
    fun `a single image post maps to a one-item collection`() {
        val result = post(listOf(item(1))).toMediaCollectionResult()

        assertEquals("abc123", result.id)
        assertEquals("Instagram", result.sourceName)
        assertEquals("A caption", result.title)
        assertEquals("https://www.instagram.com/p/abc123/", result.webpageUrl)
        assertEquals(1, result.items.size)
        val single = result.items.single()
        assertEquals("abc123_1", single.id)
        assertEquals(1, single.index)
        assertEquals(MediaType.IMAGE, single.mediaType)
        assertEquals("https://cdn.example.com/i1.jpg", single.mediaUrl)
        assertTrue(single.isAvailable)
    }

    @Test
    fun `a carousel maps every image item, preserving source order`() {
        val result = post(listOf(item(1), item(2), item(3))).toMediaCollectionResult()

        assertEquals(listOf(1, 2, 3), result.items.map { it.index })
        assertEquals(
            listOf("https://cdn.example.com/i1.jpg", "https://cdn.example.com/i2.jpg", "https://cdn.example.com/i3.jpg"),
            result.items.map { it.mediaUrl },
        )
        assertEquals(listOf("abc123_1", "abc123_2", "abc123_3"), result.items.map { it.id })
    }

    /**
     * Regression test for the real reported defect: a mixed carousel (some image items, some
     * video items, interleaved) previously lost every video item in this exact mapper — a
     * 9-item post with 2 real images and 7 videos in between showed as "2 images". Every item
     * must survive, at its real position, with its real type.
     */
    @Test
    fun `a mixed carousel keeps every item — image and video alike — at its original position and type`() {
        val result = post(
            listOf(
                item(1),
                item(2, isVideo = true),
                item(3, isVideo = true),
                item(4),
                item(5, isVideo = true),
                item(6, isVideo = true),
                item(7, isVideo = true),
                item(8, isVideo = true),
                item(9),
            ),
        ).toMediaCollectionResult()

        assertEquals(9, result.items.size)
        assertEquals((1..9).toList(), result.items.map { it.index })
        assertEquals(
            listOf(
                MediaType.IMAGE, MediaType.VIDEO, MediaType.VIDEO, MediaType.IMAGE, MediaType.VIDEO,
                MediaType.VIDEO, MediaType.VIDEO, MediaType.VIDEO, MediaType.IMAGE,
            ),
            result.items.map { it.mediaType },
        )
        assertTrue(result.items.all { it.isAvailable })
    }

    @Test
    fun `a post whose every item is video maps to a full video collection, not an empty one`() {
        val result = post(listOf(item(1, isVideo = true), item(2, isVideo = true))).toMediaCollectionResult()

        assertEquals(2, result.items.size)
        assertTrue(result.items.all { it.mediaType == MediaType.VIDEO })
    }

    @Test
    fun `an item Instaloader couldn't resolve a URL for is kept as unavailable, not dropped`() {
        val result = post(listOf(item(1), item(2, imageUrl = null), item(3))).toMediaCollectionResult()

        assertEquals(3, result.items.size)
        assertEquals(listOf(true, false, true), result.items.map { it.isAvailable })
        assertNull(result.items[1].mediaUrl)
        assertFalse(result.items[1].isAvailable)
    }

    @Test
    fun `an item thumbnail falls back to its own media URL when the source gave no smaller preview`() {
        val result = post(listOf(item(1, thumbnailUrl = null))).toMediaCollectionResult()

        assertEquals("https://cdn.example.com/i1.jpg", result.items.single().thumbnailUrl)
    }

    @Test
    fun `toExtractionResult wraps the mapped collection as ExtractionResult Collection`() {
        val result = post(listOf(item(1))).toExtractionResult()

        assertTrue(result is ExtractionResult.Collection)
        assertEquals(1, (result as ExtractionResult.Collection).collection.items.size)
    }

    @Test
    fun `a post-level thumbnail is preserved even when null, falling back to the first item's thumbnail`() {
        val bare = InstaloaderPostJson(
            id = "xyz",
            sourceName = "Instagram",
            title = "",
            thumbnailUrl = null,
            webpageUrl = "https://www.instagram.com/p/xyz/",
            items = listOf(item(1)),
        )

        val result = bare.toMediaCollectionResult()

        assertEquals("https://cdn.example.com/t1.jpg", result.thumbnailUrl)
    }

    @Test
    fun `a post with no items at all maps to an empty collection with a null fallback thumbnail`() {
        val bare = InstaloaderPostJson(
            id = "xyz",
            sourceName = "Instagram",
            title = "",
            thumbnailUrl = null,
            webpageUrl = null,
            items = emptyList(),
        )

        val result = bare.toMediaCollectionResult()

        assertTrue(result.items.isEmpty())
        assertNull(result.thumbnailUrl)
    }
}
