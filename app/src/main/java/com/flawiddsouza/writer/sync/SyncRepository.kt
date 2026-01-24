package com.flawiddsouza.writer.sync

/**
 * Isolation layer between sync system and main app database.
 *
 * This interface defines what sync needs from the app WITHOUT directly accessing
 * the database or depending on app-specific classes.
 *
 * Benefits:
 * - Main app database can evolve independently
 * - Sync can be added/removed without modifying existing tables
 * - Easy to test sync logic in isolation
 * - Can merge sync code years later without conflicts
 */
interface SyncRepository {

    /**
     * Generic item that can be synced (note, category, etc.)
     */
    data class SyncableItem(
        val localId: Long,
        val serverId: String?,
        val itemType: String, // "entry" or "category"
        val data: Map<String, Any?>, // Flexible data structure
        val createdAt: String,
        val updatedAt: String,
        val isDeleted: Boolean
    )

    /**
     * Sync status for an item
     */
    enum class SyncStatus {
        PENDING,   // Local changes not synced
        SYNCED,    // In sync with server
        CONFLICT   // Conflict detected
    }

    /**
     * Get all items that need to be synced to server (status = PENDING)
     */
    fun getPendingItems(itemType: String): List<SyncableItem>

    /**
     * Mark item as synced and save server ID
     */
    fun markAsSynced(localId: Long, itemType: String, serverId: String)

    /**
     * Mark item as having conflict
     */
    fun markAsConflict(localId: Long, itemType: String)

    /**
     * Update or insert item from server
     * Returns: local ID of updated/inserted item
     */
    fun upsertFromServer(item: SyncableItem): Long

    /**
     * Get last sync timestamp
     */
    fun getLastSyncTimestamp(): String

    /**
     * Update last sync timestamp
     */
    fun setLastSyncTimestamp(timestamp: String)

    /**
     * Get item by server ID
     */
    fun getItemByServerId(serverId: String, itemType: String): SyncableItem?

    /**
     * Delete item (mark as deleted)
     */
    fun deleteItem(serverId: String, itemType: String)

    /**
     * Get all items of a type (for initial sync)
     */
    fun getAllItems(itemType: String): List<SyncableItem>
}
