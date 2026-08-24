package com.mediavault.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaVaultDestinationTest {

    @Test
    fun `every destination has a unique route`() {
        val routes = MediaVaultDestination.entries.map { it.route }

        assertEquals(routes.size, routes.toSet().size)
    }
}
