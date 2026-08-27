package com.mediavault.app.policy

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.networkPolicyDataStore by preferencesDataStore(name = "network_policy")

/** The user-editable mobile-data policy settings — see [NetworkPolicyStore.settings]. */
data class NetworkPolicySettings(
    val mobileDownloadsEnabled: Boolean,
    val perDownloadLimitBytes: Long,
    val dailyBudgetBytes: Long,
)

/**
 * Real, persisted mobile-data policy settings and actual usage-so-far-today — no fabricated
 * numbers. [AndroidNetworkPolicyManager] is the only reader of these for real download
 * decisions; the Settings screen ([settings]/setters) is the only writer.
 */
@Singleton
class NetworkPolicyStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mobileDownloadsEnabledKey = booleanPreferencesKey("mobile_downloads_enabled")
    private val perDownloadLimitKey = longPreferencesKey("per_download_mobile_limit_bytes")
    private val dailyBudgetKey = longPreferencesKey("daily_mobile_budget_bytes")
    private val usedTodayKey = longPreferencesKey("mobile_bytes_used_today")
    private val usageEpochDayKey = longPreferencesKey("usage_epoch_day")

    /** Live view of the three user-editable settings, for the Settings screen. */
    val settings: Flow<NetworkPolicySettings> = context.networkPolicyDataStore.data.map { it.toSettings() }

    suspend fun mobileDownloadsEnabled(): Boolean =
        context.networkPolicyDataStore.data.first()[mobileDownloadsEnabledKey] ?: DEFAULT_MOBILE_DOWNLOADS_ENABLED

    suspend fun perDownloadLimitBytes(): Long =
        context.networkPolicyDataStore.data.first()[perDownloadLimitKey] ?: DEFAULT_PER_DOWNLOAD_LIMIT_BYTES

    suspend fun dailyBudgetBytes(): Long =
        context.networkPolicyDataStore.data.first()[dailyBudgetKey] ?: DEFAULT_DAILY_BUDGET_BYTES

    suspend fun setMobileDownloadsEnabled(enabled: Boolean) {
        context.networkPolicyDataStore.edit { prefs -> prefs[mobileDownloadsEnabledKey] = enabled }
    }

    suspend fun setPerDownloadLimitBytes(bytes: Long) {
        context.networkPolicyDataStore.edit { prefs -> prefs[perDownloadLimitKey] = bytes }
    }

    suspend fun setDailyBudgetBytes(bytes: Long) {
        context.networkPolicyDataStore.edit { prefs -> prefs[dailyBudgetKey] = bytes }
    }

    /** Bytes transferred over mobile data today; resets automatically when the date rolls over. */
    suspend fun mobileBytesUsedToday(): Long {
        val prefs = context.networkPolicyDataStore.data.first()
        val storedDay = prefs[usageEpochDayKey]
        val today = LocalDate.now().toEpochDay()
        return if (storedDay == today) prefs[usedTodayKey] ?: 0L else 0L
    }

    suspend fun addMobileBytesUsedToday(bytes: Long) {
        if (bytes <= 0) return
        val today = LocalDate.now().toEpochDay()
        context.networkPolicyDataStore.edit { prefs ->
            val storedDay = prefs[usageEpochDayKey]
            val existing = if (storedDay == today) prefs[usedTodayKey] ?: 0L else 0L
            prefs[usageEpochDayKey] = today
            prefs[usedTodayKey] = existing + bytes
        }
    }

    private fun Preferences.toSettings() = NetworkPolicySettings(
        mobileDownloadsEnabled = this[mobileDownloadsEnabledKey] ?: DEFAULT_MOBILE_DOWNLOADS_ENABLED,
        perDownloadLimitBytes = this[perDownloadLimitKey] ?: DEFAULT_PER_DOWNLOAD_LIMIT_BYTES,
        dailyBudgetBytes = this[dailyBudgetKey] ?: DEFAULT_DAILY_BUDGET_BYTES,
    )

    private companion object {
        const val DEFAULT_MOBILE_DOWNLOADS_ENABLED = true
        const val DEFAULT_PER_DOWNLOAD_LIMIT_BYTES = 500L * 1024 * 1024
        const val DEFAULT_DAILY_BUDGET_BYTES = 2L * 1024 * 1024 * 1024
    }
}
