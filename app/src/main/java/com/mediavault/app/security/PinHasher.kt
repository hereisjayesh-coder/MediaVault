package com.mediavault.app.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Fixed-length by design — see [com.mediavault.app.ui.screens.lock.AppLockViewModel]'s KDoc for why App Lock uses a fixed 4-digit PIN rather than a variable-length one. */
const val PIN_LENGTH = 4

/**
 * Turns a PIN into a salted, iterated hash using only standard JDK crypto (`javax.crypto`,
 * `java.security`) — no custom cryptography, no third-party crypto library. Deliberately has no
 * `android.*` import so it stays a plain JVM unit under test; [com.mediavault.app.security.EncryptedPinCredentialStore]
 * is the only caller, and owns the actual (Keystore-backed) storage of the salt/hash this
 * produces. The raw PIN itself is never persisted anywhere — only this hash is.
 */
object PinHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    const val DEFAULT_ITERATIONS = 120_000

    fun generateSalt(): ByteArray = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }

    fun hash(pin: CharArray, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Constant-time comparison ([MessageDigest.isEqual]) so a wrong-PIN check can't leak timing information about how many leading bytes matched. */
    fun verify(pin: CharArray, salt: ByteArray, iterations: Int, expectedHash: ByteArray): Boolean =
        MessageDigest.isEqual(hash(pin, salt, iterations), expectedHash)
}
