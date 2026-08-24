package com.mediavault.app.ui.screens.home

import com.mediavault.app.storage.DownloadDestinationProvider
import kotlinx.coroutines.flow.MutableStateFlow

class FakeDownloadDestinationProvider(initialUri: String? = null) : DownloadDestinationProvider {
    private val state = MutableStateFlow(initialUri)

    override val treeUri = state

    override suspend fun currentTreeUri(): String? = state.value

    override suspend fun setTreeUri(uri: String) {
        state.value = uri
    }
}
