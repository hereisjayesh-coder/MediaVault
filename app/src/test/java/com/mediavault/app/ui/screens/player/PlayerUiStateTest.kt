package com.mediavault.app.ui.screens.player

import com.mediavault.core.database.entity.MediaItemEntity
import com.mediavault.core.domain.player.PlaybackState
import com.mediavault.core.model.MediaType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [PlayerUiState.isAudioOnly] is the one piece of logic deciding which player presentation renders — see PlayerScreen's branch on it. */
class PlayerUiStateTest {

    private fun item(mediaType: MediaType) = MediaItemEntity(
        id = "a",
        title = "Title",
        mediaUri = "file:///a",
        mediaType = mediaType,
        durationMs = 1_000L,
        sizeBytes = 1L,
        container = null,
        isImported = false,
        sourceDownloadTaskId = null,
        lastPlaybackPositionMs = 0L,
        isFavorite = false,
        addedAtEpochMs = 0L,
    )

    private fun playback(hasVideoTrack: Boolean?) = PlaybackState(
        isPlaying = false,
        positionMs = 0L,
        durationMs = null,
        bufferedPercentage = 0,
        availableAudioTracks = emptyList(),
        availableSubtitleTracks = emptyList(),
        selectedAudioTrackId = null,
        selectedSubtitleTrackId = null,
        playbackSpeed = 1f,
        hasVideoTrack = hasVideoTrack,
    )

    @Test
    fun `falls back to the stored media type before any playback state exists`() {
        val audioState = PlayerUiState(item = item(MediaType.AUDIO), playback = null)
        val videoState = PlayerUiState(item = item(MediaType.VIDEO), playback = null)

        assertTrue(audioState.isAudioOnly)
        assertFalse(videoState.isAudioOnly)
    }

    @Test
    fun `falls back to the stored media type while the engine hasn't reported real tracks yet`() {
        val state = PlayerUiState(item = item(MediaType.AUDIO), playback = playback(hasVideoTrack = null))

        assertTrue(state.isAudioOnly)
    }

    @Test
    fun `real engine metadata confirming a video track overrides a stale AUDIO media type`() {
        val state = PlayerUiState(item = item(MediaType.AUDIO), playback = playback(hasVideoTrack = true))

        assertFalse(state.isAudioOnly)
    }

    @Test
    fun `real engine metadata confirming no video track overrides a stale VIDEO media type`() {
        val state = PlayerUiState(item = item(MediaType.VIDEO), playback = playback(hasVideoTrack = false))

        assertTrue(state.isAudioOnly)
    }

    @Test
    fun `no item at all is not treated as audio-only`() {
        val state = PlayerUiState(item = null, playback = null)

        assertFalse(state.isAudioOnly)
    }
}
