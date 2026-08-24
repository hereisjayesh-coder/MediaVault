package com.mediavault.app.ui.screens.sources

import com.mediavault.app.R
import com.mediavault.core.model.SourceCategory

fun SourceCategory.labelRes(): Int = when (this) {
    SourceCategory.SOCIAL_MEDIA -> R.string.source_category_social_media
    SourceCategory.VIDEO -> R.string.source_category_video
    SourceCategory.MUSIC -> R.string.source_category_music
    SourceCategory.AUDIO -> R.string.source_category_audio
    SourceCategory.EDUCATION -> R.string.source_category_education
    SourceCategory.NEWS -> R.string.source_category_news
    SourceCategory.LIVE_STREAMING -> R.string.source_category_live_streaming
    SourceCategory.PODCASTS -> R.string.source_category_podcasts
    SourceCategory.SPORTS -> R.string.source_category_sports
    SourceCategory.ANIME -> R.string.source_category_anime
    SourceCategory.CLOUD_HOSTING -> R.string.source_category_cloud_hosting
    SourceCategory.ADULT -> R.string.source_category_adult
    SourceCategory.OTHER -> R.string.source_category_other
}
