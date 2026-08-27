package com.mediavault.app.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stores only a salted [PinHasher] verifier for the app-lock PIN — never the raw PIN itself, and
 * never in Room or a plain (unencrypted) `SharedPreferences`/DataStore file. [EncryptedSharedPreferences]
 * (an Android Jetpack Security API, backed by an Android Keystore [MasterKey]) is used exactly as
 * documented for "small amounts of sensitive data" rather than inventing a custom on-disk cipher.
 */
interface PinCredentialStore {
    suspend fun isPinSet(): Boolean
    suspend fun setPin(pin: CharArray)
    suspend fun verifyPin(pin: CharArray): Boolean
    suspend fun clearPin()
}

@Singleton
class EncryptedPinCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : PinCredentialStore {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "app_lock_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun isPinSet(): Boolean = withContext(Dispatchers.IO) {
        prefs.contains(KEY_HASH)
    }

    override suspend fun setPin(pin: CharArray): Unit = withContext(Dispatchers.IO) {
        val salt = PinHasher.generateSalt()
        val hash = PinHasher.hash(pin, salt)
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt(KEY_ITERATIONS, PinHasher.DEFAULT_ITERATIONS)
            .apply()
        pin.fill('0')
    }

    override suspend fun verifyPin(pin: CharArray): Boolean = withContext(Dispatchers.IO) {
        val saltB64 = prefs.getString(KEY_SALT, null)
        val hashB64 = prefs.getString(KEY_HASH, null)
        if (saltB64 == null || hashB64 == null) {
            pin.fill('0')
            return@withContext false
        }
        val iterations = prefs.getInt(KEY_ITERATIONS, PinHasher.DEFAULT_ITERATIONS)
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val expected = Base64.decode(hashB64, Base64.NO_WRAP)
        val result = PinHasher.verify(pin, salt, iterations, expected)
        pin.fill('0')
        result
    }

    override suspend fun clearPin(): Unit = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_SALT = "pin_salt"
        const val KEY_HASH = "pin_hash"
        const val KEY_ITERATIONS = "pin_iterations"
    }
}
