package com.mediavault.core.domain.urlresolution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceRegistryTest {

    private fun normalize(url: String): NormalizedUrl = requireNotNull(UrlNormalizer.normalize(url))

    @Test
    fun `canonical YouTube URL is recognized and not a short link`() {
        val url = normalize("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals(SupportedSource.YOUTUBE, SourceRegistry.sourceOf(url))
        assertFalse(SourceRegistry.isShortLink(url))
    }

    @Test
    fun `youtu-be is recognized as YouTube and not treated as a short link needing redirect resolution`() {
        // yt-dlp's own extractor regex already understands youtu.be directly — no redirect
        // resolution should be triggered for it.
        val url = normalize("https://youtu.be/dQw4w9WgXcQ")
        assertEquals(SupportedSource.YOUTUBE, SourceRegistry.sourceOf(url))
        assertFalse(SourceRegistry.isShortLink(url))
    }

    @Test
    fun `canonical Instagram URL is recognized and not a short link`() {
        val url = normalize("https://www.instagram.com/p/Cabc123/")
        assertEquals(SupportedSource.INSTAGRAM, SourceRegistry.sourceOf(url))
        assertFalse(SourceRegistry.isShortLink(url))
    }

    @Test
    fun `instagram share URL is recognized as a short link`() {
        val url = normalize("https://www.instagram.com/share/reel/abc123/")
        assertEquals(SupportedSource.INSTAGRAM, SourceRegistry.sourceOf(url))
        assertTrue(SourceRegistry.isShortLink(url))
    }

    @Test
    fun `canonical Facebook URL is recognized and not a short link`() {
        val url = normalize("https://www.facebook.com/watch/?v=123456")
        assertEquals(SupportedSource.FACEBOOK, SourceRegistry.sourceOf(url))
        assertFalse(SourceRegistry.isShortLink(url))
    }

    @Test
    fun `facebook share URL is recognized as a short link`() {
        val url = normalize("https://www.facebook.com/share/1FSrUatk1W/")
        assertEquals(SupportedSource.FACEBOOK, SourceRegistry.sourceOf(url))
        assertTrue(SourceRegistry.isShortLink(url))
    }

    @Test
    fun `fb-watch host is recognized as Facebook and as a short link`() {
        val url = normalize("https://fb.watch/abc123/")
        assertEquals(SupportedSource.FACEBOOK, SourceRegistry.sourceOf(url))
        assertTrue(SourceRegistry.isShortLink(url))
    }

    @Test
    fun `canonical Reddit comments URL is recognized and not a short link`() {
        val url = normalize("https://www.reddit.com/r/videos/comments/abc123/some_title/")
        assertEquals(SupportedSource.REDDIT, SourceRegistry.sourceOf(url))
        assertFalse(SourceRegistry.isShortLink(url))
    }

    @Test
    fun `reddit s share URL is recognized as a short link`() {
        val url = normalize("https://www.reddit.com/r/Animey/s/rjrc2HaPXD")
        assertEquals(SupportedSource.REDDIT, SourceRegistry.sourceOf(url))
        assertTrue(SourceRegistry.isShortLink(url))
    }

    @Test
    fun `redd-it host is recognized as Reddit and as a short link`() {
        val url = normalize("https://redd.it/abc123")
        assertEquals(SupportedSource.REDDIT, SourceRegistry.sourceOf(url))
        assertTrue(SourceRegistry.isShortLink(url))
    }

    @Test
    fun `canonical X-Twitter URL is recognized and not a short link`() {
        val url = normalize("https://x.com/user/status/1234567890")
        assertEquals(SupportedSource.TWITTER, SourceRegistry.sourceOf(url))
        assertFalse(SourceRegistry.isShortLink(url))
    }

    @Test
    fun `legacy twitter-com host is recognized as the same source as x-com`() {
        val url = normalize("https://twitter.com/user/status/1234567890")
        assertEquals(SupportedSource.TWITTER, SourceRegistry.sourceOf(url))
    }

    @Test
    fun `mobile twitter subdomain is recognized`() {
        val url = normalize("https://mobile.twitter.com/user/status/1234567890")
        assertEquals(SupportedSource.TWITTER, SourceRegistry.sourceOf(url))
    }

    @Test
    fun `unsupported domain is recognized by no source and is not a short link`() {
        val url = normalize("https://example.com/video/123")
        assertNull(SourceRegistry.sourceOf(url))
        assertFalse(SourceRegistry.isShortLink(url))
    }

    @Test
    fun `share-shaped path on an unrelated host is not treated as a short link`() {
        // Guards against the path-pattern check ever firing independently of a host match.
        val url = normalize("https://example.com/share/abc123")
        assertNull(SourceRegistry.sourceOf(url))
        assertFalse(SourceRegistry.isShortLink(url))
    }
}
