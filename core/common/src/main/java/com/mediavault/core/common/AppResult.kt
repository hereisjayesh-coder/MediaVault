package com.mediavault.core.common

/**
 * Explicit success/failure wrapper for operations whose failures the UI must react to
 * (network calls, extraction, file I/O) instead of surfacing raw exceptions.
 */
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Failure(val error: AppError) : AppResult<Nothing>()
}

sealed class AppError(open val message: String, open val cause: Throwable? = null) {
    data class Network(override val message: String, override val cause: Throwable? = null) : AppError(message, cause)
    /** The operation exceeded its time budget with no response — distinct from [Network] so the UI can
     * name the specific failure without asserting *why* (slow source, unreachable host, or a blocked
     * network path all look identical from here). */
    data class Timeout(override val message: String, override val cause: Throwable? = null) : AppError(message, cause)
    data class Storage(override val message: String, override val cause: Throwable? = null) : AppError(message, cause)
    data class Unsupported(override val message: String) : AppError(message)
    /** The source (site/extractor) itself reported a failure — removed video, blocked, etc. */
    data class Source(override val message: String, override val cause: Throwable? = null) : AppError(message, cause)
    /** An OS-level permission (storage access, notifications, ...) was denied or missing. */
    data class Permission(override val message: String) : AppError(message)
    /** The operation was cancelled or paused by the user — not a real failure. */
    data class Cancelled(override val message: String = "Cancelled.") : AppError(message)
    data class Unknown(override val message: String, override val cause: Throwable? = null) : AppError(message, cause)
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}
