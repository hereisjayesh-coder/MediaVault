package com.mediavault.core.extractor.ytdlp

import androidx.test.platform.app.InstrumentationRegistry
import com.mediavault.core.common.AppResult
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real integration test: starts yt-dlp inside Chaquopy on-device and analyzes a public,
 * long-stable test video over the network. This is intentionally not mocked — it is the one
 * place that proves the whole Kotlin -> Chaquopy -> Python -> yt-dlp -> JSON path actually
 * works together. Requires a network connection on the test device/emulator.
 */
class YtDlpExtractorEngineInstrumentedTest {

    @Test
    fun analyzesAPublicTestVideo() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = YtDlpExtractorEngine(context)

        val result = withTimeout(TIMEOUT_MS) {
            engine.analyze(TEST_VIDEO_URL, UUID.randomUUID().toString())
        }

        assertTrue("Expected a successful analysis, got $result", result is AppResult.Success)
        val data = (result as AppResult.Success).data
        assertFalse("Title should not be blank", data.title.isBlank())
        assertTrue("Expected at least one video format", data.formats.isNotEmpty())
        assertTrue("Every format should report a video codec", data.formats.all { it.hasVideo })
    }

    @Test
    fun canHandleRecognizesAKnownSourceAndRejectsGarbage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = YtDlpExtractorEngine(context)

        assertTrue(withTimeout(TIMEOUT_MS) { engine.canHandle(TEST_VIDEO_URL) })
        assertFalse(withTimeout(TIMEOUT_MS) { engine.canHandle("not a url at all") })
    }

    private companion object {
        // "Me at the zoo" — the first video ever uploaded to YouTube. Permanently public
        // and extremely unlikely to be removed, making it a stable choice for this test.
        const val TEST_VIDEO_URL = "https://www.youtube.com/watch?v=jNQXAC9IVRw"
        const val TIMEOUT_MS = 60_000L
    }
}
