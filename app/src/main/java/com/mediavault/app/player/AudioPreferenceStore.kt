package com.mediavault.app.player

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.audioPreferenceDataStore by preferencesDataStore(name = "audio_preference")

/**
 * Remembers the language code of the last audio track the user explicitly chose, so the next
 * file that offers a track in the same language starts on it automatically instead of always
 * defaulting to whatever the source marked default — "remember the user's last preferred audio
 * language where practical" per the player milestone. Only ever applied when a track with a
 * matching, source-reported language code actually exists; never guessed or invented.
 */
interface AudioPreferenceProvider {
    suspend fun preferredLanguage(): String?
    suspend fun setPreferredLanguage(languageCode: String)
}

@Singleton
class AudioPreferenceStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : AudioPreferenceProvider {
    private val preferredLanguageKey = stringPreferencesKey("preferred_audio_language")

    override suspend fun preferredLanguage(): String? = context.audioPreferenceDataStore.data.first()[preferredLanguageKey]

    override suspend fun setPreferredLanguage(languageCode: String) {
        context.audioPreferenceDataStore.edit { it[preferredLanguageKey] = languageCode }
    }
}
