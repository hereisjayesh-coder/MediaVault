package com.mediavault.core.extractor.instaloader

import com.chaquo.python.PyException
import com.mediavault.core.common.AppError

/**
 * Best-effort mapping from Instaloader's Python-side exception text to a clean [AppError] —
 * mirrors `YtDlpErrorMapper`'s own convention exactly. Matches on both the exception class
 * name and its own message text, since Chaquopy's [PyException.message] formatting can
 * include either or both depending on how the exception was raised.
 */
internal fun PyException.toAppError(): AppError {
    val raw = message.orEmpty()
    return when {
        raw.contains("LoginRequiredException", ignoreCase = true) ||
            raw.contains("PrivateProfileNotFollowedException", ignoreCase = true) ||
            raw.contains("login required", ignoreCase = true) ||
            raw.contains("private", ignoreCase = true) ->
            AppError.Unsupported("This account is private or requires login — MediaVault only downloads from public posts.")

        raw.contains("ProfileNotExistsException", ignoreCase = true) ||
            raw.contains("QueryReturnedNotFoundException", ignoreCase = true) ||
            raw.contains("does not exist", ignoreCase = true) ||
            raw.contains("returned 404", ignoreCase = true) ->
            AppError.Unsupported("This post couldn't be found. It may have been deleted or the link is incorrect.")

        raw.contains("Not a recognized Instagram post URL", ignoreCase = true) ->
            AppError.Unsupported("This link isn't from a source MediaVault's extractor recognizes yet.")

        raw.contains("no longer part of the post", ignoreCase = true) ->
            AppError.Unsupported("This item is no longer available in the post.")

        raw.contains("ConnectionException", ignoreCase = true) ||
            raw.contains("ConnectionError", ignoreCase = true) ||
            raw.contains("Failed to establish a new connection", ignoreCase = true) ||
            raw.contains("Network is unreachable", ignoreCase = true) ->
            AppError.Network("Couldn't reach the source. Check your connection and try again.", this)

        raw.contains("timed out", ignoreCase = true) ->
            AppError.Timeout("Connection timed out. This source may be unavailable or blocked on your current network.", this)

        raw.contains("TooManyRequestsException", ignoreCase = true) ||
            raw.contains("429", ignoreCase = true) ->
            AppError.Source("The source is temporarily rate-limiting requests — try again in a moment.", this)

        else -> AppError.Unknown(cleanExceptionMessage(raw), this)
    }
}

/** Strips Chaquopy's Python-traceback noise down to the useful part — never surfaces a raw Python exception string to the UI. */
private fun cleanExceptionMessage(raw: String): String {
    val withoutPythonPrefix = raw.substringAfterLast("Error: ").substringAfterLast("Exception: ").trim()
    val message = withoutPythonPrefix.ifBlank { raw.trim() }
    return message.take(MAX_MESSAGE_LENGTH).ifBlank { "This post couldn't be analyzed for an unknown reason." }
}

private const val MAX_MESSAGE_LENGTH = 300
