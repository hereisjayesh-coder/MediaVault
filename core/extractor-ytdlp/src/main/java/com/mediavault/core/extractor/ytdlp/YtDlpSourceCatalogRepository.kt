package com.mediavault.core.extractor.ytdlp

import android.content.Context
import com.mediavault.core.domain.source.SourceCatalog
import com.mediavault.core.domain.source.SourceCatalogRepository
import com.mediavault.core.extractor.ytdlp.json.SourceCatalogJson
import com.mediavault.core.extractor.ytdlp.json.toSourceCatalog
import com.mediavault.core.extractor.ytdlp.json.ytDlpJson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Reads the Supported Sources catalog from a bundled JSON asset generated offline by
 * `core/extractor-ytdlp/scripts/generate_source_catalog.py` from the pinned yt-dlp's own
 * extractor registry — see that script's docstring for how to regenerate it after a yt-dlp
 * version bump. Deliberately not a runtime Chaquopy/network call: this data changes only
 * when yt-dlp itself is upgraded, not on every app launch.
 */
@Singleton
class YtDlpSourceCatalogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SourceCatalogRepository {

    private val loadMutex = Mutex()

    @Volatile
    private var cached: SourceCatalog? = null

    override suspend fun getCatalog(): SourceCatalog {
        cached?.let { return it }
        return loadMutex.withLock {
            cached?.let { return it }
            val loaded = withContext(Dispatchers.IO) {
                context.assets.open(CATALOG_ASSET_PATH).use { stream ->
                    val json = stream.readBytes().toString(Charsets.UTF_8)
                    ytDlpJson.decodeFromString(SourceCatalogJson.serializer(), json).toSourceCatalog()
                }
            }
            cached = loaded
            loaded
        }
    }

    private companion object {
        const val CATALOG_ASSET_PATH = "source_catalog.json"
    }
}
