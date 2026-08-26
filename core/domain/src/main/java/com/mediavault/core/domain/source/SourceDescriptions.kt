package com.mediavault.core.domain.source

import com.mediavault.core.model.Source
import com.mediavault.core.model.SourceCategory

/**
 * Hand-curated, one-to-two-sentence descriptions for the handful of services users look up by
 * name most often. Deliberately NOT exhaustive: with ~1,000 services in the catalog,
 * hand-authoring a description for every one doesn't scale (or read as genuinely useful for
 * services almost nobody searches for individually) and would bloat the app for little benefit.
 * Everything not listed here falls back to [genericDescriptionFor] instead of going unlabeled.
 *
 * Keyed by [Source.id] (the catalog's stable slug), not display name, so entries survive a
 * catalog regeneration untouched as long as the underlying service's slug doesn't change.
 */
object CuratedSourceDescriptions {
    val byId: Map<String, String> = mapOf(
        "youtube" to "Video-sharing platform covering everything from music videos and vlogs to full-length films and live streams.",
        "instagram" to "Social media platform for sharing photos, videos, Stories and Reels.",
        "reddit" to "Discussion forum organized into topic-based communities, where video and image posts are shared and discussed.",
        "vimeo" to "Video hosting platform favored by independent filmmakers and creative professionals for high-quality video.",
        "tiktok" to "Short-form video platform built around music, trends, and creator clips.",
        "facebook" to "Social networking platform where people share posts, videos, and live streams with friends and groups.",
        "twitter" to "Short-form social media platform (also known as X) for text posts, images, and video clips.",
        "twitch" to "Live-streaming platform focused on gaming, esports, and creator broadcasts.",
        "soundcloud" to "Audio platform for streaming and sharing music, podcasts, and independent artist tracks.",
        "dailymotion" to "General-purpose video-sharing platform similar in spirit to YouTube.",
        "bilibili" to "Chinese video-sharing platform popular for anime, gaming, and creator content.",
        "bandcamp" to "Platform for independent musicians to sell and stream their music directly to fans.",
        "spotify" to "Music and podcast streaming service.",
        "linkedin" to "Professional networking platform that also hosts video posts and articles.",
        "pinterest" to "Visual discovery platform for images and short videos organized into boards.",
        "tumblr" to "Blogging and social platform for short-form posts, images, and video.",
        "vk" to "Russian social networking platform for posts, videos, and live streams.",
        "rumble" to "Video-sharing platform positioned as an alternative to mainstream video platforms.",
        "bitchute" to "Video-hosting platform positioned as an alternative to mainstream video platforms.",
        "archiveorg" to "Non-profit digital library archiving web pages, audio, video, and books.",
    )

    /**
     * A safe, honest fallback for the services with no hand-written entry above: names what
     * MediaVault actually knows about the source (its primary category) rather than guessing
     * further or leaving the field blank.
     */
    fun genericDescriptionFor(categories: List<SourceCategory>): String {
        val category = categories.firstOrNull() ?: SourceCategory.OTHER
        return "A ${category.genericNoun()} source supported by MediaVault's extraction engine."
    }
}

private fun SourceCategory.genericNoun(): String = when (this) {
    SourceCategory.SOCIAL_MEDIA -> "social media"
    SourceCategory.VIDEO -> "video"
    SourceCategory.MUSIC -> "music"
    SourceCategory.AUDIO -> "audio"
    SourceCategory.EDUCATION -> "educational"
    SourceCategory.NEWS -> "news"
    SourceCategory.LIVE_STREAMING -> "live-streaming"
    SourceCategory.PODCASTS -> "podcast"
    SourceCategory.SPORTS -> "sports"
    SourceCategory.ANIME -> "anime"
    SourceCategory.CLOUD_HOSTING -> "cloud storage/hosting"
    SourceCategory.ADULT -> "adult content"
    SourceCategory.OTHER -> "media"
}

/**
 * The description to actually show for this source: [description] itself when the catalog was
 * already enriched (the normal path, via [withCuratedDescriptions]); otherwise a direct curated
 * lookup by [Source.id] as a safety net for a source that reached the UI unenriched; otherwise a
 * generic, category-based fallback. This is the single place that curated-vs-fallback decision
 * is made, so no UI/ViewModel layer needs to duplicate it.
 */
fun Source.displayDescription(): String =
    description
        ?: CuratedSourceDescriptions.byId[id]
        ?: CuratedSourceDescriptions.genericDescriptionFor(categories)

/** Applies [CuratedSourceDescriptions] to every source in this catalog that doesn't already
 * carry a description (the JSON catalog itself never does — see `SourceCatalogJson.kt`). Called
 * once, at catalog load time, by whichever [SourceCatalogRepository] implementation parses the
 * raw catalog, so the rest of the app can just read [Source.description]/[displayDescription]. */
fun SourceCatalog.withCuratedDescriptions(): SourceCatalog =
    copy(sources = sources.map { source -> source.copy(description = source.description ?: CuratedSourceDescriptions.byId[source.id]) })
