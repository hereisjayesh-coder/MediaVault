package com.mediavault.core.model

enum class MediaType {
    VIDEO,
    AUDIO,
    /** A single downloaded image — a standalone image post or one item of an image collection/carousel. Never opened in the video/audio player. */
    IMAGE,
}
