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
