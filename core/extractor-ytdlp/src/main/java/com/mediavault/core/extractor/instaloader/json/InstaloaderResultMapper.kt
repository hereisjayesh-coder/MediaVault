package com.mediavault.core.extractor.instaloader.json

import com.mediavault.core.domain.extractor.ExtractionResult
import com.mediavault.core.domain.extractor.MediaCollectionItem
import com.mediavault.core.domain.extractor.MediaCollectionResult
import com.mediavault.core.model.MediaType

/** Converts Instaloader's raw post shape into MediaVault's engine-agnostic domain model. */
fun InstaloaderPostJson.toExtractionResult(): ExtractionResult = ExtractionResult.Collection(toMediaCollectionResult())

/**
 * Every item Instaloader reported is kept, in its original order and at its original
 * [InstaloaderItemJson.index] — a real Instagram carousel is very often a mix of image and video
 * items, and an earlier version of this mapper silently dropped every video item, which both
 * undercounted the carousel and skipped whichever items happened to land between two images
 * (confirmed live: a 9-item mixed carousel showed as "2 images"). [MediaCollectionItem.mediaType]
 * now carries each item's real type instead of assuming image, so a video item routes through the
 * existing video download/player path exactly like a video item from any other source — no
 * second download engine, no Instagram-specific player code. An item Instaloader couldn't resolve
 * a URL for (see `analyze()`'s per-item try/except) still gets a row, marked
 * [MediaCollectionItem.isAvailable] = false, so the carousel's displayed count and positions stay
 * honest instead of that one item silently vanishing.
 */
fun InstaloaderPostJson.toMediaCollectionResult(): MediaCollectionResult {
    val collectionItems = items.map { item ->
        MediaCollectionItem(
            id = "${id}_${item.index}",
            index = item.index,
            mediaType = if (item.isVideo) MediaType.VIDEO else MediaType.IMAGE,
            mediaUrl = item.imageUrl,
            isAvailable = item.imageUrl != null,
            thumbnailUrl = item.thumbnailUrl ?: item.imageUrl,
        )
    }
    return MediaCollectionResult(
        id = id,
        sourceName = sourceName,
        title = title,
        thumbnailUrl = thumbnailUrl ?: collectionItems.firstOrNull { it.thumbnailUrl != null }?.thumbnailUrl,
        webpageUrl = webpageUrl,
        items = collectionItems,
    )
}
