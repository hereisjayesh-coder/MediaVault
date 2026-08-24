package com.mediavault.core.model

enum class DownloadStatus {
    QUEUED,
    ANALYZING,
    DOWNLOADING,
    PROCESSING,
    MERGING,
    COMPLETED,
    PAUSED,
    CANCELLED,
    FAILED,
}
