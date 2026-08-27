package com.mediavault.app.security

/** Plain in-memory comparison — real hash/verify correctness is [PinHasher]'s own test's job, not this fake's. */
class FakePinCredentialStore(initialPin: String? = null) : PinCredentialStore {
    private var storedPin: String? = initialPin

    override suspend fun isPinSet(): Boolean = storedPin != null

    override suspend fun setPin(pin: CharArray) {
        storedPin = String(pin)
    }

    override suspend fun verifyPin(pin: CharArray): Boolean = storedPin != null && storedPin == String(pin)

    override suspend fun clearPin() {
        storedPin = null
    }
}
