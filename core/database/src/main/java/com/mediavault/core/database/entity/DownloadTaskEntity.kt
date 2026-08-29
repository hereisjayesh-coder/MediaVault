package com.mediavault.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mediavault.core.model.DownloadStatus
import com.mediavault.core.model.MediaType

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey
    val id: String,
    /** Webpage URL to re-extract from — not a raw CDN URL, which may be short-lived/signed. */
    val sourceUrl: String,
    val title: String?,
    val sourceName: String?,
    val thumbnailUrl: String?,
    val mediaType: MediaType,
    val formatId: String?,
    /**
     * One or more separate audio-track format ids to remux with [formatId] — see
     * [ResolvedSelection.requiresProcessing][com.mediavault.core.domain.download.ResolvedSelection.requiresProcessing]
     * and `MediaProcessor`. Stored comma-joined in the same TEXT column the single-audio-track
     * design originally used ([joinForColumn]/[splitColumn]) — reinterpreting an existing
     * column's content rather than adding a new one, since a lone format id is already a valid
     * one-element "list". Null/blank means this task downloads directly, exactly as before
     * FFmpeg existed. Order matches [audioLanguageCodes] and, once a download starts,
     * [audioLocalCachePaths].
     */
    @ColumnInfo(name = "audioFormatId")
    val audioFormatIds: String? = null,
    /**
     * The language each entry in [audioFormatIds] was reported as, same order, comma-joined —
     * a blank entry means the source reported no language for that particular track (still
     * preserved by position, never dropped). Only meaningful for muxing metadata (see
     * `FFmpegMediaProcessor`); added alongside the v6→v7 schema change since no prior column
     * carried this — see [Migrations.MIGRATION_6_7][com.mediavault.core.database.Migrations].
     */
    val audioLanguageCodes: String? = null,
    /** File extension/container of the selected format, e.g. "mp4" — used to name the saved file. */
    val container: String?,
    /** SAF tree URI of the user-selected destination folder, chosen once up front. */
    val destinationTreeUri: String?,
    /** SAF file URI of the finished file — set only once [status] is COMPLETED. */
    val destinationUri: String?,
    /** Real filesystem path in app-private storage the video (or, for a direct task, the only) stream's bytes are/were written to mid-transfer. */
    val localCachePath: String?,
    /** Cache path each separately-downloaded audio stream's bytes are/were written to, comma-joined in the same order as [audioFormatIds] — see that field's own KDoc for why this reuses the original single-path column rather than adding a new one. Null for direct tasks. */
    @ColumnInfo(name = "audioLocalCachePath")
    val audioLocalCachePaths: String? = null,
    val status: DownloadStatus,
    val bytesTransferred: Long,
    val totalBytes: Long?,
    /** Whether a paused copy of this task can safely continue from its byte offset. */
    val canResume: Boolean,
    val errorMessage: String?,
    /** The extractor-assigned id of the source media, for future dedup/"already downloaded" checks. */
    val sourceMediaId: String? = null,
    /** Set when this task came from a playlist item; groups tasks and preserves playlist order. */
    val playlistId: String? = null,
    val playlistItemIndex: Int? = null,
    /** Denormalized across every task in a playlist group — avoids a separate playlist table for just two display fields. */
    val playlistTitle: String? = null,
    val playlistThumbnailUrl: String? = null,
    /**
     * The quality every task in this playlist group was queued at (see `QualityDescriptor`
     * in core:domain), persisted per-task so format resolution can resume after process death
     * without needing to remember anything outside Room. Null for non-playlist tasks, which
     * already know their exact `formatId` up front and never need to re-resolve one.
     *
     * Stores [com.mediavault.core.domain.download.QualityTier]'s enum name (or null for a direct
     * audio-only playlist quality) — reuses the original resolution-label column rather than
     * adding a new one, since the format-selection redesign replaced "match by resolution label"
     * with "match by quality tier" (see `QualityDescriptor.resolveForPlaylist`).
     */
    @ColumnInfo(name = "qualityResolutionLabel")
    val qualityTier: String? = null,
    /**
     * Deprecated, unused since the format-selection redesign — [QualityDescriptor][com.mediavault.core.domain.download.QualityDescriptor]
     * re-derives container/processing-need per item now (via [qualityTier]/[qualityAudioLanguageCodes]
     * and each item's own freshly-resolved format list) rather than persisting them separately.
     * Kept declared, always null/false from new code, rather than dropped: removing a column
     * requires a table-recreation migration, a needless risk for a purely cosmetic cleanup on a
     * database real downloads already exist in. Room's schema-identity check requires every
     * physical column from prior migrations to stay represented in the entity one way or another.
     */
    val qualityContainer: String? = null,
    val qualityHasVideo: Boolean? = null,
    val qualityHasAudio: Boolean? = null,
    val qualityRequiresProcessing: Boolean? = null,
    /** Mirrors `QualityDescriptor.audioLanguageCodes`, comma-joined ([joinForColumn]/[splitColumn]) — reuses the original single-language column, same reasoning as [audioFormatIds]'s own KDoc. */
    @ColumnInfo(name = "qualityAudioLanguageCode")
    val qualityAudioLanguageCodes: String? = null,
    /** Media duration, known from analysis — carried through to the completed `MediaItemEntity`. */
    val durationSeconds: Long? = null,
    /** The resolved format's resolution label (e.g. "1080p"), for Library display. Null for audio. */
    val resolutionLabel: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

private const val LIST_COLUMN_DELIMITER = ","

/** Comma-joins a list for storage in one of this entity's multi-value TEXT columns — null (not an empty string) when [this] is empty, so a genuinely-unset column stays `NULL` rather than `""`. */
fun List<String>.joinForColumn(): String? = takeIf { it.isNotEmpty() }?.joinToString(LIST_COLUMN_DELIMITER)

/** The inverse of [joinForColumn] — null/blank becomes an empty list, never a one-element list containing an empty string. */
fun String?.splitColumn(): List<String> = this?.split(LIST_COLUMN_DELIMITER)?.filter { it.isNotEmpty() }.orEmpty()

/**
 * Same shape as [joinForColumn], but keeps a blank entry as an unknown-at-that-position value
 * instead of collapsing it away — used for [DownloadTaskEntity.audioLanguageCodes], whose Nth
 * entry must stay aligned with [DownloadTaskEntity.audioFormatIds]'s own Nth entry even when one
 * particular track's language isn't known.
 */
fun List<String?>.joinPositional(): String? = takeIf { it.isNotEmpty() }?.joinToString(LIST_COLUMN_DELIMITER) { it.orEmpty() }

/** The inverse of [joinPositional] — an empty segment becomes null at that position, never dropped. */
fun String?.splitPositional(): List<String?> = this?.split(LIST_COLUMN_DELIMITER)?.map { it.ifEmpty { null } }.orEmpty()
