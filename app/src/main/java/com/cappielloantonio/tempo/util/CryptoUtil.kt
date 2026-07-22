package com.cappielloantonio.tempo.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Field-level encryption for credentials stored on disk (SharedPreferences values and the
 * Room `server.password` column). Backed by an AES key held in the Android Keystore, so the
 * key material never leaves secure hardware where available.
 *
 * Ciphertext is tagged with [PREFIX] so [decrypt] can recognise a value it produced and pass
 * through anything else unchanged. That makes reads tolerant of credentials written before this
 * was introduced: they are returned as-is (legacy plaintext) until the next write re-encrypts them.
 */
object CryptoUtil {
    private const val TAG = "CryptoUtil"

    private const val KEY_ALIAS = "tempus_credential_key"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128

    // Version marker prefixing every value we encrypt; also lets us tell ciphertext from legacy plaintext.
    private const val PREFIX = "enc:1:"
    private const val SEPARATOR = ":"

    @JvmStatic
    fun isEncrypted(value: String?): Boolean = value != null && value.startsWith(PREFIX)

    @JvmStatic
    fun encrypt(plain: String?): String? {
        if (plain == null) return null

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))

            PREFIX + Base64.encodeToString(iv, Base64.NO_WRAP) +
                    SEPARATOR + Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        } catch (e: Exception) {
            // Never store a value we could not encrypt; the caller keeps plaintext out of storage.
            Log.e(TAG, "Failed to encrypt value", e)
            null
        }
    }

    /**
     * Decrypts a value produced by [encrypt]. Returns:
     * - null for a null input,
     * - the input unchanged when it is not our ciphertext (legacy plaintext written before encryption),
     * - null when our ciphertext cannot be decrypted (e.g. the Keystore key was invalidated), so the
     *   caller treats it as "no valid credential" and re-authenticates rather than crashing.
     */
    @JvmStatic
    fun decrypt(stored: String?): String? {
        if (stored == null) return null
        if (!isEncrypted(stored)) return stored

        return try {
            val parts = stored.substring(PREFIX.length).split(SEPARATOR)
            if (parts.size != 2) return null

            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))

            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt value", e)
            null
        }
    }

    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            ?: throw IllegalStateException("Credential key missing from Keystore")
        return entry.secretKey
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
