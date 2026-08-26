package com.mediavault.app.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure coverage of the player's tap-gesture contract — see [PlayerScreen.VideoArea] for the pointer-timing plumbing this decision table is fed from. */
class PlayerGesturesTest {

    // --- Zone detection ---------------------------------------------------------------------

    @Test
    fun `left third is LEFT, right third is RIGHT, middle third is CENTER`() {
        val width = 900f
        assertEquals(TapZone.LEFT, tapZoneFor(x = 0f, totalWidth = width))
        assertEquals(TapZone.LEFT, tapZoneFor(x = 299f, totalWidth = width))
        assertEquals(TapZone.CENTER, tapZoneFor(x = 300f, totalWidth = width))
        assertEquals(TapZone.CENTER, tapZoneFor(x = 600f, totalWidth = width))
        assertEquals(TapZone.RIGHT, tapZoneFor(x = 601f, totalWidth = width))
        assertEquals(TapZone.RIGHT, tapZoneFor(x = 900f, totalWidth = width))
    }

    @Test
    fun `zero width surface never divides by zero`() {
        assertEquals(TapZone.CENTER, tapZoneFor(x = 50f, totalWidth = 0f))
    }

    // --- Tap-count-and-zone resolution --------------------------------------------------------

    @Test
    fun `a single tap always toggles controls, in every zone, and never seeks`() {
        assertEquals(PlayerTapAction.ToggleControls, resolveTapAction(TapZone.LEFT, tapCount = 1))
        assertEquals(PlayerTapAction.ToggleControls, resolveTapAction(TapZone.CENTER, tapCount = 1))
        assertEquals(PlayerTapAction.ToggleControls, resolveTapAction(TapZone.RIGHT, tapCount = 1))
    }

    @Test
    fun `a zero or negative tap count is treated the same as a single tap`() {
        assertEquals(PlayerTapAction.ToggleControls, resolveTapAction(TapZone.LEFT, tapCount = 0))
    }

    @Test
    fun `double tap left seeks back 10 seconds`() {
        val action = resolveTapAction(TapZone.LEFT, tapCount = 2)
        assertTrue(action is PlayerTapAction.SeekBy)
        assertEquals(-10_000L, (action as PlayerTapAction.SeekBy).deltaMs)
    }

    @Test
    fun `double tap right seeks forward 10 seconds`() {
        val action = resolveTapAction(TapZone.RIGHT, tapCount = 2)
        assertTrue(action is PlayerTapAction.SeekBy)
        assertEquals(10_000L, (action as PlayerTapAction.SeekBy).deltaMs)
    }

    @Test
    fun `triple tap left seeks back 30 seconds`() {
        val action = resolveTapAction(TapZone.LEFT, tapCount = 3)
        assertTrue(action is PlayerTapAction.SeekBy)
        assertEquals(-30_000L, (action as PlayerTapAction.SeekBy).deltaMs)
    }

    @Test
    fun `triple tap right seeks forward 30 seconds`() {
        val action = resolveTapAction(TapZone.RIGHT, tapCount = 3)
        assertTrue(action is PlayerTapAction.SeekBy)
        assertEquals(30_000L, (action as PlayerTapAction.SeekBy).deltaMs)
    }

    @Test
    fun `a tap count beyond triple still resolves to the 30 second seek, never higher`() {
        val action = resolveTapAction(TapZone.RIGHT, tapCount = 4)
        assertTrue(action is PlayerTapAction.SeekBy)
        assertEquals(30_000L, (action as PlayerTapAction.SeekBy).deltaMs)
    }

    @Test
    fun `double or triple tap in the center zone has no seek meaning, so it only toggles controls`() {
        assertEquals(PlayerTapAction.ToggleControls, resolveTapAction(TapZone.CENTER, tapCount = 2))
        assertEquals(PlayerTapAction.ToggleControls, resolveTapAction(TapZone.CENTER, tapCount = 3))
    }
}
