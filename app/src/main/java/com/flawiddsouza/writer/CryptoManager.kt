package com.flawiddsouza.writer

import android.util.Base64
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private const val ALGORITHM = "ChaCha20-Poly1305"
    private const val KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 310_000  // OWASP 2021 recommendation - balanced for mobile
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 32
    private const val NONCE_LENGTH = 12

    // Session password management - per note
    // Maps note ID to its password for this session
    private val sessionPasswords = mutableMapOf<Long, CharArray>()

    // Key cache: maps Base64-encoded salt to derived key
    // This avoids re-running expensive PBKDF2 for the same salt
    private val keyCache = mutableMapOf<String, SecretKey>()

    /**
     * Encrypts plaintext using ChaCha20-Poly1305
     * @param plaintext The text to encrypt
     * @param password The password to use for encryption
     * @param existingSalt Optional salt from previous encryption (for re-encrypting same note)
     * @return Base64-encoded string in format: [SALT]:[NONCE]:[CIPHERTEXT]
     * @throws Exception if encryption fails
     */
    @JvmOverloads
    fun encrypt(plaintext: String, password: CharArray, existingSalt: ByteArray? = null): String {
        // Use existing salt if provided, otherwise generate new one
        val salt = existingSalt ?: ByteArray(SALT_LENGTH).apply {
            SecureRandom().nextBytes(this)
        }

        // Derive key from password using PBKDF2 (will use cache if available)
        val key = deriveKey(password, salt)

        // Generate nonce (IV) for ChaCha20
        val nonce = ByteArray(NONCE_LENGTH)
        SecureRandom().nextBytes(nonce)

        // Encrypt
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(nonce))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // Encode everything to Base64 and return in format: SALT:NONCE:CIPHERTEXT
        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val nonceB64 = Base64.encodeToString(nonce, Base64.NO_WRAP)
        val ciphertextB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)

        return "$saltB64:$nonceB64:$ciphertextB64"
    }

    /**
     * Decrypts encrypted data using ChaCha20-Poly1305
     * @param encryptedData Base64-encoded string in format: [SALT]:[NONCE]:[CIPHERTEXT]
     * @param password The password to use for decryption
     * @return Decrypted plaintext
     * @throws Exception if decryption fails (wrong password, corrupted data, etc.)
     */
    fun decrypt(encryptedData: String, password: CharArray): String {
        // Parse the encrypted data format: SALT:NONCE:CIPHERTEXT
        val parts = encryptedData.split(":")
        if (parts.size != 3) {
            throw IllegalArgumentException("Invalid encrypted data format")
        }

        val salt = Base64.decode(parts[0], Base64.NO_WRAP)
        val nonce = Base64.decode(parts[1], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)

        // Derive key from password using same salt
        val key = deriveKey(password, salt)

        // Decrypt
        try {
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(nonce))
            val plaintext = cipher.doFinal(ciphertext)
            return String(plaintext, Charsets.UTF_8)
        } catch (e: Exception) {
            // Decryption failed (wrong password) - clear cached key for this salt
            // so next attempt with different password will derive fresh key
            val saltKey = Base64.encodeToString(salt, Base64.NO_WRAP)
            keyCache.remove(saltKey)
            throw e
        }
    }

    /**
     * Derives a secret key from password using PBKDF2
     * Uses cache to avoid re-deriving for the same salt
     */
    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
        // Check cache first
        val saltKey = Base64.encodeToString(salt, Base64.NO_WRAP)
        keyCache[saltKey]?.let { return it }

        // Not in cache, derive the key
        val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM)
        val spec: KeySpec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH)
        val tmp = factory.generateSecret(spec)
        val derivedKey = SecretKeySpec(tmp.encoded, "ChaCha20")

        // Store in cache for future use
        keyCache[saltKey] = derivedKey

        return derivedKey
    }

    /**
     * Extracts the salt from encrypted data
     * @param encryptedData Base64-encoded string in format: [SALT]:[NONCE]:[CIPHERTEXT]
     * @return The salt as ByteArray, or null if format is invalid
     */
    fun extractSalt(encryptedData: String): ByteArray? {
        return try {
            val parts = encryptedData.split(":")
            if (parts.size != 3) return null
            Base64.decode(parts[0], Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Securely clears a CharArray by overwriting with zeros
     */
    fun clearPassword(password: CharArray) {
        password.fill('\u0000')
    }

    // Session Management Functions

    /**
     * Gets the session password for a specific note
     * @param noteId The note ID
     * @return The password as CharArray, or null if not set
     */
    fun getSessionPassword(noteId: Long): CharArray? {
        return sessionPasswords[noteId]
    }

    /**
     * Sets the session password for a specific note
     * @param noteId The note ID
     * @param password The password to store in memory
     */
    fun setSessionPassword(noteId: Long, password: CharArray) {
        // Clear old password for this note if exists
        sessionPasswords[noteId]?.let { clearPassword(it) }
        // Store new password (make a copy to avoid external modifications)
        sessionPasswords[noteId] = password.copyOf()
    }

    /**
     * Checks if a session password is set for a specific note
     * @param noteId The note ID
     * @return true if password is set, false otherwise
     */
    fun hasSessionPassword(noteId: Long): Boolean {
        return sessionPasswords.containsKey(noteId)
    }

    /**
     * Clears the session password for a specific note
     * @param noteId The note ID
     */
    fun clearSessionPassword(noteId: Long) {
        sessionPasswords[noteId]?.let {
            clearPassword(it)
            sessionPasswords.remove(noteId)
        }
    }

    /**
     * Clears all session passwords and key cache
     * Call this when app closes or user logs out
     */
    fun clearAllSessions() {
        sessionPasswords.values.forEach { clearPassword(it) }
        sessionPasswords.clear()
        keyCache.clear()
    }
}
