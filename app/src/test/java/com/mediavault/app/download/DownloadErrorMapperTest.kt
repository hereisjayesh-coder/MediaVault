package com.mediavault.app.download

import com.mediavault.core.common.AppError
import java.io.IOException
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadErrorMapperTest {

    @Test
    fun `security exception maps to Permission`() {
        val error = SecurityException("no access").toDownloadAppError()

        assertTrue(error is AppError.Permission)
    }

    @Test
    fun `out of space IOException maps to Storage with a specific message`() {
        val error = IOException("write failed: ENOSPC (No space left on device)").toDownloadAppError()

        assertTrue(error is AppError.Storage)
        assertTrue((error as AppError.Storage).message.contains("space", ignoreCase = true))
    }

    @Test
    fun `generic IOException maps to Storage`() {
        val error = IOException("stream closed unexpectedly").toDownloadAppError()

        assertTrue(error is AppError.Storage)
    }

    @Test
    fun `unrecognized exception maps to Unknown`() {
        val error = IllegalStateException("something odd happened").toDownloadAppError()

        assertTrue(error is AppError.Unknown)
    }
}
