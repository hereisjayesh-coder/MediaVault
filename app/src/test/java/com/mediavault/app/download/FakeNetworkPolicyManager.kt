package com.mediavault.app.download

import com.mediavault.core.domain.network.NetworkPolicyDecision
import com.mediavault.core.domain.network.NetworkPolicyManager
import com.mediavault.core.model.MediaFormat
import com.mediavault.core.model.NetworkType
import kotlinx.coroutines.flow.MutableStateFlow

class FakeNetworkPolicyManager(
    var decision: NetworkPolicyDecision = NetworkPolicyDecision.Allow,
) : NetworkPolicyManager {

    val recordedTransfers = mutableListOf<Pair<NetworkType, Long>>()

    override fun currentNetworkType(): NetworkType = NetworkType.WIFI
    override fun observeNetworkType() = MutableStateFlow(NetworkType.WIFI)
    override suspend fun evaluate(estimatedSizeBytes: Long): NetworkPolicyDecision = decision
    override suspend fun remainingMobileDataBudgetBytes(): Long = Long.MAX_VALUE

    override suspend fun recordTransferredBytes(networkType: NetworkType, bytes: Long) {
        recordedTransfers.add(networkType to bytes)
    }

    override suspend fun recommendQuality(availableFormats: List<MediaFormat>): MediaFormat? = availableFormats.firstOrNull()
}
