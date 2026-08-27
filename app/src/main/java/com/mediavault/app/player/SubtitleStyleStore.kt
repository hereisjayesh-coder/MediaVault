package com.mediavault.app.player

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.subtitleStyleDataStore by preferencesDataStore(name = "subtitle_style")

/**
 * Persists the user's chosen subtitle appearance across every video and app session — same
 * [androidx.datastore.preferences] approach already used for the theme and audio-language
 * preferences. Defaults to [SubtitleStyle.CLEAN] until the user picks something else.
 */
interface SubtitleStyleProvider {
    val subtitleStyle: Flow<SubtitleStyle>
    suspend fun setSubtitleStyle(style: SubtitleStyle)
}

@Singleton
class SubtitleStyleStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : SubtitleStyleProvider {

    private val subtitleStyleKey = stringPreferencesKey("subtitle_style")

    override val subtitleStyle: Flow<SubtitleStyle> = context.subtitleStyleDataStore.data.map { prefs -> prefs.toSubtitleStyle() }

    override suspend fun setSubtitleStyle(style: SubtitleStyle) {
        context.subtitleStyleDataStore.edit { prefs -> prefs[subtitleStyleKey] = style.name }
    }

    private fun Preferences.toSubtitleStyle(): SubtitleStyle =
        this[subtitleStyleKey]?.let { stored -> runCatching { SubtitleStyle.valueOf(stored) }.getOrNull() } ?: SubtitleStyle.CLEAN
}
