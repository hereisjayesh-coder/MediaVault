package com.mediavault.app.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun `verifying the same pin against its own salt and hash succeeds`() {
        val salt = PinHasher.generateSalt()
        val hash = PinHasher.hash("1234".toCharArray(), salt)

        assertTrue(PinHasher.verify("1234".toCharArray(), salt, PinHasher.DEFAULT_ITERATIONS, hash))
    }

    @Test
    fun `verifying a different pin against the same salt and hash fails`() {
        val salt = PinHasher.generateSalt()
        val hash = PinHasher.hash("1234".toCharArray(), salt)

        assertFalse(PinHasher.verify("4321".toCharArray(), salt, PinHasher.DEFAULT_ITERATIONS, hash))
    }

    @Test
    fun `the same pin hashed with different salts produces different hashes`() {
        val saltA = PinHasher.generateSalt()
        val saltB = PinHasher.generateSalt()

        val hashA = PinHasher.hash("1234".toCharArray(), saltA)
        val hashB = PinHasher.hash("1234".toCharArray(), saltB)

        assertFalse(hashA.contentEquals(hashB))
    }

    @Test
    fun `generateSalt produces non-repeating, non-empty salts`() {
        val first = PinHasher.generateSalt()
        val second = PinHasher.generateSalt()

        assertTrue(first.isNotEmpty())
        assertFalse(first.contentEquals(second))
    }

    @Test
    fun `hashing is deterministic for the same pin, salt, and iteration count`() {
        val salt = PinHasher.generateSalt()

        val hashA = PinHasher.hash("9999".toCharArray(), salt, iterations = 10_000)
        val hashB = PinHasher.hash("9999".toCharArray(), salt, iterations = 10_000)

        assertTrue(hashA.contentEquals(hashB))
    }
}
