package com.mediavault.core.extractor.instaloader.json

import com.mediavault.core.domain.extractor.ExtractionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstaloaderResultMapperTest {

    private fun item(index: Int, isVideo: Boolean = false, thumbnailUrl: String? = "https://cdn.example.com/t$index.jpg") =
        InstaloaderItemJson(index = index, isVideo = isVideo, imageUrl = "https://cdn.example.com/i$index.jpg", thumbnailUrl = thumbnailUrl)

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
        assertEquals("https://cdn.example.com/i1.jpg", single.imageUrl)
    }

    @Test
    fun `a carousel maps every image item, preserving source order`() {
        val result = post(listOf(item(1), item(2), item(3))).toMediaCollectionResult()

        assertEquals(listOf(1, 2, 3), result.items.map { it.index })
        assertEquals(
            listOf("https://cdn.example.com/i1.jpg", "https://cdn.example.com/i2.jpg", "https://cdn.example.com/i3.jpg"),
            result.items.map { it.imageUrl },
        )
        assertEquals(listOf("abc123_1", "abc123_2", "abc123_3"), result.items.map { it.id })
    }

    @Test
    fun `a video item mixed into a carousel is dropped, but surviving items keep their original index`() {
        // A 4-item post where item 2 is a video clip — only images 1, 3, 4 belong in a
        // Collection; the survivors must still report their real position in the *post*
        // (matters for `download()`'s format_id, which indexes into the post's own node list,
        // not a renumbered image-only list).
        val result = post(listOf(item(1), item(2, isVideo = true), item(3), item(4))).toMediaCollectionResult()

        assertEquals(listOf(1, 3, 4), result.items.map { it.index })
    }

    @Test
    fun `a post whose every item is video maps to an empty collection, never a fabricated image row`() {
        val result = post(listOf(item(1, isVideo = true), item(2, isVideo = true))).toMediaCollectionResult()

        assertTrue(result.items.isEmpty())
    }

    @Test
    fun `an item thumbnail falls back to its own image URL when the source gave no smaller preview`() {
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
