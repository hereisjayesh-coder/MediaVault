package com.mediavault.core.domain.network

import com.mediavault.core.model.MediaFormat
import com.mediavault.core.model.NetworkType
import kotlinx.coroutines.flow.Flow

/**
 * Sole owner of "should this download proceed right now" decisions. The UI only ever
 * reads a [NetworkPolicyDecision]; it must never re-derive network/budget logic itself.
 */
interface NetworkPolicyManager {

    fun currentNetworkType(): NetworkType

    fun observeNetworkType(): Flow<NetworkType>

    /** Evaluates a candidate download of [estimatedSizeBytes] against the active policy. */
    suspend fun evaluate(estimatedSizeBytes: Long): NetworkPolicyDecision

    /** Bytes remaining in the configured daily mobile-data budget, based on actual usage so far today. */
    suspend fun remainingMobileDataBudgetBytes(): Long

    /** Must be called with the real number of bytes transferred so the daily budget stays accurate. */
    suspend fun recordTransferredBytes(networkType: NetworkType, bytes: Long)

    /** Picks the best format that fits the remaining budget, or null if even the smallest doesn't fit. */
    suspend fun recommendQuality(availableFormats: List<MediaFormat>): MediaFormat?
}

sealed class NetworkPolicyDecision {
    data object Allow : NetworkPolicyDecision()
    data class Warn(val reason: String) : NetworkPolicyDecision()
    data object QueueForWifi : NetworkPolicyDecision()
    data class Block(val reason: String) : NetworkPolicyDecision()
}
