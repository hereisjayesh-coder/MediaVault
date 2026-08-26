package com.mediavault.app.ui.screens.player

/** Which third of the video surface a tap landed in — see [resolveTapAction]. */
enum class TapZone { LEFT, CENTER, RIGHT }

/** Which third of [totalWidth] an x position falls in. Defensive against a not-yet-measured (zero-width) surface — falls back to CENTER rather than dividing by zero. */
fun tapZoneFor(x: Float, totalWidth: Float): TapZone {
    if (totalWidth <= 0f) return TapZone.CENTER
    val thirdWidth = totalWidth / 3f
    return when {
        x < thirdWidth -> TapZone.LEFT
        x > thirdWidth * 2 -> TapZone.RIGHT
        else -> TapZone.CENTER
    }
}

/** What a resolved tap sequence (1, 2, or 3+ quick taps in the same zone) should do — the player gesture contract. */
sealed class PlayerTapAction {
    /** Single tap anywhere, or any tap count in the CENTER zone (no seek meaning there) — show/hide controls only, never a seek. */
    data object ToggleControls : PlayerTapAction()
    data class SeekBy(val deltaMs: Long) : PlayerTapAction()
}

internal const val DOUBLE_TAP_SEEK_MS = 10_000L
internal const val TRIPLE_TAP_SEEK_MS = 30_000L

/**
 * Pure mapping from "how many quick taps landed, in which zone" to the required gesture
 * behavior: a single tap must never seek (always [PlayerTapAction.ToggleControls]); double tap
 * seeks 10s, triple seeks 30s, both signed by [zone] — left is backward, right is forward. A
 * double/triple tap in the CENTER zone has no defined seek direction, so it falls back to the
 * same toggle-controls behavior as a single tap there rather than being ignored outright.
 *
 * Kept free of Compose/pointer-input entirely so the actual decision table is unit-testable
 * without simulating touch events — see [com.mediavault.app.ui.screens.player.VideoArea] for the
 * one caller, which owns only the timing/pointer plumbing that decides [tapCount] and [zone].
 */
fun resolveTapAction(zone: TapZone, tapCount: Int): PlayerTapAction = when {
    zone == TapZone.CENTER || tapCount <= 1 -> PlayerTapAction.ToggleControls
    tapCount == 2 -> PlayerTapAction.SeekBy(if (zone == TapZone.LEFT) -DOUBLE_TAP_SEEK_MS else DOUBLE_TAP_SEEK_MS)
    else -> PlayerTapAction.SeekBy(if (zone == TapZone.LEFT) -TRIPLE_TAP_SEEK_MS else TRIPLE_TAP_SEEK_MS)
}
