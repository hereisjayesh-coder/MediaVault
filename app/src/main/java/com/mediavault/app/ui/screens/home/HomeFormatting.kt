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

/** e.g. "1080p • MP4 • 844 MB", omitting any part that isn't known. */
fun formatFormatSummary(format: MediaFormat): String {
    val parts = listOfNotNull(
        format.resolutionLabel,
        format.container.takeIf { it != "unknown" }?.uppercase(),
        formatFileSizeLabel(format.estimatedSizeBytes),
    )
    return parts.joinToString(" • ")
}
