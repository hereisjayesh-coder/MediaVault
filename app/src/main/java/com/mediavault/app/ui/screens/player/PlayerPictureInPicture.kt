package com.mediavault.app.ui.screens.player

import androidx.compose.runtime.compositionLocalOf

/**
 * True while the hosting Activity is in Android's Picture-in-Picture mode. Set by
 * `MainActivity.onPictureInPictureModeChanged`, read here so the Player can swap its custom
 * Compose overlay controls for Media3's own minimal built-in `PlayerView` controller — Compose's
 * touch targets don't work reliably at PiP's tiny window size, but `PlayerView.useController`
 * is purpose-built for exactly this.
 */
val LocalIsInPictureInPicture = compositionLocalOf { false }
