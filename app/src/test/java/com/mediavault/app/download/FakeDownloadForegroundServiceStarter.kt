package com.mediavault.app.download

class FakeDownloadForegroundServiceStarter : DownloadForegroundServiceStarter {
    var startCount = 0
        private set

    override fun start() {
        startCount++
    }
}
