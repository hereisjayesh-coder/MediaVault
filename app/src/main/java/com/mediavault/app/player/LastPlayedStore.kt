package com.mediavault.app.player

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.lastPlayedDataStore by preferencesDataStore(name = "last_played")

/**
 * Remembers which library item was last opened, so the bottom-tab Player entry (reached with
 * no specific item id, unlike a Library-drill-in) can resume it — see the Library <-> Player
 * requirement in PROJECT_MASTER.md.
 */
interface LastPlayedProvider {
    suspend fun currentId(): String?
    suspend fun setId(mediaItemId: String)
}

@Singleton
class LastPlayedStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : LastPlayedProvider {
    private val lastPlayedIdKey = stringPreferencesKey("last_played_media_item_id")

    override suspend fun currentId(): String? = context.lastPlayedDataStore.data.first()[lastPlayedIdKey]

    override suspend fun setId(mediaItemId: String) {
        context.lastPlayedDataStore.edit { it[lastPlayedIdKey] = mediaItemId }
    }
}
