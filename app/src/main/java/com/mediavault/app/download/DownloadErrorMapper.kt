package com.mediavault.app.download

import com.mediavault.core.common.AppError
import java.io.IOException

/**
 * Maps failures that happen in the download *orchestrator* itself (moving the finished file
 * into MediaVault's private storage, mostly) rather than inside yt-dlp — those are already
 * mapped close to the source by `PyException.toAppError()` in core:extractor-ytdlp.
 */
internal fun Throwable.toDownloadAppError(): AppError = when (this) {
    is SecurityException ->
        AppError.Permission("MediaVault doesn't have permission to write to its private storage.")

    is IOException -> {
        val text = message.orEmpty()
        if (text.contains("ENOSPC", ignoreCase = true) || text.contains("No space left", ignoreCase = true)) {
            AppError.Storage("Not enough storage space to finish this download.", this)
        } else {
            AppError.Storage("Couldn't save the file to the selected location.", this)
        }
    }

    else -> AppError.Unknown(message ?: "Something went wrong while downloading.", this)
}
