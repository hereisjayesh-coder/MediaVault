package com.mediavault.app.ui.screens.home

import com.mediavault.core.model.MediaFormat

/** "596" -> "9:56", "3725" -> "1:02:05". Returns null when there's nothing to show. */
fun formatDurationLabel(totalSeconds: Long?): String? {
    if (totalSeconds == null || totalSeconds < 0) return null
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/** Bytes to a short human-readable label, e.g. 884_000_000 -> "843 MB". Null when unknown. */
fun formatFileSizeLabel(bytes: Long?): String? {
    if (bytes == null || bytes <= 0) return null
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return if (unitIndex == 0) "$bytes B" else "%.0f %s".format(value, units[unitIndex])
}

/** e.g. "1080p 60fps • MP4 • H.264 • 844 MB • with audio (AAC)", omitting any part that isn't known. */
fun formatFormatSummary(format: MediaFormat): String {
    // Some sources (yt-dlp included) report the resolution field as the literal string
    // "audio only" for audio-only formats — that's already conveyed by audioLabel below.
    val resolutionAndFps = listOfNotNull(
        format.resolutionLabel?.takeIf { format.hasVideo },
        format.fps?.takeIf { it > 0 && format.hasVideo }?.let { "${it}fps" },
    ).joinToString(" ").ifBlank { null }

    val audioLabel = when {
        !format.hasVideo && format.hasAudio -> "audio only" + (format.audioCodec?.let { " ($it)" } ?: "")
        format.hasVideo && format.hasAudio -> "with audio" + (format.audioCodec?.let { " ($it)" } ?: "")
        format.hasVideo && !format.hasAudio -> "video only"
        else -> null
    }

    val parts = listOfNotNull(
        resolutionAndFps,
        format.container.takeIf { it != "unknown" }?.uppercase(),
        format.videoCodec?.takeIf { format.hasVideo },
        formatFileSizeLabel(format.estimatedSizeBytes),
        audioLabel,
    )
    return parts.joinToString(" • ")
}
