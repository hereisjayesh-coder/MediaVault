package com.mediavault.app.ui.screens.home

import com.mediavault.app.util.DeviceStatusProvider
import com.mediavault.app.util.NetworkStatus

class FakeDeviceStatusProvider(
    private val freeBytes: Long = 10_000_000_000L,
    private val status: NetworkStatus = NetworkStatus.WIFI,
) : DeviceStatusProvider {
    override fun freeStorageBytes(): Long = freeBytes
    override fun networkStatus(): NetworkStatus = status
}
