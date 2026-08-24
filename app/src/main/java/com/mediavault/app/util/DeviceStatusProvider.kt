package com.mediavault.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Real, on-device status only — no mobile-data budgeting or policy decisions. Those belong
 * to `NetworkPolicyManager`, which has no implementation yet; this just answers "what's the
 * current network/storage state right now" for the Home screen's status row.
 */
interface DeviceStatusProvider {
    /** Bytes free on the primary storage volume the app writes to. */
    fun freeStorageBytes(): Long
    fun networkStatus(): NetworkStatus
}

enum class NetworkStatus {
    WIFI,
    MOBILE_DATA,
    OFFLINE,
}

class AndroidDeviceStatusProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceStatusProvider {

    override fun freeStorageBytes(): Long {
        val path = context.getExternalFilesDir(null)?.path ?: context.filesDir.path
        return StatFs(path).availableBytes
    }

    override fun networkStatus(): NetworkStatus {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkStatus.OFFLINE
        val network = connectivityManager.activeNetwork ?: return NetworkStatus.OFFLINE
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkStatus.OFFLINE
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkStatus.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkStatus.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkStatus.MOBILE_DATA
            else -> NetworkStatus.OFFLINE
        }
    }
}
