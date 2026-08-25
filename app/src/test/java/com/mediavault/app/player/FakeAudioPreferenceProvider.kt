package com.mediavault.app.player

class FakeAudioPreferenceProvider(initial: String? = null) : AudioPreferenceProvider {
    var languageCode: String? = initial

    override suspend fun preferredLanguage(): String? = languageCode

    override suspend fun setPreferredLanguage(languageCode: String) {
        this.languageCode = languageCode
    }
}
