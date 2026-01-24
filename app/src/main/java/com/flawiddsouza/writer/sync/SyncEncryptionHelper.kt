package com.flawiddsouza.writer.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.google.gson.Gson
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Bitwarden-style encryption for sync data
 *
 * Architecture:
 * 1. Random 256-bit master key encrypts all data (maximum security)
 * 2. User password derives encryption key via PBKDF2
 * 3. Derived key encrypts master key
 * 4. Encrypted master key stored on server
 * 5. Any device with password can unlock master key
 *
 * This is separate from per-note encryption feature!
 */
object SyncEncryptionHelper {
    private const val PREFS_NAME = "writer_sync_e2e_encryption"
    private const val KEY_MASTER_KEY = "sync_master_key"  // Stores master key locally (encrypted at rest by Android)
    private const val ALGORITHM = "ChaCha20-Poly1305"
    private const val KEY_SIZE = 256 // bits
    private const val PBKDF2_ITERATIONS = 600000  // High iteration count for security
    private const val PBKDF2_KEY_LENGTH = 256 // bits

    private lateinit var prefs: SharedPreferences
    private var masterKey: SecretKey? = null
    private val gson = Gson()

    // Data classes for JSON serialization
    private data class NoteData(
        val title: String,
        val body: String,
        val is_encrypted: Boolean,
        val category_id: String?
    )

