package com.mediavault.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppResultTest {

    @Test
    fun `map transforms success payload`() {
        val result: AppResult<Int> = AppResult.Success(2)

        val mapped = result.map { it * 21 }

        assertTrue(mapped is AppResult.Success)
        assertEquals(42, (mapped as AppResult.Success).data)
    }

    @Test
    fun `map leaves failure untouched`() {
        val failure: AppResult<Int> = AppResult.Failure(AppError.Network("offline"))

        val mapped = failure.map { it * 21 }

        assertTrue(mapped is AppResult.Failure)
        assertEquals("offline", (mapped as AppResult.Failure).error.message)
    }
}
