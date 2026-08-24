package com.mediavault.core.extractor.ytdlp

import com.chaquo.python.PyException
import com.mediavault.core.common.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpErrorMapperTest {

    @Test
    fun `unsupported URL message maps to Unsupported`() {
        val error = PyException("yt_dlp.utils.UnsupportedError: Unsupported URL: https://example.com/x")
            .toAppError()

        assertTrue(error is AppError.Unsupported)
    }

    @Test
    fun `network failure message maps to Network`() {
        val error = PyException(
            "urllib.error.URLError: <urlopen error [Errno 110] Connection timed out>",
        ).toAppError()

        assertTrue(error is AppError.Network)
    }

    @Test
    fun `unavailable video message maps to Unsupported with a clear reason`() {
        val error = PyException("ERROR: [youtube] abc123: Private video. Sign in if you've been granted access")
            .toAppError()

        assertTrue(error is AppError.Unsupported)
    }

    @Test
    fun `unrecognized message falls back to a cleaned Unknown error`() {
        val error = PyException("yt_dlp.utils.ExtractorError: Something entirely new broke").toAppError()

        assertTrue(error is AppError.Unknown)
        assertEquals("Something entirely new broke", error.message)
    }
}
