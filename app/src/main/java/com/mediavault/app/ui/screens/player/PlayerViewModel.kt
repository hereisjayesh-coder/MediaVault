package com.mediavault.app.ui.screens.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.ui.PlayerView
import com.mediavault.app.library.LibraryRepository
import com.mediavault.app.player.LastPlayedProvider
import com.mediavault.app.player.Media3PlayerEngine
import com.mediavault.app.player.PlayerEngineFactory
import com.mediavault.core.domain.player.PlayerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    private val playerEngineFactory: PlayerEngineFactory,
    private val lastPlayedProvider: LastPlayedProvider,
) : ViewModel() {

    private val requestedMediaItemId: String? = savedStateHandle[MEDIA_ITEM_ID_ARG]

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var playerEngine: PlayerEngine? = null

    /** The whole load-and-observe session, so a single cancel (real teardown or test cleanup) stops every child coroutine below. */
    private val sessionJob: Job

    init {
        sessionJob = viewModelScope.launch {
            val id = requestedMediaItemId ?: lastPlayedProvider.currentId()
            if (id == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            val item = libraryRepository.getById(id)
            if (item == null || !libraryRepository.fileExists(item)) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "This file is no longer available. It may have been moved or deleted.") }
                return@launch
            }

            lastPlayedProvider.setId(id)
            _uiState.update { it.copy(isLoading = false, item = item) }

            val engine = playerEngineFactory.create()
            playerEngine = engine
            engine.prepare(item.mediaUri)
            if (item.lastPlaybackPositionMs > 0) engine.seekTo(item.lastPlaybackPositionMs)
            engine.play()

            launch { engine.observeState().collect { state -> _uiState.update { it.copy(playback = state) } } }

            launch {
                while (isActive) {
                    delay(3_000)
                    persistPosition()
                }
            }
        }
    }

    private suspend fun persistPosition() {
        val item = _uiState.value.item ?: return
        val position = _uiState.value.playback?.positionMs ?: return
        libraryRepository.updatePlaybackPosition(item.id, position)
    }

    fun onPlayPauseToggled() {
        val engine = playerEngine ?: return
        if (_uiState.value.playback?.isPlaying == true) {
            engine.pause()
            viewModelScope.launch { persistPosition() }
        } else {
            engine.play()
        }
    }

    /** Called when the screen leaves composition (tab switch, back navigation) — stop audio even if the ViewModel itself survives a saved back-stack entry. */
    fun onScreenLeft() {
        playerEngine?.pause()
        viewModelScope.launch { persistPosition() }
    }

    fun onSeek(positionMs: Long) {
        playerEngine?.seekTo(positionMs)
        viewModelScope.launch { persistPosition() }
    }

    fun onSpeedSelected(speed: Float) {
        playerEngine?.setPlaybackSpeed(speed)
    }

    fun onAudioTrackSelected(trackId: String) {
        playerEngine?.selectAudioTrack(trackId)
    }

    fun onSubtitleTrackSelected(trackId: String?) {
        playerEngine?.selectSubtitleTrack(trackId)
    }

    fun onFullscreenToggled() {
        _uiState.update { it.copy(isFullscreen = !it.isFullscreen) }
    }

    /** Binds the video surface — see [Media3PlayerEngine.rawPlayer] for why this one call steps outside the [PlayerEngine] contract. */
    fun attachVideoSurface(playerView: PlayerView) {
        (playerEngine as? Media3PlayerEngine)?.let { playerView.player = it.rawPlayer }
    }

    /**
     * Stops the load session (state-observing collector + periodic position-save loop) without
     * a real Android `ViewModelStore` teardown. `internal` is module-wide in Kotlin, so JVM unit
     * tests (same `:app` module) can call this to let `runTest` finish cleanly instead of
     * tripping its "uncompleted coroutines" check on these intentionally-infinite background jobs.
     */
    internal fun cancelBackgroundWorkForTesting() {
        sessionJob.cancel()
    }

    override fun onCleared() {
        sessionJob.cancel()
        // viewModelScope is torn down around the same time onCleared runs, so a fire-and-forget
        // launch here isn't reliable — this is the one deliberate blocking call in the app,
        // covering a fast single-row Room UPDATE during a real teardown, not the steady state.
        val item = _uiState.value.item
        val position = _uiState.value.playback?.positionMs
        if (item != null && position != null) {
            runBlocking { libraryRepository.updatePlaybackPosition(item.id, position) }
        }
        playerEngine?.release()
        super.onCleared()
    }

    companion object {
        const val MEDIA_ITEM_ID_ARG = "mediaItemId"
    }
}
