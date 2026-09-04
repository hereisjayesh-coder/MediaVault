package com.mediavault.core.domain.urlresolution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlNormalizerTest {

    @Test
    fun `canonical YouTube URL is preserved`() {
        val result = UrlNormalizer.normalize("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", result?.toUrlString())
    }

    @Test
    fun `YouTube share URL with tracking param strips only the tracking param`() {
        val result = UrlNormalizer.normalize("https://youtu.be/dQw4w9WgXcQ?si=abc123")
        assertEquals("https://youtu.be/dQw4w9WgXcQ", result?.toUrlString())
    }

    @Test
    fun `scheme and host casing are normalized to lowercase`() {
        val result = UrlNormalizer.normalize("HTTPS://WWW.YouTube.COM/watch?v=dQw4w9WgXcQ")
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", result?.toUrlString())
    }

    @Test
    fun `missing scheme defaults to https`() {
        val result = UrlNormalizer.normalize("www.reddit.com/r/videos/comments/abc/title/")
        assertEquals("https://www.reddit.com/r/videos/comments/abc/title/", result?.toUrlString())
    }

    @Test
    fun `facebook share URL tracking params are stripped but path is preserved`() {
        val result = UrlNormalizer.normalize("https://www.facebook.com/share/1FSrUatk1W/?mibextid=abc123")
        assertEquals("https://www.facebook.com/share/1FSrUatk1W/", result?.toUrlString())
    }

    @Test
    fun `reddit s share URL round-trips unchanged`() {
        val result = UrlNormalizer.normalize("https://www.reddit.com/r/Animey/s/rjrc2HaPXD")
        assertEquals("https://www.reddit.com/r/Animey/s/rjrc2HaPXD", result?.toUrlString())
    }

    @Test
    fun `content-identifying query parameters are never stripped`() {
        val result = UrlNormalizer.normalize("https://www.youtube.com/watch?v=abc&list=PL123&t=42&utm_source=share")
        val query = result?.query.orEmpty().toMap()
        assertEquals("abc", query["v"])
        assertEquals("PL123", query["list"])
        assertEquals("42", query["t"])
        assertEquals(false, query.containsKey("utm_source"))
    }

    @Test
    fun `blank input is malformed`() {
        assertNull(UrlNormalizer.normalize("   "))
    }

    @Test
    fun `garbage text is malformed`() {
        assertNull(UrlNormalizer.normalize("this is not a url"))
    }

    @Test
    fun `non-http scheme is rejected`() {
        assertNull(UrlNormalizer.normalize("javascript:alert(1)"))
        assertNull(UrlNormalizer.normalize("intent://evil#Intent;scheme=https;end"))
        assertNull(UrlNormalizer.normalize("ftp://example.com/file"))
    }

    @Test
    fun `url with no host is malformed`() {
        assertNull(UrlNormalizer.normalize("https:///path"))
    }

    @Test
    fun `mobile subdomain host is preserved`() {
        val result = UrlNormalizer.normalize("https://m.facebook.com/watch/?v=123")
        assertEquals("m.facebook.com", result?.host)
    }
}
