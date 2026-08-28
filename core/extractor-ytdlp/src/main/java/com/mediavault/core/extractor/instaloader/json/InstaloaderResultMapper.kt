package com.mediavault.core.extractor.instaloader.json

import com.mediavault.core.domain.extractor.ExtractionResult
import com.mediavault.core.domain.extractor.MediaCollectionItem
import com.mediavault.core.domain.extractor.MediaCollectionResult

/** Converts Instaloader's raw post shape into MediaVault's engine-agnostic domain model. */
fun InstaloaderPostJson.toExtractionResult(): ExtractionResult = ExtractionResult.Collection(toMediaCollectionResult())

/**
 * Only image items are kept — MediaVault's Collection/`MediaType.IMAGE` pipeline doesn't
 * handle video (that stays on yt-dlp end to end), so a video item mixed into an otherwise
 * image carousel is dropped here rather than surfaced as something the app can't actually
 * download or play. Each surviving item keeps its *original* [InstaloaderItemJson.index] —
 * never renumbered — since that index is also the `format_id` `download()` uses to re-find
 * the exact same node in the post's own node list.
 */
fun InstaloaderPostJson.toMediaCollectionResult(): MediaCollectionResult {
    val imageItems = items.filter { !it.isVideo }.map { item ->
        MediaCollectionItem(
            id = "${id}_${item.index}",
            index = item.index,
            imageUrl = item.imageUrl,
            thumbnailUrl = item.thumbnailUrl ?: item.imageUrl,
        )
    }
    return MediaCollectionResult(
        id = id,
        sourceName = sourceName,
        title = title,
        thumbnailUrl = thumbnailUrl ?: imageItems.firstOrNull()?.thumbnailUrl,
        webpageUrl = webpageUrl,
        items = imageItems,
    )
}