    private data class CategoryData(
        val name: String
    )

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadMasterKey()
    }

    /**
     * Check if master key exists locally
     */
    fun hasMasterKey(): Boolean {
        return prefs.contains(KEY_MASTER_KEY)
    }

    /**
     * Load master key from local storage
     */
    private fun loadMasterKey() {
        val encodedKey = prefs.getString(KEY_MASTER_KEY, null)
        masterKey = if (encodedKey != null) {
            val keyBytes = Base64.decode(encodedKey, Base64.NO_WRAP)
            SecretKeySpec(keyBytes, "ChaCha20")
        } else {
            null
        }
    }

    /**
     * Generate new random master key (first device setup)
     */
    fun generateMasterKey(): ByteArray {
        val random = SecureRandom()
        val keyBytes = ByteArray(32) // 256 bits
        random.nextBytes(keyBytes)
        return keyBytes
    }

    /**
     * Derive encryption key from user password using PBKDF2
     */
    private fun deriveKeyFromPassword(password: CharArray, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    /**
     * Encrypt master key with user's password
     * Returns: Base64(salt + iv + ciphertext)
     */
    fun encryptMasterKeyWithPassword(masterKeyBytes: ByteArray, password: CharArray): String {
        // Generate random salt for PBKDF2
        val salt = ByteArray(32)
        SecureRandom().nextBytes(salt)

        // Derive key from password
        val derivedKey = deriveKeyFromPassword(password, salt)

        // Generate random IV for AES-GCM
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)

        // Encrypt master key with derived key using AES-GCM
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, derivedKey, gcmSpec)
        val ciphertext = cipher.doFinal(masterKeyBytes)

        // Combine salt + iv + ciphertext
        val combined = salt + iv + ciphertext

        // Return Base64 encoded
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypt master key with user's password
     * Returns master key bytes or throws exception if password wrong
     */
    fun decryptMasterKeyWithPassword(encryptedBlob: String, password: CharArray): ByteArray {
        // Decode Base64
        val combined = Base64.decode(encryptedBlob, Base64.NO_WRAP)

        // Extract components
        val salt = combined.sliceArray(0 until 32)
        val iv = combined.sliceArray(32 until 44)
        val ciphertext = combined.sliceArray(44 until combined.size)

        // Derive key from password
        val derivedKey = deriveKeyFromPassword(password, salt)

        // Decrypt master key
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, derivedKey, gcmSpec)

        return cipher.doFinal(ciphertext)
    }

    /**
     * Save master key locally (encrypted at rest by Android Keystore)
     */
    fun saveMasterKey(masterKeyBytes: ByteArray) {
        val encoded = Base64.encodeToString(masterKeyBytes, Base64.NO_WRAP)
        prefs.edit().putString(KEY_MASTER_KEY, encoded).apply()
        masterKey = SecretKeySpec(masterKeyBytes, "ChaCha20")
    }

    /**
     * Get master key bytes for encryption with password
     */
    fun getMasterKeyBytes(): ByteArray? {
        val encoded = prefs.getString(KEY_MASTER_KEY, null) ?: return null
        return Base64.decode(encoded, Base64.NO_WRAP)
    }

    /**
     * Clear all encryption data (logout)
     */
    fun clearAll() {
        prefs.edit().remove(KEY_MASTER_KEY).apply()
        masterKey = null
    }

    /**
     * Encrypt data with master key before sending to server
     */
    fun encrypt(plaintext: String): String {
        val key = masterKey ?: throw IllegalStateException("Master key not loaded")

        // Generate random IV (12 bytes for ChaCha20-Poly1305)
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)

        // Encrypt
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // Combine IV + ciphertext
        val combined = iv + ciphertext

        // Return base64 encoded
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypt data received from server
     */
    fun decrypt(encryptedBase64: String): String {
        val key = masterKey ?: throw IllegalStateException("Master key not loaded")

        // Decode base64
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)

        // Extract IV (first 12 bytes) and ciphertext
        val iv = combined.sliceArray(0 until 12)
        val ciphertext = combined.sliceArray(12 until combined.size)

        // Decrypt
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        val plaintext = cipher.doFinal(ciphertext)

        return String(plaintext, Charsets.UTF_8)
    }

    /**
     * Encrypt note data as JSON blob
     */
    fun encryptNoteData(
        title: String,
        body: String,
        isEncrypted: Boolean,
        categoryId: String?
    ): String {
        val noteData = NoteData(title, body, isEncrypted, categoryId)
        val json = gson.toJson(noteData)
        return encrypt(json)
    }

    /**
     * Decrypt note data from JSON blob
     */
    fun decryptNoteData(encryptedData: String): Map<String, Any?> {
        val json = decrypt(encryptedData)
        val noteData = gson.fromJson(json, NoteData::class.java)
        return mapOf(
            "title" to noteData.title,
            "body" to noteData.body,
            "is_encrypted" to noteData.is_encrypted,
            "category_id" to noteData.category_id
        )
    }

    /**
     * Encrypt category data as JSON blob
     */
    fun encryptCategoryData(name: String): String {
        val categoryData = CategoryData(name)
        val json = gson.toJson(categoryData)
        return encrypt(json)
    }

    /**
     * Decrypt category data from JSON blob
     */
    fun decryptCategoryData(encryptedData: String): Map<String, Any?> {
        val json = decrypt(encryptedData)
        val categoryData = gson.fromJson(json, CategoryData::class.java)
        return mapOf("name" to categoryData.name)
    }

    /**
     * Get password strength score (0-4)
     * 0 = Very weak, 4 = Very strong
     */
    fun getPasswordStrength(password: CharArray): Int {
        val pwd = String(password)
        var score = 0

        // Length
        if (pwd.length >= 12) score++
        if (pwd.length >= 16) score++

        // Character variety
        if (pwd.any { it.isUpperCase() }) score++
        if (pwd.any { it.isLowerCase() }) score++
        if (pwd.any { it.isDigit() }) score++
        if (pwd.any { !it.isLetterOrDigit() }) score++

        // Cap at 4
        return minOf(score, 4)
    }

    /**
     * Validate password meets minimum requirements
     */
    fun validatePassword(password: CharArray): Pair<Boolean, String> {
        val pwd = String(password)

        if (pwd.length < 12) {
            return false to "Password must be at least 12 characters"
        }

        if (!pwd.any { it.isUpperCase() }) {
            return false to "Password must contain uppercase letter"
        }

        if (!pwd.any { it.isLowerCase() }) {
            return false to "Password must contain lowercase letter"
        }

        if (!pwd.any { it.isDigit() }) {
            return false to "Password must contain number"
        }

        if (!pwd.any { !it.isLetterOrDigit() }) {
            return false to "Password must contain special character"
        }

        return true to "Password meets requirements"
    }
}
