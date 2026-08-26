package com.mediavault.app.ui.screens.player

import com.mediavault.core.database.entity.MediaItemEntity

/** An item counts as "finished" once playback position reaches this fraction of its duration — matches the same near-the-end tolerance a real re-watch/resume decision needs (never expecting an exact 100%). */
private const val FINISHED_FRACTION = 0.95f

data class WatchHistorySections(
    /** Started but not finished, most-recently-watched first — resuming picks up where it left off. */
    val continueWatching: List<MediaItemEntity>,
    /** Finished (or never actually progressed past 0, an edge case with no real progress to resume), most-recently-watched first. */
    val recentlyWatched: List<MediaItemEntity>,
)

/**
 * Pure partition of a recency-ordered watch history into the Player tab's two sections — no
 * DAO/Compose involved, so this is directly unit-testable. Order within each section is
 * preserved from the input (already most-recently-watched-first from the DAO query).
 */
fun List<MediaItemEntity>.toWatchHistorySections(): WatchHistorySections {
    val (continueWatching, recentlyWatched) = partition { it.isInProgress() }
    return WatchHistorySections(continueWatching, recentlyWatched)
}

private fun MediaItemEntity.isInProgress(): Boolean {
    if (lastPlaybackPositionMs <= 0) return false
    val duration = durationMs ?: return true // unknown duration (e.g. a probe failure) — any real progress counts as in-progress rather than guessing it's finished
    return lastPlaybackPositionMs < duration * FINISHED_FRACTION
}
