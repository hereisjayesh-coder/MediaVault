package com.mediavault.app.extractor

import com.mediavault.core.common.AppError
import com.mediavault.core.common.AppResult
import com.mediavault.core.domain.extractor.ExtractionEvent
import com.mediavault.core.domain.extractor.ExtractionRequest
import com.mediavault.core.domain.extractor.ExtractionResult
import com.mediavault.core.domain.extractor.ExtractorEngine
import com.mediavault.core.domain.extractor.MediaAnalysisResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers extractor-selection/routing logic only — real backend behavior (yt-dlp, Instaloader)
 * is covered by their own modules' tests. Uses small local fakes rather than
 * `com.mediavault.app.ui.screens.home.FakeExtractorEngine` (that one always `canHandle`s
 * everything and only stands in for a single backend — not useful for exercising multi-backend
 * routing/fallback).
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CompositeExtractorEngineTest {

    private fun singleResult(id: String = "x") = AppResult.Success<ExtractionResult>(
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

    private class FakeEngine(
        override val engineId: String,
        private val canHandlePredicate: (String) -> Boolean = { true },
        private val analyzeResult: AppResult<ExtractionResult>,
    ) : ExtractorEngine {
        override val engineVersion: String = "1.0"
        val analyzeCalls = mutableListOf<String>()
        val downloadedRequests = mutableListOf<ExtractionRequest>()
        val cancelledTaskIds = mutableListOf<String>()

        override suspend fun canHandle(url: String): Boolean = canHandlePredicate(url)

        override suspend fun analyze(url: String, taskId: String): AppResult<ExtractionResult> {
            analyzeCalls.add(url)
            return analyzeResult
        }

        override fun download(request: ExtractionRequest): Flow<ExtractionEvent> {
            downloadedRequests.add(request)
            return flowOf(ExtractionEvent.Completed(request.taskId, "output/path"))
        }

        override suspend fun cancel(taskId: String) {
            cancelledTaskIds.add(taskId)
        }
    }

    private fun request(url: String, taskId: String = "task-1", preferredEngineId: String? = null) = ExtractionRequest(
        taskId = taskId,
        sourceUrl = url,
        formatId = "1",
        destinationPath = "/tmp/out",
        preferredEngineId = preferredEngineId,
    )

    @Test
    fun `canHandle is true when any backend recognizes the URL`() = runTest {
        val ytdlp = FakeEngine("ytdlp", canHandlePredicate = { false }, analyzeResult = singleResult())
        val instaloader = FakeEngine("instaloader", canHandlePredicate = { true }, analyzeResult = singleResult())
        val composite = CompositeExtractorEngine(setOf(ytdlp, instaloader))

        assertTrue(composite.canHandle("https://instagram.com/p/x/"))
    }

    @Test
    fun `canHandle is false when no backend recognizes the URL`() = runTest {
        val ytdlp = FakeEngine("ytdlp", canHandlePredicate = { false }, analyzeResult = singleResult())
        val composite = CompositeExtractorEngine(setOf(ytdlp))

        assertTrue(!composite.canHandle("https://unknown-host.example/x"))
    }

    @Test
    fun `analyze uses the primary backend when it succeeds, never trying the fallback`() = runTest {
        val ytdlp = FakeEngine("ytdlp", analyzeResult = singleResult("from-ytdlp"))
        val instaloader = FakeEngine("instaloader", analyzeResult = singleResult("from-instaloader"))
        val composite = CompositeExtractorEngine(setOf(instaloader, ytdlp)) // deliberately out-of-priority-order in the set

        val result = composite.analyze("https://instagram.com/p/x/", "task-1") as AppResult.Success
        val media = (result.data as ExtractionResult.Single).media

        assertEquals("from-ytdlp", media.id)
        assertEquals(1, ytdlp.analyzeCalls.size)
        assertEquals(0, instaloader.analyzeCalls.size)
    }

    @Test
    fun `analyze falls back to the next candidate when the primary backend fails`() = runTest {
        val ytdlpFailure = AppResult.Failure(AppError.Unsupported("This post doesn't contain a video MediaVault can download."))
        val ytdlp = FakeEngine("ytdlp", analyzeResult = ytdlpFailure)
        val instaloader = FakeEngine("instaloader", analyzeResult = singleResult("from-instaloader"))
        val composite = CompositeExtractorEngine(setOf(ytdlp, instaloader))

        val result = composite.analyze("https://instagram.com/p/x/", "task-1") as AppResult.Success
        val media = (result.data as ExtractionResult.Single).media

        assertEquals("from-instaloader", media.id)
        assertEquals(1, ytdlp.analyzeCalls.size)
        assertEquals(1, instaloader.analyzeCalls.size)
    }

    @Test
    fun `analyze surfaces the last backend's failure when every candidate fails`() = runTest {
        val ytdlp = FakeEngine("ytdlp", analyzeResult = AppResult.Failure(AppError.Unsupported("yt-dlp reason")))
        val instaloader = FakeEngine("instaloader", analyzeResult = AppResult.Failure(AppError.Unsupported("instaloader reason")))
        val composite = CompositeExtractorEngine(setOf(ytdlp, instaloader))

        val result = composite.analyze("https://instagram.com/p/x/", "task-1") as AppResult.Failure

        assertEquals("instaloader reason", result.error.message)
    }

    @Test
    fun `analyze on a URL no backend recognizes fails clearly without calling any backend`() = runTest {
        val ytdlp = FakeEngine("ytdlp", canHandlePredicate = { false }, analyzeResult = singleResult())
        val composite = CompositeExtractorEngine(setOf(ytdlp))

        val result = composite.analyze("https://unknown-host.example/x", "task-1") as AppResult.Failure

        assertTrue(result.error is AppError.Unsupported)
        assertTrue(ytdlp.analyzeCalls.isEmpty())
    }

    @Test
    fun `download is routed to whichever backend most recently resolved analyze for that URL`() = runTest {
        val ytdlpFailure = AppResult.Failure(AppError.Unsupported("no video"))
        val ytdlp = FakeEngine("ytdlp", analyzeResult = ytdlpFailure)
        val instaloader = FakeEngine("instaloader", analyzeResult = singleResult())
        val composite = CompositeExtractorEngine(setOf(ytdlp, instaloader))
        composite.analyze("https://instagram.com/p/x/", "task-1")

        composite.download(request("https://instagram.com/p/x/")).toList()

        assertEquals(1, instaloader.downloadedRequests.size)
        assertTrue(ytdlp.downloadedRequests.isEmpty())
    }

    @Test
    fun `download honors an explicit preferredEngineId hint even with no prior analyze in this process`() = runTest {
        // Simulates process-death recovery: a fresh CompositeExtractorEngine instance has no
        // memory of which backend resolved this URL, but the persisted task still knows.
        val ytdlp = FakeEngine("ytdlp", analyzeResult = singleResult())
        val instaloader = FakeEngine("instaloader", analyzeResult = singleResult())
        val composite = CompositeExtractorEngine(setOf(ytdlp, instaloader))

        composite.download(request("https://instagram.com/p/x/", preferredEngineId = "instaloader")).toList()

        assertEquals(1, instaloader.downloadedRequests.size)
        assertTrue(ytdlp.downloadedRequests.isEmpty())
    }

    @Test
    fun `download falls back to the first backend that recognizes the URL when there is no memory or hint`() = runTest {
        val ytdlp = FakeEngine("ytdlp", analyzeResult = singleResult())
        val instaloader = FakeEngine("instaloader", analyzeResult = singleResult())
        val composite = CompositeExtractorEngine(setOf(instaloader, ytdlp))

        composite.download(request("https://youtube.com/watch?v=x")).toList()

        assertEquals(1, ytdlp.downloadedRequests.size)
        assertTrue(instaloader.downloadedRequests.isEmpty())
    }

    @Test
    fun `cancel broadcasts to every backend, since each treats an unknown taskId as a safe no-op`() = runTest {
        val ytdlp = FakeEngine("ytdlp", analyzeResult = singleResult())
        val instaloader = FakeEngine("instaloader", analyzeResult = singleResult())
        val composite = CompositeExtractorEngine(setOf(ytdlp, instaloader))

        composite.cancel("task-1")

        assertEquals(listOf("task-1"), ytdlp.cancelledTaskIds)
        assertEquals(listOf("task-1"), instaloader.cancelledTaskIds)
    }

    @Test
    fun `an unrecognized engineId falls to the end of priority order, after every known backend`() = runTest {
        val ytdlp = FakeEngine("ytdlp", analyzeResult = AppResult.Failure(AppError.Unsupported("ytdlp failed")))
        val instaloader = FakeEngine("instaloader", analyzeResult = AppResult.Failure(AppError.Unsupported("instaloader failed")))
        val thirdParty = FakeEngine("some-future-backend", analyzeResult = singleResult("from-third-party"))
        val composite = CompositeExtractorEngine(setOf(thirdParty, instaloader, ytdlp))

        val result = composite.analyze("https://example.com/x", "task-1") as AppResult.Success
        val media = (result.data as ExtractionResult.Single).media

        // yt-dlp and Instaloader are both tried (and fail) before the unranked third backend
        // gets a turn — confirms priority ordering, not just "first in the set".
        assertEquals(1, ytdlp.analyzeCalls.size)
        assertEquals(1, instaloader.analyzeCalls.size)
        assertEquals("from-third-party", media.id)
    }
}
