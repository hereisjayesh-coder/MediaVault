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
    data class Storage(override val message: String, override val cause: Throwable? = null) : AppError(message, cause)
    data class Unsupported(override val message: String) : AppError(message)
    data class Unknown(override val message: String, override val cause: Throwable? = null) : AppError(message, cause)
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}
