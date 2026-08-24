package com.mediavault.app.policy

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.networkPolicyDataStore by preferencesDataStore(name = "network_policy")

/**
 * Real, persisted mobile-data policy settings and actual usage-so-far-today — no fabricated
 * numbers. There's no Settings UI to edit the limits yet, so sensible defaults from
 * PROJECT_MASTER.md §8 are used until one exists.
 */
@Singleton
class NetworkPolicyStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val perDownloadLimitKey = longPreferencesKey("per_download_mobile_limit_bytes")
    private val dailyBudgetKey = longPreferencesKey("daily_mobile_budget_bytes")
    private val usedTodayKey = longPreferencesKey("mobile_bytes_used_today")
    private val usageEpochDayKey = longPreferencesKey("usage_epoch_day")

    suspend fun perDownloadLimitBytes(): Long =
        context.networkPolicyDataStore.data.first()[perDownloadLimitKey] ?: DEFAULT_PER_DOWNLOAD_LIMIT_BYTES

    suspend fun dailyBudgetBytes(): Long =
        context.networkPolicyDataStore.data.first()[dailyBudgetKey] ?: DEFAULT_DAILY_BUDGET_BYTES

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

    private companion object {
        const val DEFAULT_PER_DOWNLOAD_LIMIT_BYTES = 500L * 1024 * 1024
        const val DEFAULT_DAILY_BUDGET_BYTES = 2L * 1024 * 1024 * 1024
    }
}
