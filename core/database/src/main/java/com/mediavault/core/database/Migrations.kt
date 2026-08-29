package com.mediavault.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds playlist-group display fields and the persisted quality descriptor used to resume
 * playlist format resolution after process death (see `MediaVaultDownloadEngine`). Purely
 * additive — no existing column changes, so every download/media row already on a device
 * survives this migration untouched.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE download_tasks ADD COLUMN playlistTitle TEXT")
        db.execSQL("ALTER TABLE download_tasks ADD COLUMN playlistThumbnailUrl TEXT")
        db.execSQL("ALTER TABLE download_tasks ADD COLUMN qualityResolutionLabel TEXT")
        db.execSQL("ALTER TABLE download_tasks ADD COLUMN qualityContainer TEXT")
        db.execSQL("ALTER TABLE download_tasks ADD COLUMN qualityHasVideo INTEGER")
        db.execSQL("ALTER TABLE download_tasks ADD COLUMN qualityHasAudio INTEGER")
    }
}

/**
 * Adds the metadata the Private Library needs to display real duration/resolution/thumbnail
 * per item, without a network call or local frame-generation. Purely additive.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE download_tasks ADD COLUMN durationSeconds INTEGER")
        db.execSQL("ALTER TABLE download_tasks ADD COLUMN resolutionLabel TEXT")
        db.execSQL("ALTER TABLE media_items ADD COLUMN resolutionLabel TEXT")
        db.execSQL("ALTER TABLE media_items ADD COLUMN thumbnailUrl TEXT")
    }
}

/**
 * Adds the second (audio) stream's format id and cache path a split video+audio download needs
 * to remux with FFmpeg — see `MediaProcessor`/`FormatSelectionModel`. Purely additive; both
 * columns are null for every pre-existing task, which is exactly the "direct download" behavior
 * those rows already had.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE download_tasks ADD COLUMN audioFormatId TEXT")
        db.execSQL("ALTER TABLE download_tasks ADD COLUMN audioLocalCachePath TEXT")
    }
}

/**
 * Adds the timestamp the Player tab's real Continue Watching / Recently Watched lists are
 * ordered by, stamped from now on by `LibraryRepository.updatePlaybackPosition`. A pre-existing
 * row already mid-playback (`lastPlaybackPositionMs > 0`) is backfilled using its own
 * `addedAtEpochMs` as the best available stand-in for "last watched" — without this, every
 * in-progress item a user already had would silently vanish from Continue Watching the moment
 * they update, until they happened to resume it. A never-played row stays null, correctly
 * excluded from both watch-history sections.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE media_items ADD COLUMN lastWatchedAtEpochMs INTEGER")
        db.execSQL("UPDATE media_items SET lastWatchedAtEpochMs = addedAtEpochMs WHERE lastPlaybackPositionMs > 0")
    }
}

/**
 * Adds the two quality-descriptor fields a merge-required (paired video+audio) playlist quality
 * needs on top of the existing shape fields from [MIGRATION_1_2] — see `QualityDescriptor`.
 * Purely additive; both columns are null for every pre-existing playlist task, which
 * `QualityDescriptor`'s own `requiresProcessing = false` default already treats as "this task's
 * quality was a direct one", exactly the only kind that existed before this migration.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE download_tasks ADD COLUMN qualityRequiresProcessing INTEGER")
        db.execSQL("ALTER TABLE download_tasks ADD COLUMN qualityAudioLanguageCode TEXT")
    }
}

/**
 * Adds the one genuinely new field the multi-audio-track format-selection redesign needs: which
 * language each entry in `audioFormatId` (comma-joined as of this same redesign — no schema
 * change needed for that, an existing TEXT column already holds a single format id, which is
 * already a valid one-element "list") was reported as, same order — needed to tag each muxed
 * audio track's language in the merged output. Every other change this redesign makes reuses
 * existing TEXT columns for comma-joined multi-value content rather than adding new ones — see
 * `DownloadTaskEntity`'s own field-level KDoc for the full accounting. Purely additive; null for
 * every pre-existing task, which downloads exactly as it always has (no separate audio tracks).
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE download_tasks ADD COLUMN audioLanguageCodes TEXT")
    }
}
