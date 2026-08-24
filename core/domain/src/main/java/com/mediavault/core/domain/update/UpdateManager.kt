package com.mediavault.core.domain.update

import com.mediavault.core.common.AppResult

/**
 * Reports version information and checks GitHub Releases for newer builds. This never
 * downloads or applies an update automatically — MediaVault only points the user at the
 * release page; installation goes through the normal APK/package flow.
 */
interface UpdateManager {

    suspend fun checkForUpdate(): AppResult<UpdateCheckResult>

    fun currentVersionInfo(): AppVersionInfo
}

data class UpdateCheckResult(
    val isUpdateAvailable: Boolean,
    val latestVersionName: String,
    val releaseNotesUrl: String?,
    val releaseUrl: String,
)

/**
 * The app version, extraction-engine version, and media-processing version are tracked
 * independently so an engine/backend upgrade never has to be conflated with an app release.
 */
data class AppVersionInfo(
    val appVersionName: String,
    val appVersionCode: Long,
    val extractorEngineVersion: String?,
    val mediaProcessingVersion: String?,
)
