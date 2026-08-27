package com.mediavault.app.player

import kotlinx.coroutines.flow.MutableStateFlow

class FakeSubtitleStyleProvider(initial: SubtitleStyle = SubtitleStyle.CLEAN) : SubtitleStyleProvider {
    private val state = MutableStateFlow(initial)

    override val subtitleStyle = state

    override suspend fun setSubtitleStyle(style: SubtitleStyle) {
        state.value = style
    }
}
