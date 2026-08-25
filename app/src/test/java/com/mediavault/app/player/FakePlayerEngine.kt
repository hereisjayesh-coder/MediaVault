package com.mediavault.app.player

import com.mediavault.core.domain.player.PlaybackState
import com.mediavault.core.domain.player.PlayerEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakePlayerEngine : PlayerEngine {
    val prepareCalls = mutableListOf<String>()
    val seekCalls = mutableListOf<Long>()
    var playCalled = false
    var pauseCalled = false
    var released = false

    val state = MutableStateFlow(
        PlaybackState(
            isPlaying = false,
            positionMs = 0,
            durationMs = 10_000,
            bufferedPercentage = 0,
            availableAudioTracks = emptyList(),
            availableSubtitleTracks = emptyList(),
            selectedAudioTrackId = null,
            selectedSubtitleTrackId = null,
            playbackSpeed = 1f,
        ),
    )

    override fun prepare(mediaUri: String) {
        prepareCalls.add(mediaUri)
    }

    override fun play() {
        playCalled = true
        state.update { it.copy(isPlaying = true) }
    }

    override fun pause() {
        pauseCalled = true
        state.update { it.copy(isPlaying = false) }
    }

    override fun seekTo(positionMs: Long) {
        seekCalls.add(positionMs)
        state.update { it.copy(positionMs = positionMs) }
    }

    override fun setPlaybackSpeed(speed: Float) {
        state.update { it.copy(playbackSpeed = speed) }
    }

    override fun selectAudioTrack(trackId: String) {
        state.update { it.copy(selectedAudioTrackId = trackId) }
    }

    override fun selectSubtitleTrack(trackId: String?) {
        state.update { it.copy(selectedSubtitleTrackId = trackId) }
    }

    override fun setLooping(enabled: Boolean) {
        state.update { it.copy(isLooping = enabled) }
    }

    override fun observeState(): Flow<PlaybackState> = state

    override fun release() {
        released = true
    }
}

class FakePlayerEngineFactory(private val engine: FakePlayerEngine) : PlayerEngineFactory {
    var createCalls = 0
    override fun create(): PlayerEngine {
        createCalls++
        return engine
    }
}

class FakeLastPlayedProvider(initial: String? = null) : LastPlayedProvider {
    var id: String? = initial

    override suspend fun currentId(): String? = id

    override suspend fun setId(mediaItemId: String) {
        id = mediaItemId
    }
}
