package com.mediavault.app.extractor

import com.mediavault.core.common.AppError
import com.mediavault.core.common.AppResult
import com.mediavault.core.domain.extractor.ExtractionEvent
import com.mediavault.core.domain.extractor.ExtractionRequest
import com.mediavault.core.domain.extractor.ExtractionResult
import com.mediavault.core.domain.extractor.ExtractorEngine
import com.mediavault.core.domain.extractor.MediaAnalysisResult
import com.mediavault.core.domain.urlresolution.RedirectResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the URL-resolution layer end to end: normalization, redirect resolution for known
 * share/short links, and re-validation of the resolved host against [SourceRegistry] — using a
 * real [CompositeExtractorEngine] (as production wiring does) backed by a single recording fake,
 * so these tests exercise exactly what a pasted URL turns into by the time a backend sees it.
 */
private fun successResult(id: String = "x") = AppResult.Success<ExtractionResult>(
    ExtractionResult.Single(
        MediaAnalysisResult(
            id = id,
            sourceName = "Test",
            title = "Title",
            durationSeconds = null,
            thumbnailUrl = null,
            webpageUrl = null,
            formats = emptyList(),
            audioTracks = emptyList(),
            subtitleTracks = emptyList(),
        ),
    ),
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class UrlResolvingExtractorEngineTest {

    /** Records every URL it's asked to handle/analyze — always agrees to `canHandle`, standing
     * in for "some backend recognizes this URL" without needing real yt-dlp/Instaloader. */
    private class RecordingBackend : ExtractorEngine {
        override val engineId: String = "fake"
        override val engineVersion: String = "1.0"
        val analyzeCalls = mutableListOf<String>()
        var canHandlePredicate: (String) -> Boolean = { true }

        override suspend fun canHandle(url: String): Boolean = canHandlePredicate(url)

        override suspend fun analyze(url: String, taskId: String): AppResult<ExtractionResult> {
            analyzeCalls.add(url)
            return successResult(url)
        }

        override fun download(request: ExtractionRequest): Flow<ExtractionEvent> = emptyFlow()
        override suspend fun cancel(taskId: String) = Unit
    }

    private class FakeRedirectResolver(private val resultsByUrl: Map<String, AppResult<String>>) : RedirectResolver {
        val calls = mutableListOf<String>()
        override suspend fun resolveFinalUrl(url: String): AppResult<String> {
            calls.add(url)
            return resultsByUrl[url] ?: AppResult.Failure(AppError.Unknown("No fake redirect result configured for $url"))
        }
    }

    private fun engine(
        backend: RecordingBackend = RecordingBackend(),
        redirects: Map<String, AppResult<String>> = emptyMap(),
    ): Triple<UrlResolvingExtractorEngine, RecordingBackend, FakeRedirectResolver> {
        val resolver = FakeRedirectResolver(redirects)
        val composite = CompositeExtractorEngine(setOf(backend))
        return Triple(UrlResolvingExtractorEngine(composite, resolver), backend, resolver)
    }

    // --- Canonical URLs: passed straight through, no redirect resolution --------------------

    @Test
    fun `canonical YouTube URL is analyzed unchanged`() = runTest {
        val (engine, backend, resolver) = engine()

        val result = engine.analyze("https://www.youtube.com/watch?v=dQw4w9WgXcQ", "t1")

        assertTrue(result is AppResult.Success)
        assertEquals(listOf("https://www.youtube.com/watch?v=dQw4w9WgXcQ"), backend.analyzeCalls)
        assertTrue(resolver.calls.isEmpty())
    }

    @Test
    fun `youtube shortened youtu-be URL is analyzed unchanged without redirect resolution`() = runTest {
        val (engine, backend, resolver) = engine()

        val result = engine.analyze("https://youtu.be/dQw4w9WgXcQ?si=abc123", "t1")

        assertTrue(result is AppResult.Success)
        assertEquals(listOf("https://youtu.be/dQw4w9WgXcQ"), backend.analyzeCalls)
        assertTrue(resolver.calls.isEmpty())
    }

    @Test
    fun `canonical Instagram URL is analyzed unchanged`() = runTest {
        val (engine, backend, resolver) = engine()

        engine.analyze("https://www.instagram.com/p/Cabc123/", "t1")

        assertEquals(listOf("https://www.instagram.com/p/Cabc123/"), backend.analyzeCalls)
        assertTrue(resolver.calls.isEmpty())
    }

    @Test
    fun `canonical Facebook URL is analyzed unchanged`() = runTest {
        val (engine, backend, resolver) = engine()

        engine.analyze("https://www.facebook.com/watch/?v=123456", "t1")

        assertEquals(listOf("https://www.facebook.com/watch/?v=123456"), backend.analyzeCalls)
        assertTrue(resolver.calls.isEmpty())
    }

    @Test
    fun `canonical Reddit comments URL is analyzed unchanged`() = runTest {
        val (engine, backend, resolver) = engine()

        engine.analyze("https://www.reddit.com/r/videos/comments/abc123/some_title/", "t1")

        assertEquals(listOf("https://www.reddit.com/r/videos/comments/abc123/some_title/"), backend.analyzeCalls)
        assertTrue(resolver.calls.isEmpty())
    }

    @Test
    fun `canonical X-Twitter URL is analyzed unchanged`() = runTest {
        val (engine, backend, resolver) = engine()

        engine.analyze("https://x.com/user/status/1234567890", "t1")

        assertEquals(listOf("https://x.com/user/status/1234567890"), backend.analyzeCalls)
        assertTrue(resolver.calls.isEmpty())
    }

    @Test
    fun `mobile Twitter URL variant is analyzed unchanged`() = runTest {
        val (engine, backend, resolver) = engine()

        engine.analyze("https://mobile.twitter.com/user/status/1234567890", "t1")

        assertEquals(listOf("https://mobile.twitter.com/user/status/1234567890"), backend.analyzeCalls)
        assertTrue(resolver.calls.isEmpty())
    }

    // --- Share/short links: redirect must be resolved first ---------------------------------

    @Test
    fun `instagram share link is resolved via redirect before being routed`() = runTest {
        val shareUrl = "https://www.instagram.com/share/reel/abc123/"
        val canonical = "https://www.instagram.com/reel/Cxyz987/"
        val (engine, backend, resolver) = engine(redirects = mapOf(shareUrl to AppResult.Success(canonical)))

        val result = engine.analyze(shareUrl, "t1")

        assertTrue(result is AppResult.Success)
        assertEquals(listOf(shareUrl), resolver.calls)
        assertEquals(listOf(canonical), backend.analyzeCalls)
    }

    @Test
    fun `facebook share link is resolved via redirect before being routed`() = runTest {
        // The exact URL reported as failing.
        val shareUrl = "https://www.facebook.com/share/1FSrUatk1W/"
        val canonical = "https://www.facebook.com/watch/?v=9988776655"
        val (engine, backend, resolver) = engine(redirects = mapOf(shareUrl to AppResult.Success(canonical)))

        val result = engine.analyze(shareUrl, "t1")

        assertTrue(result is AppResult.Success)
        assertEquals(listOf(shareUrl), resolver.calls)
        assertEquals(listOf(canonical), backend.analyzeCalls)
    }

    @Test
    fun `reddit s share link is resolved via redirect before being routed`() = runTest {
        // The exact URL reported as failing.
        val shareUrl = "https://www.reddit.com/r/Animey/s/rjrc2HaPXD"
        val canonical = "https://www.reddit.com/r/Animey/comments/1abcxyz/some_title/"
        val (engine, backend, resolver) = engine(redirects = mapOf(shareUrl to AppResult.Success(canonical)))

        val result = engine.analyze(shareUrl, "t1")

        assertTrue(result is AppResult.Success)
        assertEquals(listOf(shareUrl), resolver.calls)
        assertEquals(listOf(canonical), backend.analyzeCalls)
    }

    @Test
    fun `redd-it short URL is resolved via redirect before being routed`() = runTest {
        val shortUrl = "https://redd.it/abc123"
        val canonical = "https://www.reddit.com/r/videos/comments/abc123/some_title/"
        val (engine, backend, resolver) = engine(redirects = mapOf(shortUrl to AppResult.Success(canonical)))

        val result = engine.analyze(shortUrl, "t1")

        assertTrue(result is AppResult.Success)
        assertEquals(listOf(shortUrl), resolver.calls)
        assertEquals(listOf(canonical), backend.analyzeCalls)
    }

    // --- Failure paths: graceful, never a crash ----------------------------------------------

    @Test
    fun `unsupported domain is passed through and fails with the existing friendly error`() = runTest {
        val backend = RecordingBackend().apply { canHandlePredicate = { false } }
        val (engine, _, resolver) = engine(backend = backend)

        val result = engine.analyze("https://example.com/video/123", "t1") as AppResult.Failure

        assertTrue(result.error is AppError.Unsupported)
        assertTrue(resolver.calls.isEmpty())
    }

    @Test
    fun `malformed URL fails immediately without calling the backend or redirect resolver`() = runTest {
        val (engine, backend, resolver) = engine()

        val result = engine.analyze("this is not a url", "t1") as AppResult.Failure

        assertTrue(result.error is AppError.Unsupported)
        assertTrue(backend.analyzeCalls.isEmpty())
        assertTrue(resolver.calls.isEmpty())
    }

    @Test
    fun `redirect landing on an unsupported source is rejected without reaching the backend`() = runTest {
        val shortUrl = "https://redd.it/abc123"
        val (engine, backend, resolver) = engine(redirects = mapOf(shortUrl to AppResult.Success("https://evil.example/phishing")))

        val result = engine.analyze(shortUrl, "t1") as AppResult.Failure

        assertTrue(result.error is AppError.Unsupported)
        assertEquals(listOf(shortUrl), resolver.calls)
        assertTrue("redirect target must never reach the backend", backend.analyzeCalls.isEmpty())
    }

    @Test
    fun `redirect failure is surfaced gracefully without calling the backend`() = runTest {
        val shortUrl = "https://redd.it/abc123"
        val failure = AppResult.Failure(AppError.Network("Couldn't reach the source. Check your connection and try again."))
        val (engine, backend, resolver) = engine(redirects = mapOf(shortUrl to failure))

        val result = engine.analyze(shortUrl, "t1") as AppResult.Failure

        assertTrue(result.error is AppError.Network)
        assertEquals(listOf(shortUrl), resolver.calls)
        assertTrue(backend.analyzeCalls.isEmpty())
    }

    @Test
    fun `redirect timeout is surfaced gracefully without calling the backend`() = runTest {
        val shortUrl = "https://redd.it/abc123"
        val failure = AppResult.Failure(AppError.Timeout("Connection timed out. This source may be unavailable or blocked on your current network."))
        val (engine, backend, resolver) = engine(redirects = mapOf(shortUrl to failure))

        val result = engine.analyze(shortUrl, "t1") as AppResult.Failure

        assertTrue(result.error is AppError.Timeout)
        assertTrue(backend.analyzeCalls.isEmpty())
    }

    // --- canHandle mirrors analyze's routing decision ----------------------------------------

    @Test
    fun `canHandle is false for a malformed URL`() = runTest {
        val (engine, _, _) = engine()
        assertFalse(engine.canHandle("not a url"))
    }

    @Test
    fun `canHandle resolves a share link before delegating`() = runTest {
        val shareUrl = "https://www.facebook.com/share/1FSrUatk1W/"
        val canonical = "https://www.facebook.com/watch/?v=123"
        val (engine, _, resolver) = engine(redirects = mapOf(shareUrl to AppResult.Success(canonical)))

        assertTrue(engine.canHandle(shareUrl))
        assertEquals(listOf(shareUrl), resolver.calls)
    }
}
