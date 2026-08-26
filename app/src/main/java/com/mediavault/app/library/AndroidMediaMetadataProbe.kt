package com.mediavault.app.library

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.mediavault.core.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AndroidMediaMetadataProbe @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaMetadataProbe {

    override suspend fun probe(uri: Uri, mediaType: MediaType): ProbedMetadata? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val resolutionLabel = if (mediaType == MediaType.VIDEO) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull()?.takeIf { it > 0 }?.let { "${it}p" }
            } else {
                null
            }
            val thumbnail = if (mediaType == MediaType.VIDEO) {
                runCatching { retriever.getFrameAtTime(0L) }.getOrNull()
            } else {
                runCatching { retriever.embeddedPicture }.getOrNull()
                    ?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
            }

            ProbedMetadata(durationMs = durationMs, resolutionLabel = resolutionLabel, thumbnail = thumbnail)
        } catch (e: Exception) {
            // MediaMetadataRetriever is documented to throw RuntimeException (not just
            // IOException/IllegalArgumentException) for corrupt/DRM-protected/unsupported-codec
            // files — this is the one deliberate broad catch in the import path, exactly at the
            // boundary the "handle unsupported formats gracefully" requirement is about.
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
