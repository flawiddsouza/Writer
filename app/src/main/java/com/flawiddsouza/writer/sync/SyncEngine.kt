package com.flawiddsouza.writer.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.crypto.BadPaddingException

/**
 * Isolated sync engine with zero dependencies on app database schema.
 *
 * All database access goes through SyncRepository interface.
 * This enables merging sync years later even if schema changes.
 */
class SyncEngine(
    private val context: Context,
    private val repository: SyncRepository
) {

    companion object {
        private const val TAG = "SyncEngine"
        private const val PREFS_NAME = "writer_sync_config"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USER_TOKEN = "user_token"
        private const val KEY_USER_EMAIL = "user_email"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // Initialize E2E encryption
        SyncEncryptionHelper.initialize(context)
        // Initialize conflict storage
        ConflictStorage.initialize(context)
    }

    // ========== Configuration Management ==========

    fun getServerUrl(): String? = prefs.getString(KEY_SERVER_URL, null)

    fun setServerUrl(url: String) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
    }

    fun getUserToken(): String? = prefs.getString(KEY_USER_TOKEN, null)

    fun setUserToken(token: String?) {
        prefs.edit().putString(KEY_USER_TOKEN, token).apply()
        ApiClient.setAuthToken(token)
    }

    fun getUserEmail(): String? = prefs.getString(KEY_USER_EMAIL, null)

    fun setUserEmail(email: String?) {
        prefs.edit().putString(KEY_USER_EMAIL, email).apply()
    }

    fun isConfigured(): Boolean {
        return !getServerUrl().isNullOrEmpty() && !getUserToken().isNullOrEmpty()
    }

    fun clearSyncState() {
        prefs.edit().clear().apply()
        ApiClient.setAuthToken(null)
        SyncEncryptionHelper.clearAll()
    }

    // ========== Main Sync Operation ==========

    suspend fun performSync(): SyncResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting sync...")

        // Validation
        if (!isConfigured()) {
            return@withContext SyncResult.failure("Sync not configured. Please login first.")
        }

        if (!SyncEncryptionHelper.hasMasterKey()) {
            return@withContext SyncResult.failure("Encryption not configured. Please set up encryption password.")
        }

        try {
            // Initialize API client
            val serverUrl = getServerUrl()!!
            if (!ApiClient.isInitialized()) {
                ApiClient.initialize(serverUrl)
                ApiClient.setAuthToken(getUserToken())
            }

            var entriesSynced = 0
            var categoriesSynced = 0
            var conflictsDetected = 0

            // Step 1: Push local changes
            val pushResult = pushLocalChanges()
            entriesSynced += pushResult.entriesPushed
            categoriesSynced += pushResult.categoriesPushed
            conflictsDetected += pushResult.conflictsDetected

            // Step 2: Pull server changes
            val pullResult = pullServerChanges()
            entriesSynced += pullResult.entriesUpdated
            categoriesSynced += pullResult.categoriesUpdated

            // Step 3: Update last sync timestamp
            pullResult.serverTimestamp?.let { repository.setLastSyncTimestamp(it) }

            Log.d(TAG, "Sync completed successfully")
            SyncResult.success(entriesSynced, categoriesSynced, conflictsDetected)

        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            SyncResult.failure(e.message ?: "Unknown error")
        }
    }

    // ========== Push Changes to Server ==========

    private suspend fun pushLocalChanges(): PushResult {
        Log.d(TAG, "Pushing local changes...")

        // Get pending items from repository
        val pendingEntries = repository.getPendingItems(WriterSyncRepository.TYPE_ENTRY)
        val pendingCategories = repository.getPendingItems(WriterSyncRepository.TYPE_CATEGORY)

        if (pendingEntries.isEmpty() && pendingCategories.isEmpty()) {
            Log.d(TAG, "No pending changes to push")
            return PushResult(0, 0, 0)
        }

        // Convert to sync format with encryption
        val syncEntries = pendingEntries.map { item ->
            val encryptedData = SyncEncryptionHelper.encryptNoteData(
                title = item.data["title"] as? String ?: "",
                body = item.data["body"] as? String ?: "",
                isEncrypted = item.data["is_encrypted"] as? Boolean ?: false,
                categoryId = item.data["category_id"] as? String
            )

            SyncEntry(
                localId = item.localId,
                serverId = item.serverId,
                encryptedData = encryptedData,
                createdAt = item.createdAt,
                updatedAt = item.updatedAt,
                isDeleted = item.isDeleted
            )
        }

        val syncCategories = pendingCategories.map { item ->
            val encryptedData = SyncEncryptionHelper.encryptCategoryData(
                name = item.data["name"] as? String ?: ""
            )

            SyncCategory(
                localId = item.localId,
                serverId = item.serverId,
                encryptedData = encryptedData,
                createdAt = item.createdAt,
                updatedAt = item.updatedAt,
                isDeleted = item.isDeleted
            )
        }

        // Push to server
        val result = ApiClient.pushChanges(syncEntries, syncCategories)

        return if (result.isSuccess) {
            val response = result.getOrNull()!!
            handlePushResponse(response)
        } else {
            Log.e(TAG, "Failed to push changes: ${result.exceptionOrNull()?.message}")
            PushResult(0, 0, 0)
        }
    }

    private fun handlePushResponse(response: PushResponse): PushResult {
        var entriesPushed = 0
        var categoriesPushed = 0
        var conflictsDetected = 0

        // Process category responses
        for (result in response.categories) {
            when (result.status) {
                "success" -> {
                    result.localId?.let { localId ->
                        result.serverId?.let { serverId ->
                            repository.markAsSynced(localId, WriterSyncRepository.TYPE_CATEGORY, serverId)
                            categoriesPushed++
                        }
                    }
                }
                "conflict" -> {
                    result.localId?.let { localId ->
                        repository.markAsConflict(localId, WriterSyncRepository.TYPE_CATEGORY)
                        conflictsDetected++
                    }
                }
            }
        }

        // Process entry responses
        for (result in response.entries) {
            when (result.status) {
                "success" -> {
                    result.localId?.let { localId ->
                        result.serverId?.let { serverId ->
                            repository.markAsSynced(localId, WriterSyncRepository.TYPE_ENTRY, serverId)
                            entriesPushed++
                        }
                    }
                }
                "conflict" -> {
                    result.localId?.let { localId ->
                        repository.markAsConflict(localId, WriterSyncRepository.TYPE_ENTRY)
                        conflictsDetected++

                        // Store conflict for manual resolution
                        result.conflictData?.let { serverConflictData ->
                            storeConflict(localId, serverConflictData)
                        }
                    }
                }
            }
        }

        return PushResult(entriesPushed, categoriesPushed, conflictsDetected)
    }

    private fun storeConflict(localId: Long, serverConflictData: Map<String, Any>) {
        // Get local item
        val localItem = repository.getItemByServerId(
            serverConflictData["id"] as? String ?: return,
            WriterSyncRepository.TYPE_ENTRY
        ) ?: return

        val localData = mutableMapOf<String, Any>()
        localData["title"] = localItem.data["title"] ?: ""
        localData["body"] = localItem.data["body"] ?: ""
        localData["updated_at"] = localItem.updatedAt

        // Decrypt server data
        val encryptedData = serverConflictData["encrypted_data"] as? String
        val decryptedServerData = if (encryptedData != null) {
            try {
                val noteData = SyncEncryptionHelper.decryptNoteData(encryptedData)
                mutableMapOf<String, Any>().apply {
                    put("title", noteData["title"] ?: "")
                    put("body", noteData["body"] ?: "")
                    put("updated_at", serverConflictData["updated_at"] ?: "")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decrypt conflict data", e)
                mutableMapOf()
            }
        } else {
            mutableMapOf()
        }

        ConflictStorage.addConflict(localId, localData, decryptedServerData)
    }

    // ========== Pull Changes from Server ==========

    private suspend fun pullServerChanges(): PullResult {
        Log.d(TAG, "Pulling server changes...")

        val lastSync = repository.getLastSyncTimestamp()
        val result = ApiClient.getChanges(lastSync)

        return if (result.isSuccess) {
            val response = result.getOrNull()!!
            handleServerChanges(response)
            PullResult(
                entriesUpdated = response.entries.size,
                categoriesUpdated = response.categories.size,
                serverTimestamp = response.currentTimestamp
            )
        } else {
            Log.e(TAG, "Failed to pull changes: ${result.exceptionOrNull()?.message}")
            PullResult(0, 0, null)
        }
    }

    private fun handleServerChanges(response: ChangesResponse) {
        try {
            // Handle categories first
            for (category in response.categories) {
                if (category.deletedAt != null) {
                    // Category was deleted on server
                    repository.deleteItem(category.id, WriterSyncRepository.TYPE_CATEGORY)
                } else {
                    try {
                        // Decrypt category data
                        val decryptedData = SyncEncryptionHelper.decryptCategoryData(category.encryptedData)
                        val name = decryptedData["name"] as? String ?: ""

                        // Upsert via repository
                        val item = SyncRepository.SyncableItem(
                            localId = -1, // Will be assigned by repository
                            serverId = category.id,
                            itemType = WriterSyncRepository.TYPE_CATEGORY,
                            data = mapOf("name" to name),
                            createdAt = category.createdAt,
                            updatedAt = category.updatedAt,
                            isDeleted = false
                        )
                        repository.upsertFromServer(item)

                    } catch (e: BadPaddingException) {
                        throw Exception("Decryption failed. Your encryption password may be incorrect or data is corrupted.")
                    } catch (e: Exception) {
                        throw Exception("Failed to process category: ${e.message}")
                    }
                }
            }

            // Handle entries
            for (entry in response.entries) {
                if (entry.deletedAt != null) {
                    // Entry was deleted on server
                    repository.deleteItem(entry.id, WriterSyncRepository.TYPE_ENTRY)
                } else {
                    try {
                        // Decrypt note data
                        val decryptedData = SyncEncryptionHelper.decryptNoteData(entry.encryptedData)
                        val title = decryptedData["title"] as? String ?: ""
                        val body = decryptedData["body"] as? String ?: ""
                        val isEncrypted = decryptedData["is_encrypted"] as? Boolean ?: false
                        val categoryId = decryptedData["category_id"] as? String

                        // Upsert via repository
                        val item = SyncRepository.SyncableItem(
                            localId = -1, // Will be assigned by repository
                            serverId = entry.id,
                            itemType = WriterSyncRepository.TYPE_ENTRY,
                            data = mapOf(
                                "title" to title,
                                "body" to body,
                                "is_encrypted" to isEncrypted,
                                "category_id" to categoryId
                            ),
                            createdAt = entry.createdAt,
                            updatedAt = entry.updatedAt,
                            isDeleted = false
                        )
                        repository.upsertFromServer(item)

                    } catch (e: BadPaddingException) {
                        throw Exception("Decryption failed. Your encryption password may be incorrect or data is corrupted.")
                    } catch (e: Exception) {
                        throw Exception("Failed to process entry: ${e.message}")
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error handling server changes", e)
            throw e
        }
    }

    // ========== Data Classes ==========

    data class SyncResult(
        val success: Boolean,
        val error: String?,
        val entriesSynced: Int,
        val categoriesSynced: Int,
        val conflictsDetected: Int
    ) {
        companion object {
            fun success(entries: Int, categories: Int, conflicts: Int) = SyncResult(
                success = true,
                error = null,
                entriesSynced = entries,
                categoriesSynced = categories,
                conflictsDetected = conflicts
            )

            fun failure(error: String) = SyncResult(
                success = false,
                error = error,
                entriesSynced = 0,
                categoriesSynced = 0,
                conflictsDetected = 0
            )
        }
    }

    private data class PushResult(
        val entriesPushed: Int,
        val categoriesPushed: Int,
        val conflictsDetected: Int
    )

    private data class PullResult(
        val entriesUpdated: Int,
        val categoriesUpdated: Int,
        val serverTimestamp: String?
    )
}
