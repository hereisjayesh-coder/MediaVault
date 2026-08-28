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
    fun `non-timeout network failure message maps to Network`() {
        val error = PyException(
            "urllib.error.URLError: <urlopen error [Errno 111] Failed to establish a new connection>",
        ).toAppError()

        assertTrue(error is AppError.Network)
    }

    @Test
    fun `timeout message maps to Timeout, not the generic Network error`() {
        val error = PyException(
            "urllib.error.URLError: <urlopen error [Errno 110] Connection timed out>",
        ).toAppError()

        assertTrue(error is AppError.Timeout)
    }

    @Test
    fun `timeout message produces the exact expected user-facing text`() {
        val error = PyException(
            "ERROR: [PornHub] 6a82805275b6f: Unable to download webpage: timed out (caused by TransportError('timed out'))",
        ).toAppError()

        assertTrue(error is AppError.Timeout)
        assertEquals(
            "Connection timed out. This source may be unavailable or blocked on your current network.",
            error.message,
        )
    }

    @Test
    fun `unavailable video message maps to Unsupported with a clear reason`() {
        val error = PyException("ERROR: [youtube] abc123: Private video. Sign in if you've been granted access")
            .toAppError()

        assertTrue(error is AppError.Unsupported)
    }

    @Test
    fun `image-only post message maps to Unsupported with a clear, non-raw reason`() {
        // The exact message yt-dlp's Instagram extractor raises for a single-image post —
        // confirmed live against a real public Instagram photo post during QA.
        val error = PyException("ERROR: [Instagram] Db9IVmrDvQ4: There is no video in this post")
            .toAppError()

        assertTrue(error is AppError.Unsupported)
        assertEquals("This post doesn't contain a video MediaVault can download.", error.message)
    }

    @Test
    fun `an image-only tweet's distinct wording maps to the same clear, non-raw reason`() {
        // Twitter/X's own wording for this same scenario — confirmed live against a real,
        // public, image-only tweet (yt-dlp's own maintained test fixture for the case).
        val error = PyException("ERROR: [twitter] 657991469417025536: No video could be found in this tweet")
            .toAppError()

        assertTrue(error is AppError.Unsupported)
        assertEquals("This post doesn't contain a video MediaVault can download.", error.message)
    }

    @Test
    fun `a login-required message from any extractor maps to Unsupported, never leaking the raw --cookies CLI hint`() {
        // The exact shape yt-dlp's shared raise_login_required() helper produces for a real
        // public Vimeo video — confirmed live: Vimeo's default web API currently requires a
        // login for every video, not just this one, per common.py's shared login-hint text.
        val error = PyException(
            "ERROR: [vimeo] 393756517: The web client only works when logged-in. Use --cookies, " +
                "--cookies-from-browser, --username and --password, --netrc-cmd, or --netrc (vimeo) to " +
                "provide account credentials. See  https://github.com/yt-dlp/yt-dlp/wiki/FAQ  for how to " +
                "manually pass cookies",
        ).toAppError()

        assertTrue(error is AppError.Unsupported)
        assertEquals(
            "This content requires logging into the source — MediaVault only downloads public content.",
            error.message,
        )
    }

    @Test
    fun `a protected-tweet login message also maps to the same clean login-required reason`() {
        val error = PyException(
            "ERROR: [twitter] 123: You are not authorized to view this protected tweet. Use --cookies...",
        ).toAppError()

        assertTrue(error is AppError.Unsupported)
        assertEquals(
            "This content requires logging into the source — MediaVault only downloads public content.",
            error.message,
        )
    }

    @Test
    fun `yt-dlp's own ALL-CAPS ERROR log prefix is stripped, not just the lowercase Error class-name prefix`() {
        // Regression guard: DownloadError's own __str__ formats as "ERROR: [extractor] id: msg"
        // (all-caps) — case-sensitively distinct from "SomeClassError: msg", which the
        // pre-existing "Error: " substring match alone does not catch, previously leaking the
        // entire raw string (including any CLI-only hints) for any unrecognized error.
        val error = PyException(
            "ERROR: [TikTok] 7206382937372134662: Unexpected response from webpage request; please " +
                "report this issue on  https://github.com/yt-dlp/yt-dlp/issues",
        ).toAppError()

        assertTrue(error is AppError.Unknown)
        assertEquals(
            "Unexpected response from webpage request; please report this issue on  " +
                "https://github.com/yt-dlp/yt-dlp/issues",
            error.message,
        )
    }

    @Test
    fun `reddit gallery rejection message maps to Unsupported with a clear, non-raw reason`() {
        // The exact ValueError mediavault_ytdlp.py's own `_reddit_image_result` raises for a
        // multi-image Reddit gallery post — see its module-level docstring for why this is
        // rejected outright rather than attempted.
        val error = PyException(
            "ValueError: This is a multi-image Reddit gallery post — MediaVault can only " +
                "download single-image Reddit posts today.",
        ).toAppError()

        assertTrue(error is AppError.Unsupported)
        assertEquals(
            "This is a multi-image Reddit gallery post — MediaVault can only download single-image Reddit posts today.",
            error.message,
        )
    }

    @Test
    fun `unrecognized message falls back to a cleaned Unknown error`() {
        val error = PyException("yt_dlp.utils.ExtractorError: Something entirely new broke").toAppError()

        assertTrue(error is AppError.Unknown)
        assertEquals("Something entirely new broke", error.message)
    }
}
