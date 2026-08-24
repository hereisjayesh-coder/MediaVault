package com.mediavault.core.extractor.ytdlp.json

import kotlinx.serialization.json.Json

/** Shared decoder: yt-dlp's real info-dict has far more fields than [YtDlpInfoJson] models. */
val ytDlpJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}
