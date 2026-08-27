package com.mediavault.app.ui.screens.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.ui.PlayerView
import com.mediavault.app.library.LibraryRepository
import com.mediavault.app.player.AudioPreferenceProvider
import com.mediavault.app.player.LastPlayedProvider
import com.mediavault.app.player.Media3PlayerEngine
import com.mediavault.app.player.PlayerEngineFactory
import com.mediavault.app.player.PlayerPreferences
import com.mediavault.app.player.PlayerPreferencesProvider
import com.mediavault.app.player.SubtitleStyle
import com.mediavault.app.player.SubtitleStyleProvider
import com.mediavault.core.domain.player.PlaybackState
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
    private val audioPreferenceProvider: AudioPreferenceProvider,
    private val subtitleStyleProvider: SubtitleStyleProvider,
    private val playerPreferencesProvider: PlayerPreferencesProvider,
) : ViewModel() {

    private val requestedMediaItemId: String? = savedStateHandle[MEDIA_ITEM_ID_ARG]

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var playerEngine: PlayerEngine? = null

    // Tracked individually (rather than relying on structured-concurrency parentage alone) so
    // cancelBackgroundWorkForTesting() can stop every background loop regardless of which
    // caller (init, onNext/onPrevious, auto-advance) started the current one.
    private var stateCollectJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var controlsHideJob: Job? = null
    private var subtitleStyleJob: Job? = null
    private var playerPreferencesJob: Job? = null

    private var appliedPreferredAudioThisLoad = false
    private var appliedAutoFullscreenThisLoad = false
    private var pauseAtEndOfMedia = false
    /** Speed to restore when a long-press-to-2x gesture releases; null when no long-press is active. */
    private var speedBeforeLongPress: Float? = null
    /** Kept up to date by [playerPreferencesJob]; read synchronously wherever a preference decision is needed. */
    private var playerPreferences = PlayerPreferences()

    /** The whole load-and-observe session, so a single cancel (real teardown or test cleanup) stops every child coroutine below. */
    private val sessionJob: Job

    init {
        // Independent of the loaded media item — a persisted, app-wide appearance preference,
        // not per-video playback state, so it's collected for this ViewModel's whole lifetime
        // rather than reset on loadItem().
        subtitleStyleJob = viewModelScope.launch {
            subtitleStyleProvider.subtitleStyle.collect { style -> _uiState.update { it.copy(subtitleStyle = style) } }
        }
        playerPreferencesJob = viewModelScope.launch {
            playerPreferencesProvider.preferences.collect { prefs ->
                playerPreferences = prefs
                _uiState.update { it.copy(autoEnterPip = prefs.autoEnterPip) }
            }
        }

        sessionJob = viewModelScope.launch {
            val id = requestedMediaItemId ?: lastPlayedProvider.currentId()
            if (id == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            loadItem(id)
            if (_uiState.value.item == null) return@launch

            launch {
                while (isActive) {
                    delay(3_000)
                    persistPosition()
                }
            }
        }
    }

    /** Loads [id] as the active item — used for the initial load, Previous/Next, and end-of-media auto-advance alike. */
    private suspend fun loadItem(id: String) {
        val item = libraryRepository.getById(id)
        if (item == null || !libraryRepository.fileExists(item)) {
            stateCollectJob?.cancel()
            playerEngine?.release()
            playerEngine = null
            _uiState.update {
                it.copy(
                    isLoading = false,
                    item = null,
                    playback = null,
                    errorMessage = "This file is no longer available. It may have been moved or deleted.",
                )
            }
            return
        }

        lastPlayedProvider.setId(id)
        val playlistItems = libraryRepository.getPlaylistSiblings(item)

        stateCollectJob?.cancel()
        playerEngine?.release()
        appliedPreferredAudioThisLoad = false
        appliedAutoFullscreenThisLoad = false
        pauseAtEndOfMedia = false
        speedBeforeLongPress = null
        sleepTimerJob?.cancel()
        sleepTimerJob = null

        _uiState.update {
            it.copy(
                isLoading = false,
                item = item,
                playlistItems = playlistItems,
                playback = null,
                errorMessage = null,
                sleepTimer = SleepTimerOption.OFF,
                sleepTimerRemainingMs = null,
            )
        }

        val engine = playerEngineFactory.create()
        playerEngine = engine
        engine.prepare(item.mediaUri)
        if (item.lastPlaybackPositionMs > 0 && playerPreferences.resumePlaybackEnabled) {
            engine.seekTo(item.lastPlaybackPositionMs)
        }
        engine.play()
        if (playerPreferences.defaultPlaybackSpeed != 1f) engine.setPlaybackSpeed(playerPreferences.defaultPlaybackSpeed)

        stateCollectJob = viewModelScope.launch {
            engine.observeState().collect { state -> onPlaybackState(state) }
        }
    }

    private suspend fun onPlaybackState(state: PlaybackState) {
        _uiState.update { it.copy(playback = state) }

        if (!appliedPreferredAudioThisLoad && state.availableAudioTracks.isNotEmpty()) {
            appliedPreferredAudioThisLoad = true
            val preferred = audioPreferenceProvider.preferredLanguage()
            val match = preferred?.let { code -> state.availableAudioTracks.firstOrNull { it.languageCode == code } }
            if (match != null && match.id != state.selectedAudioTrackId) {
                playerEngine?.selectAudioTrack(match.id)
            }
        }

        // Real dimensions are only known once `videoAspectRatio` arrives (null for audio-only
        // media or before the first frame) — applied once per load, same guard style as the
        // preferred-audio block above.
        val aspectRatio = state.videoAspectRatio
        if (!appliedAutoFullscreenThisLoad && aspectRatio != null) {
            appliedAutoFullscreenThisLoad = true
            if (playerPreferences.autoFullscreenLandscape && aspectRatio >= 1f && !_uiState.value.isFullscreen) {
                _uiState.update { it.copy(isFullscreen = true, controlsVisible = true) }
            }
        }

        if (state.isEnded) handlePlaybackEnded()
    }

    private fun handlePlaybackEnded() {
        if (pauseAtEndOfMedia) {
            pauseAtEndOfMedia = false
            _uiState.update { it.copy(sleepTimer = SleepTimerOption.OFF) }
            return
        }
        if (!playerPreferences.autoAdvancePlaylist) return
        val state = _uiState.value
        val currentIndex = state.playlistItems.indexOfFirst { it.id == state.item?.id }
        val nextItem = state.playlistItems.getOrNull(currentIndex + 1) ?: return
        viewModelScope.launch { loadItem(nextItem.id) }
    }

    private suspend fun persistPosition() {
        val item = _uiState.value.item ?: return
        val position = _uiState.value.playback?.positionMs ?: return
        libraryRepository.updatePlaybackPosition(item.id, position)
    }

    fun onPlayPauseToggled() {
        val engine = playerEngine ?: return
        val playback = _uiState.value.playback
        when {
            playback?.isEnded == true -> {
                // Player.STATE_ENDED already stopped playback on its own — "resume" here means replay from the start.
                engine.seekTo(0)
                engine.play()
            }
            playback?.isPlaying == true -> {
                engine.pause()
                viewModelScope.launch { persistPosition() }
            }
            else -> engine.play()
        }
        scheduleControlsAutoHide()
    }

    /** Called when the screen leaves composition (tab switch, back navigation) — stop audio even if the ViewModel itself survives a saved back-stack entry. */
    fun onScreenLeft() {
        playerEngine?.pause()
        viewModelScope.launch { persistPosition() }
    }

    fun onSeek(positionMs: Long) {
        playerEngine?.seekTo(positionMs)
        viewModelScope.launch { persistPosition() }
        scheduleControlsAutoHide()
    }

    /** Powers the -10s/+10s controls — clamps to the item's own bounds, never seeks past either end. */
    fun seekBy(deltaMs: Long) {
        val playback = _uiState.value.playback ?: return
        val duration = playback.durationMs ?: Long.MAX_VALUE
        onSeek((playback.positionMs + deltaMs).coerceIn(0L, duration))
    }

    fun onSpeedSelected(speed: Float) {
        playerEngine?.setPlaybackSpeed(speed)
    }

    /** YouTube-style hold-for-2x gesture: remembers whatever speed was active so release can restore it exactly, not just reset to 1x. */
    fun onLongPressSpeedEngaged() {
        if (speedBeforeLongPress != null) return
        speedBeforeLongPress = _uiState.value.playback?.playbackSpeed ?: 1f
        playerEngine?.setPlaybackSpeed(2f)
    }

    fun onLongPressSpeedReleased() {
        val previousSpeed = speedBeforeLongPress ?: return
        speedBeforeLongPress = null
        playerEngine?.setPlaybackSpeed(previousSpeed)
    }

    fun onAudioTrackSelected(trackId: String) {
        playerEngine?.selectAudioTrack(trackId)
        val languageCode = _uiState.value.playback?.availableAudioTracks?.firstOrNull { it.id == trackId }?.languageCode
        if (languageCode != null) {
            viewModelScope.launch { audioPreferenceProvider.setPreferredLanguage(languageCode) }
        }
    }

    fun onSubtitleTrackSelected(trackId: String?) {
        playerEngine?.selectSubtitleTrack(trackId)
    }

    /** Persisted via [subtitleStyleProvider] — [subtitleStyleJob] reflects the new value back into [uiState] once the write completes. */
    fun onSubtitleStyleSelected(style: SubtitleStyle) {
        viewModelScope.launch { subtitleStyleProvider.setSubtitleStyle(style) }
    }

    fun onLoopToggled() {
        val newValue = !(_uiState.value.playback?.isLooping ?: false)
        playerEngine?.setLooping(newValue)
    }

    fun onResizeModeSelected(mode: VideoResizeMode) {
        _uiState.update { it.copy(resizeMode = mode) }
    }

    fun onDetailsToggled(show: Boolean) {
        _uiState.update { it.copy(showDetails = show) }
    }

    fun onNext() {
        val state = _uiState.value
        val index = state.playlistItems.indexOfFirst { it.id == state.item?.id }
        val next = state.playlistItems.getOrNull(index + 1) ?: return
        viewModelScope.launch { loadItem(next.id) }
    }

    fun onPrevious() {
        val state = _uiState.value
        val index = state.playlistItems.indexOfFirst { it.id == state.item?.id }
        if (index <= 0) return
        val previous = state.playlistItems.getOrNull(index - 1) ?: return
        viewModelScope.launch { loadItem(previous.id) }
    }

    fun onSleepTimerSelected(option: SleepTimerOption) {
        sleepTimerJob?.cancel()
        pauseAtEndOfMedia = false
        _uiState.update { it.copy(sleepTimer = option, sleepTimerRemainingMs = null) }

        when (option) {
            SleepTimerOption.OFF -> Unit
            SleepTimerOption.END_OF_MEDIA -> pauseAtEndOfMedia = true
            else -> {
                val totalMs = (option.minutes ?: return) * 60_000L
                sleepTimerJob = viewModelScope.launch {
                    var remainingMs = totalMs
                    while (remainingMs > 0) {
                        _uiState.update { it.copy(sleepTimerRemainingMs = remainingMs) }
                        delay(1_000)
                        remainingMs -= 1_000
                    }
                    playerEngine?.pause()
                    persistPosition()
                    _uiState.update { it.copy(sleepTimer = SleepTimerOption.OFF, sleepTimerRemainingMs = null) }
                }
            }
        }
    }

    fun onFullscreenToggled() {
        _uiState.update { it.copy(isFullscreen = !it.isFullscreen, controlsVisible = true) }
        scheduleControlsAutoHide()
    }

    /** Tapping the video surface toggles the overlay controls while fullscreen; a no-op otherwise (controls are always visible in the embedded layout). */
    fun onSurfaceTapped() {
        if (!_uiState.value.isFullscreen) return
        _uiState.update { it.copy(controlsVisible = !it.controlsVisible) }
        scheduleControlsAutoHide()
    }

    private fun scheduleControlsAutoHide() {
        controlsHideJob?.cancel()
        val state = _uiState.value
        if (state.isFullscreen && state.controlsVisible) {
            controlsHideJob = viewModelScope.launch {
                delay(3_500)
                if (_uiState.value.playback?.isPlaying == true) {
                    _uiState.update { it.copy(controlsVisible = false) }
                }
            }
        }
    }

    /** Binds the video surface — see [Media3PlayerEngine.rawPlayer] for why this one call steps outside the [PlayerEngine] contract. */
    fun attachVideoSurface(playerView: PlayerView) {
        (playerEngine as? Media3PlayerEngine)?.let { playerView.player = it.rawPlayer }
    }

    /**
     * Stops every background loop (state-observing collector, periodic position-save loop,
     * sleep timer, controls auto-hide) without a real Android `ViewModelStore` teardown.
     * `internal` is module-wide in Kotlin, so JVM unit tests (same `:app` module) can call this
     * to let `runTest` finish cleanly instead of tripping its "uncompleted coroutines" check on
     * these intentionally-long-lived background jobs.
     */
    internal fun cancelBackgroundWorkForTesting() {
        sessionJob.cancel()
        stateCollectJob?.cancel()
        sleepTimerJob?.cancel()
        controlsHideJob?.cancel()
        subtitleStyleJob?.cancel()
        playerPreferencesJob?.cancel()
    }

    override fun onCleared() {
        cancelBackgroundWorkForTesting()
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
