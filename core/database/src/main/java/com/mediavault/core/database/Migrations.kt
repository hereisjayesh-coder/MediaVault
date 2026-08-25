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
 * to remux with FFmpeg — see `MediaProcessor`/`DownloadOption`. Purely additive; both columns
 * are null for every pre-existing task, which is exactly the "direct download" behavior those
 * rows already had.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE download_tasks ADD COLUMN audioFormatId TEXT")
        db.execSQL("ALTER TABLE download_tasks ADD COLUMN audioLocalCachePath TEXT")
    }
}
