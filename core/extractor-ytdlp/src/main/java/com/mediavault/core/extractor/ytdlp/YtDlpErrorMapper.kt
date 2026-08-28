package com.mediavault.core.extractor.ytdlp

import com.chaquo.python.PyException
import com.mediavault.core.common.AppError

/**
 * Best-effort mapping from yt-dlp's Python-side exception text to a clean [AppError].
 * yt-dlp has hundreds of extractors and error paths, so this recognizes the common cases
 * and falls back to a cleaned-up version of the raw message rather than guessing.
 */
internal fun PyException.toAppError(): AppError {
    val raw = message.orEmpty()
    return when {
        raw.contains("Unsupported URL", ignoreCase = true) ->
            AppError.Unsupported("This link isn't from a source MediaVault's extractor recognizes yet.")

        raw.contains("timed out", ignoreCase = true) ->
            AppError.Timeout(
                "Connection timed out. This source may be unavailable or blocked on your current network.",
                this,
            )

        raw.contains("Unable to download webpage", ignoreCase = true) ||
            raw.contains("urlopen error", ignoreCase = true) ||
            raw.contains("Failed to establish a new connection", ignoreCase = true) ||
            raw.contains("Network is unreachable", ignoreCase = true) ->
            AppError.Network("Couldn't reach the source. Check your connection and try again.", this)

        raw.contains("Video unavailable", ignoreCase = true) ||
            raw.contains("Private video", ignoreCase = true) ||
            raw.contains("This video is not available", ignoreCase = true) ||
            raw.contains("age-restricted", ignoreCase = true) ||
            raw.contains("Sign in to confirm", ignoreCase = true) ->
            AppError.Unsupported("This content isn't available (removed, private, restricted, or region-locked).")

        // yt-dlp's shared `raise_login_required()` helper (extractor/common.py) is what every
        // extractor — Vimeo, Twitter/X protected or NSFW-gated tweets, Facebook login-walled
        // posts, and more — calls for this exact scenario, always appending a `--cookies`/
        // `--username and --password`/"for the authentication" CLI hint that means nothing to a
        // MediaVault user (confirmed live: a real public Vimeo video currently fails this way).
        // One shared match here covers every such extractor, matching this file's own "reuse
        // common error mapping" convention, rather than a per-platform login-message branch.
        raw.contains("provide account credentials", ignoreCase = true) ||
            raw.contains("Use --cookies", ignoreCase = true) ||
            raw.contains("for the authentication", ignoreCase = true) ||
            raw.contains("only works when logged-in", ignoreCase = true) ||
            raw.contains("protected tweet", ignoreCase = true) ||
            raw.contains("requires authentication", ignoreCase = true) ||
            raw.contains("login required", ignoreCase = true) ->
            AppError.Unsupported("This content requires logging into the source — MediaVault only downloads public content.")

        raw.contains("No information could be extracted", ignoreCase = true) ->
            AppError.Unsupported("Nothing playable was found at this URL.")

        // yt-dlp's own extractors raise a variant of this for an image-only post on a
        // video-first platform — Instagram's own wording ("There is no video in this post",
        // confirmed live) and Twitter/X's own distinct wording for an image-only tweet
        // ("No video could be found in this tweet", confirmed live via a real 2015 photo
        // tweet) are both upstream extraction limitations, not a MediaVault defect, so both
        // get the same clear, non-raw wording rather than yt-dlp's own platform-specific text.
        raw.contains("There is no video in this post", ignoreCase = true) ||
            raw.contains("No video could be found in this tweet", ignoreCase = true) ->
            AppError.Unsupported("This post doesn't contain a video MediaVault can download.")

        // Raised deliberately by mediavault_ytdlp.py's own Reddit-image fast path, not by
        // yt-dlp itself — yt-dlp's Reddit extractor has no reliable multi-image/gallery
        // support (confirmed: attempting one through its normal pipeline can hang for over a
        // minute against an endpoint it doesn't actually resolve), so this is refused
        // immediately and clearly instead of hanging or silently downloading only one image.
        raw.contains("multi-image Reddit gallery", ignoreCase = true) ->
            AppError.Unsupported("This is a multi-image Reddit gallery post — MediaVault can only download single-image Reddit posts today.")

        raw.contains("No space left on device", ignoreCase = true) ->
            AppError.Storage("Not enough storage space to finish this download.")

        raw.contains("Permission denied", ignoreCase = true) ->
            AppError.Permission("MediaVault doesn't have permission to write to the selected location.")

        raw.contains("Requested format is not available", ignoreCase = true) ->
            AppError.Unsupported("That quality is no longer available for this item.")

        raw.contains("HTTP Error 403", ignoreCase = true) ||
            raw.contains("HTTP Error 404", ignoreCase = true) ||
            raw.contains("fragment", ignoreCase = true) ->
            AppError.Source("The source rejected or removed this download partway through.", this)

        else -> AppError.Unknown(cleanExtractorMessage(raw), this)
    }
}

/**
 * Strips yt-dlp's noisy `ERROR: [extractor] id: ` / Python traceback prefixes down to the
 * useful part. Two distinct prefix shapes exist and both must be handled: a bare Python
 * exception's `str()` (e.g. `"yt_dlp.utils.ExtractorError: message"`, cleaned by the
 * `"Error: "` substring match) and yt-dlp's own CLI-log-formatted `DownloadError.__str__()`
 * (e.g. `"ERROR: [vimeo] 123: message"` — confirmed live; note the all-caps "ERROR", which
 * the case-sensitive `"Error: "` match alone does *not* catch, previously leaking the full
 * raw string — including embedded `--cookies`/`-U` CLI hints — for any error that didn't
 * match a specific branch above).
 */
private fun cleanExtractorMessage(raw: String): String {
    val withoutLogPrefix = raw.replaceFirst(YTDLP_LOG_PREFIX, "").trim()
    val withoutPythonPrefix = withoutLogPrefix.substringAfterLast("Error: ").trim()
    val message = withoutPythonPrefix.ifBlank { withoutLogPrefix.ifBlank { raw.trim() } }
    return message.take(MAX_MESSAGE_LENGTH).ifBlank { "Extraction failed for an unknown reason." }
}

private val YTDLP_LOG_PREFIX = Regex("""^(?:ERROR|WARNING):\s*(?:\[[^]]+]\s*)?(?:[\w.-]+:\s*)?""", RegexOption.IGNORE_CASE)

private const val MAX_MESSAGE_LENGTH = 300
