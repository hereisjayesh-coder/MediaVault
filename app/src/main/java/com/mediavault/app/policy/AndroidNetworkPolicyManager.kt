package com.mediavault.app.policy

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.mediavault.app.util.DeviceStatusProvider
import com.mediavault.app.util.NetworkStatus
import com.mediavault.core.domain.network.NetworkPolicyDecision
import com.mediavault.core.domain.network.NetworkPolicyManager
import com.mediavault.core.model.MediaFormat
import com.mediavault.core.model.NetworkType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Sole owner of "should this download proceed right now" decisions (see the interface KDoc).
 * Network detection is delegated to [DeviceStatusProvider] rather than duplicated here; this
 * class only adds the budget/limit policy on top of it.
 */
@Singleton
class AndroidNetworkPolicyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceStatusProvider: DeviceStatusProvider,
    private val store: NetworkPolicyStore,
) : NetworkPolicyManager {

    override fun currentNetworkType(): NetworkType = deviceStatusProvider.networkStatus().toNetworkType()

    override fun observeNetworkType(): Flow<NetworkType> = callbackFlow {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivityManager == null) {
            trySend(NetworkType.UNKNOWN)
            awaitClose { }
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(currentNetworkType())
            }

            override fun onLost(network: Network) {
                trySend(currentNetworkType())
            }
        }

        trySend(currentNetworkType())
        connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    override suspend fun evaluate(estimatedSizeBytes: Long): NetworkPolicyDecision {
        if (currentNetworkType() != NetworkType.MOBILE) {
            return NetworkPolicyDecision.Allow
        }

        val remaining = remainingMobileDataBudgetBytes()
        if (remaining <= 0) {
            return NetworkPolicyDecision.Block("Today's mobile-data budget is used up.")
        }

        val perDownloadLimit = store.perDownloadLimitBytes()
        if (estimatedSizeBytes > perDownloadLimit) {
            return NetworkPolicyDecision.QueueForWifi
        }

        if (estimatedSizeBytes > remaining) {
            return NetworkPolicyDecision.Warn(
                "This download may exceed today's remaining mobile-data budget.",
            )
        }

        return NetworkPolicyDecision.Allow
    }

    override suspend fun remainingMobileDataBudgetBytes(): Long {
        val budget = store.dailyBudgetBytes()
        val used = store.mobileBytesUsedToday()
        return (budget - used).coerceAtLeast(0)
    }

    override suspend fun recordTransferredBytes(networkType: NetworkType, bytes: Long) {
        if (networkType == NetworkType.MOBILE) {
            store.addMobileBytesUsedToday(bytes)
        }
    }

    override suspend fun recommendQuality(availableFormats: List<MediaFormat>): MediaFormat? {
        if (availableFormats.isEmpty()) return null
        if (currentNetworkType() != NetworkType.MOBILE) {
            return availableFormats.maxByOrNull { it.estimatedSizeBytes ?: 0L }
        }
        val budget = remainingMobileDataBudgetBytes()
        return availableFormats
            .filter { (it.estimatedSizeBytes ?: Long.MAX_VALUE) <= budget }
            .maxByOrNull { it.estimatedSizeBytes ?: 0L }
    }

    private fun NetworkStatus.toNetworkType(): NetworkType = when (this) {
        NetworkStatus.WIFI -> NetworkType.WIFI
        NetworkStatus.MOBILE_DATA -> NetworkType.MOBILE
        NetworkStatus.OFFLINE -> NetworkType.UNKNOWN
    }
}
