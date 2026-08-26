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

        raw.contains("No information could be extracted", ignoreCase = true) ->
            AppError.Unsupported("Nothing playable was found at this URL.")

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

/** Strips yt-dlp's noisy `ERROR: [extractor] id: ` / Python traceback prefixes down to the useful part. */
private fun cleanExtractorMessage(raw: String): String {
    val withoutPythonPrefix = raw.substringAfterLast("Error: ").trim()
    val message = withoutPythonPrefix.ifBlank { raw.trim() }
    return message.take(MAX_MESSAGE_LENGTH).ifBlank { "Extraction failed for an unknown reason." }
}

private const val MAX_MESSAGE_LENGTH = 300
