package com.mediavault.app.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the pure, network-free logic [HttpRedirectResolver] relies on — canonical-URL
 * extraction from an HTML body, and safety validation of a redirect target. The redirect-walk
 * itself (HEAD-then-GET sequencing, actual HTTP round trips) is exercised live against real
 * Facebook/Reddit share links rather than a local fake server, since [isSafeRedirectTarget]
 * deliberately rejects loopback addresses — exactly what a local test server would resolve to.
 */
class HttpRedirectResolverTest {

    private val resolver = HttpRedirectResolver()

    // --- extractCanonicalUrl: the mechanism a Facebook /share/... link needs, confirmed live --

    @Test
    fun `extracts canonical URL from a link rel canonical tag`() {
        val html = """<html><head><link rel="canonical" href="https://www.facebook.com/user/posts/123456/"></head></html>"""
        assertEquals("https://www.facebook.com/user/posts/123456/", resolver.extractCanonicalUrl(html))
    }

    @Test
    fun `extracts canonical URL from an og-url meta tag when no canonical link is present`() {
        val html = """<html><head><meta property="og:url" content="https://www.instagram.com/reel/Cxyz987/"></head></html>"""
        assertEquals("https://www.instagram.com/reel/Cxyz987/", resolver.extractCanonicalUrl(html))
    }

    @Test
    fun `canonical link tag is preferred over an og-url meta tag when both are present`() {
        val html = """
            <meta property="og:url" content="https://www.facebook.com/share/1FSrUatk1W/">
            <link rel="canonical" href="https://www.facebook.com/user/posts/123456/">
        """.trimIndent()
        assertEquals("https://www.facebook.com/user/posts/123456/", resolver.extractCanonicalUrl(html))
    }

    @Test
    fun `attribute order within the tag does not matter`() {
        val html = """<link href="https://www.facebook.com/user/posts/123456/" rel="canonical">"""
        assertEquals("https://www.facebook.com/user/posts/123456/", resolver.extractCanonicalUrl(html))
    }

    @Test
    fun `html entity encoded ampersands in the URL are decoded`() {
        val html = """<link rel="canonical" href="https://www.reddit.com/r/x/comments/abc/title/?a=1&amp;b=2">"""
        assertEquals("https://www.reddit.com/r/x/comments/abc/title/?a=1&b=2", resolver.extractCanonicalUrl(html))
    }

    @Test
    fun `returns null when no canonical or og-url tag is present`() {
        val html = """<html><head><title>No canonical here</title></head></html>"""
        assertNull(resolver.extractCanonicalUrl(html))
    }

    // --- isSafeRedirectTarget: the SSRF/anti-spoofing guard applied to every redirect hop -----

    @Test
    fun `http and https redirect targets are safe`() {
        assertTrue(resolver.isSafeRedirectTarget("https://www.facebook.com/user/posts/123456/"))
        assertTrue(resolver.isSafeRedirectTarget("http://www.reddit.com/r/x/comments/abc/"))
    }

    @Test
    fun `non-http schemes are rejected`() {
        assertFalse(resolver.isSafeRedirectTarget("javascript:alert(1)"))
        assertFalse(resolver.isSafeRedirectTarget("intent://evil#Intent;scheme=https;end"))
        assertFalse(resolver.isSafeRedirectTarget("ftp://example.com/file"))
    }

    @Test
    fun `loopback redirect targets are rejected`() {
        assertFalse(resolver.isSafeRedirectTarget("http://127.0.0.1:8080/internal"))
        assertFalse(resolver.isSafeRedirectTarget("http://localhost/internal"))
    }

    @Test
    fun `malformed redirect target is rejected`() {
        assertFalse(resolver.isSafeRedirectTarget("not a url"))
    }
}
