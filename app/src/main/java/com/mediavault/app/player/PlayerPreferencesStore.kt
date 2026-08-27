package com.mediavault.app.player

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.playerPreferencesDataStore by preferencesDataStore(name = "player_preferences")

/** The user-editable playback-behavior preferences a Settings screen exposes — see [PlayerPreferencesProvider]. */
data class PlayerPreferences(
    /** Applied to every newly-loaded item; 1x means "don't override the source's normal speed." */
    val defaultPlaybackSpeed: Float = 1f,
    /** When false, playback always starts from 0 even if a saved position exists. */
    val resumePlaybackEnabled: Boolean = true,
    /** Automatically enters fullscreen once a landscape/square video's real dimensions are known. */
    val autoFullscreenLandscape: Boolean = false,
    /** Android 12+ (`setAutoEnterEnabled`) only — see [PlayerPreferencesProvider]'s KDoc. */
    val autoEnterPip: Boolean = false,
    /** When false, playback simply stops (instead of advancing) at the end of a playlist item. */
    val autoAdvancePlaylist: Boolean = true,
)

/**
 * Persists player-wide behavior preferences — separate from [SubtitleStyleProvider] (subtitle
 * appearance) and [AudioPreferenceProvider] (remembered audio language), which are their own
 * focused stores rather than folded in here, matching this app's one-store-per-concern
 * convention. [autoEnterPip] is a real, technically-meaningful preference only from Android 12
 * (API 31) onward, where `PictureInPictureParams.Builder.setAutoEnterEnabled` lets the OS enter
 * PiP automatically when the user leaves the app while a video is playing; below API 31 it's
 * inert (PiP stays manual-entry-only) since no such platform API exists to hook it into.
 */
interface PlayerPreferencesProvider {
    val preferences: Flow<PlayerPreferences>
    suspend fun currentPreferences(): PlayerPreferences
    suspend fun setDefaultPlaybackSpeed(speed: Float)
    suspend fun setResumePlaybackEnabled(enabled: Boolean)
    suspend fun setAutoFullscreenLandscape(enabled: Boolean)
    suspend fun setAutoEnterPip(enabled: Boolean)
    suspend fun setAutoAdvancePlaylist(enabled: Boolean)
}

@Singleton
class PlayerPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlayerPreferencesProvider {

    private val defaultPlaybackSpeedKey = floatPreferencesKey("default_playback_speed")
    private val resumePlaybackEnabledKey = booleanPreferencesKey("resume_playback_enabled")
    private val autoFullscreenLandscapeKey = booleanPreferencesKey("auto_fullscreen_landscape")
    private val autoEnterPipKey = booleanPreferencesKey("auto_enter_pip")
    private val autoAdvancePlaylistKey = booleanPreferencesKey("auto_advance_playlist")

    override val preferences: Flow<PlayerPreferences> = context.playerPreferencesDataStore.data.map { it.toPlayerPreferences() }

    override suspend fun currentPreferences(): PlayerPreferences =
        context.playerPreferencesDataStore.data.first().toPlayerPreferences()

    override suspend fun setDefaultPlaybackSpeed(speed: Float) {
        context.playerPreferencesDataStore.edit { it[defaultPlaybackSpeedKey] = speed }
    }

    override suspend fun setResumePlaybackEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { it[resumePlaybackEnabledKey] = enabled }
    }

    override suspend fun setAutoFullscreenLandscape(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { it[autoFullscreenLandscapeKey] = enabled }
    }

    override suspend fun setAutoEnterPip(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { it[autoEnterPipKey] = enabled }
    }

    override suspend fun setAutoAdvancePlaylist(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { it[autoAdvancePlaylistKey] = enabled }
    }

    private fun Preferences.toPlayerPreferences(): PlayerPreferences = PlayerPreferences(
        defaultPlaybackSpeed = this[defaultPlaybackSpeedKey] ?: 1f,
        resumePlaybackEnabled = this[resumePlaybackEnabledKey] ?: true,
        autoFullscreenLandscape = this[autoFullscreenLandscapeKey] ?: false,
        autoEnterPip = this[autoEnterPipKey] ?: false,
        autoAdvancePlaylist = this[autoAdvancePlaylistKey] ?: true,
    )
}
