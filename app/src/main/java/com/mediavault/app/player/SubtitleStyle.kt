package com.mediavault.app.player

/**
 * User-selectable subtitle appearance, independent of the app's Light/Dark/System theme — see
 * [SubtitleStyleProvider] for persistence and `toCaptionStyleCompat()` (PlayerScreen.kt) for how
 * each style is actually rendered by Media3's `SubtitleView`.
 */
enum class SubtitleStyle {
    /** White text on a black semi-transparent background. */
    CLASSIC,
    /** White text, no background — default. */
    CLEAN,
    /** White text, no background, subtle black outline for readability. */
    OUTLINED,
}

/** Edge/outline treatment applied to subtitle text — mirrors Media3 `CaptionStyleCompat`'s edge types 1:1. */
enum class SubtitleEdgeType { NONE, OUTLINE }

/**
 * Plain, Android-independent description of one subtitle appearance — colors as `0xAARRGGBB` ints
 * rather than `android.graphics.Color`/`CaptionStyleCompat`, so the CLASSIC/CLEAN/OUTLINED mapping
 * itself is pure and unit-testable without Robolectric. The one place this becomes a real Media3
 * `CaptionStyleCompat` is `toCaptionStyleCompat()` in `PlayerScreen.kt`.
 */
data class SubtitleStyleSpec(
    val foregroundColor: Int,
    val backgroundColor: Int,
    val edgeType: SubtitleEdgeType,
    val edgeColor: Int,
)

private const val WHITE = 0xFFFFFFFF.toInt()
private const val TRANSPARENT = 0x00000000
private const val SEMI_TRANSPARENT_BLACK = 0xA0000000.toInt()
private const val BLACK = 0xFF000000.toInt()

fun SubtitleStyle.toSpec(): SubtitleStyleSpec = when (this) {
    SubtitleStyle.CLASSIC -> SubtitleStyleSpec(WHITE, SEMI_TRANSPARENT_BLACK, SubtitleEdgeType.NONE, TRANSPARENT)
    SubtitleStyle.CLEAN -> SubtitleStyleSpec(WHITE, TRANSPARENT, SubtitleEdgeType.NONE, TRANSPARENT)
    SubtitleStyle.OUTLINED -> SubtitleStyleSpec(WHITE, TRANSPARENT, SubtitleEdgeType.OUTLINE, BLACK)
}
