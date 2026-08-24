package com.mediavault.core.extractor.ytdlp.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors the small dict `get_progress()` returns from the Python side while a download runs. */
@Serializable
data class YtDlpProgressJson(
    val status: String? = null,
    @SerialName("downloaded_bytes") val downloadedBytes: Long? = null,
    @SerialName("total_bytes") val totalBytes: Long? = null,
    val speed: Double? = null,
    val eta: Long? = null,
)
