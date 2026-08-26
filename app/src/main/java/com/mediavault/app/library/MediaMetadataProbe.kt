package com.mediavault.app.library

import android.graphics.Bitmap
import android.net.Uri
import com.mediavault.core.model.MediaType

/**
 * Reads whatever metadata is available for an already-authorized (SAF-granted) media [Uri] —
 * never writes anything, never touches locations the caller didn't already resolve. Kept behind
 * an interface, like [com.mediavault.core.domain.extractor.ExtractorEngine] and
 * [com.mediavault.core.domain.processing.MediaProcessor], so [AndroidMediaImportRepository]'s own
 * orchestration logic doesn't have to depend on `android.media.MediaMetadataRetriever` directly.
 */
interface MediaMetadataProbe {
    /** Null means the file couldn't be read at all (corrupt, DRM-protected, unsupported codec, I/O error) — the caller should skip it, not crash the whole import. */
    suspend fun probe(uri: Uri, mediaType: MediaType): ProbedMetadata?
}

data class ProbedMetadata(
    val durationMs: Long?,
    /** e.g. "1080p" — null for audio or when the source doesn't report dimensions. */
    val resolutionLabel: String?,
    /** A video frame (for video) or embedded cover art (for audio) — null when neither is available. Never scaled/cached here; see [AndroidMediaImportRepository] for that. */
    val thumbnail: Bitmap?,
)
