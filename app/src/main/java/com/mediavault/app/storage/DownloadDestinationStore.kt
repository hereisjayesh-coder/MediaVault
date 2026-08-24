package com.mediavault.app.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.destinationDataStore by preferencesDataStore(name = "download_destination")

/**
 * Persists the single SAF tree URI the user picked as their download folder. MediaVault never
 * hard-codes a storage path — see PROJECT_MASTER.md §11 — this is the one place that URI lives.
 */
interface DownloadDestinationProvider {
    val treeUri: Flow<String?>
    suspend fun currentTreeUri(): String?
    suspend fun setTreeUri(uri: String)
}

@Singleton
class DownloadDestinationStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : DownloadDestinationProvider {
    private val treeUriKey = stringPreferencesKey("destination_tree_uri")

    override val treeUri: Flow<String?> = context.destinationDataStore.data.map { it[treeUriKey] }

    override suspend fun currentTreeUri(): String? = treeUri.first()

    override suspend fun setTreeUri(uri: String) {
        context.destinationDataStore.edit { it[treeUriKey] = uri }
    }
}
