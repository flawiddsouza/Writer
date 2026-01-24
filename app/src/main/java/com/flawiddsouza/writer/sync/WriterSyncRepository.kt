package com.flawiddsouza.writer.sync

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.flawiddsouza.writer.WriterDatabaseHandler
import java.text.SimpleDateFormat
import java.util.*

/**
 * Implementation of SyncRepository for Writer app.
 *
 * This is the ONLY file that knows about Writer's database schema.
 * Changing this file adapts sync to schema changes without touching SyncEngine.
 *
 * Acts as an adapter/bridge between sync system and main app database.
 */
class WriterSyncRepository(context: Context) : SyncRepository {

    private val dbHandler = WriterDatabaseHandler.getInstance(context)
    private val prefs: SharedPreferences = context.getSharedPreferences("writer_sync_state", Context.MODE_PRIVATE)

    private val sqliteDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val iso8601Format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    companion object {
        const val TYPE_ENTRY = "entry"
        const val TYPE_CATEGORY = "category"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
        private const val DEFAULT_LAST_SYNC = "2000-01-01T00:00:00Z"
    }

    override fun getPendingItems(itemType: String): List<SyncRepository.SyncableItem> {
        val db = dbHandler.readableDatabase
        val items = mutableListOf<SyncRepository.SyncableItem>()

        when (itemType) {
            TYPE_ENTRY -> {
                val cursor = db.rawQuery(
                    "SELECT _id, title, body, is_encrypted, category_id, created_at, updated_at, server_id, is_deleted FROM entries WHERE sync_status = 'pending'",
                    null
                )
                try {
                    while (cursor.moveToNext()) {
                        items.add(
                            SyncRepository.SyncableItem(
                                localId = cursor.getLong(0),
                                serverId = cursor.getString(7),
                                itemType = TYPE_ENTRY,
                                data = mapOf(
                                    "title" to (cursor.getString(1) ?: ""),
                                    "body" to (cursor.getString(2) ?: ""),
                                    "is_encrypted" to (cursor.getInt(3) == 1),
                                    "category_id" to cursor.getString(4)
                                ),
                                createdAt = convertToIso8601(cursor.getString(5)),
                                updatedAt = convertToIso8601(cursor.getString(6)),
                                isDeleted = cursor.getInt(8) == 1
                            )
                        )
                    }
                } finally {
                    cursor.close()
                }
            }
            TYPE_CATEGORY -> {
                val cursor = db.rawQuery(
                    "SELECT _id, name, created_at, updated_at, server_id, is_deleted FROM categories WHERE sync_status = 'pending'",
                    null
                )
                try {
                    while (cursor.moveToNext()) {
                        items.add(
                            SyncRepository.SyncableItem(
                                localId = cursor.getLong(0),
                                serverId = cursor.getString(4),
                                itemType = TYPE_CATEGORY,
                                data = mapOf("name" to cursor.getString(1)),
                                createdAt = convertToIso8601(cursor.getString(2)),
                                updatedAt = convertToIso8601(cursor.getString(3)),
                                isDeleted = cursor.getInt(5) == 1
                            )
                        )
                    }
                } finally {
                    cursor.close()
                }
            }
        }

        return items
    }

    override fun markAsSynced(localId: Long, itemType: String, serverId: String) {
        val db = dbHandler.writableDatabase
        val table = if (itemType == TYPE_ENTRY) "entries" else "categories"

        db.execSQL(
            "UPDATE $table SET server_id = ?, sync_status = 'synced', last_synced_at = datetime('now') WHERE _id = ?",
            arrayOf(serverId, localId)
        )
    }

    override fun markAsConflict(localId: Long, itemType: String) {
        val db = dbHandler.writableDatabase
        val table = if (itemType == TYPE_ENTRY) "entries" else "categories"

        db.execSQL(
            "UPDATE $table SET sync_status = 'conflict' WHERE _id = ?",
            arrayOf(localId)
        )
    }

    override fun upsertFromServer(item: SyncRepository.SyncableItem): Long {
        val db = dbHandler.writableDatabase
        var localId: Long = -1

        db.beginTransaction()
        try {
            when (item.itemType) {
                TYPE_ENTRY -> {
                    localId = upsertEntry(db, item)
                }
                TYPE_CATEGORY -> {
                    localId = upsertCategory(db, item)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        return localId
    }

    private fun upsertEntry(db: SQLiteDatabase, item: SyncRepository.SyncableItem): Long {
        if (item.isDeleted) {
            // Mark as deleted
            db.execSQL(
                "UPDATE entries SET is_deleted = 1, sync_status = 'synced' WHERE server_id = ?",
                arrayOf(item.serverId)
            )
            return -1
        }

        val title = item.data["title"] as? String ?: ""
        val body = item.data["body"] as? String ?: ""
        val isEncrypted = item.data["is_encrypted"] as? Boolean ?: false
        val categoryId = item.data["category_id"] as? String

        // Check if exists
        val cursor = db.rawQuery(
            "SELECT _id FROM entries WHERE server_id = ?",
            arrayOf(item.serverId)
        )

        try {
            val localId: Long
            if (cursor.moveToFirst()) {
                // Update existing - but only if server version is actually newer
                localId = cursor.getLong(0)

                // Get local sync_status and updated_at
                val localInfoCursor = db.rawQuery(
                    "SELECT sync_status, updated_at FROM entries WHERE server_id = ?",
                    arrayOf(item.serverId)
                )
                try {
                    if (localInfoCursor.moveToFirst()) {
                        val syncStatus = localInfoCursor.getString(0)
                        val localUpdatedAt = localInfoCursor.getString(1)

                        // Check if note is being edited in memory (protects unsaved changes)
                        if (EditingTracker.isEntryBeingEdited(localId)) {
                            Log.d("WriterSyncRepository", "Skipping server update for entry ${item.serverId} - currently being edited")
                            return localId
                        }

                        // Skip update if user has pending local changes
                        if (syncStatus == "pending") {
                            Log.d("WriterSyncRepository", "Skipping server update for entry ${item.serverId} - has pending local changes")
                            return localId
                        }

                        // Compare timestamps - only update if server is actually newer
                        // Convert both to ISO8601 for comparison
                        val localUpdatedAtIso = convertToIso8601(localUpdatedAt)
                        val serverUpdatedAtIso = item.updatedAt

                        if (localUpdatedAtIso >= serverUpdatedAtIso) {
                            Log.d("WriterSyncRepository", "Skipping server update for entry ${item.serverId} - local version is same or newer (local: $localUpdatedAtIso, server: $serverUpdatedAtIso)")
                            return localId
                        }
                    }
                } finally {
                    localInfoCursor.close()
                }

                db.execSQL(
                    "UPDATE entries SET title = ?, body = ?, is_encrypted = ?, category_id = ?, updated_at = ?, sync_status = 'synced', last_synced_at = datetime('now') WHERE server_id = ?",
                    arrayOf(title, body, if (isEncrypted) 1 else 0, categoryId, convertFromIso8601(item.updatedAt), item.serverId)
                )
                return localId
            } else {
                // Insert new
                db.execSQL(
                    "INSERT INTO entries (title, body, is_encrypted, category_id, created_at, updated_at, server_id, sync_status, last_synced_at) VALUES (?, ?, ?, ?, ?, ?, ?, 'synced', datetime('now'))",
                    arrayOf(title, body, if (isEncrypted) 1 else 0, categoryId, convertFromIso8601(item.createdAt), convertFromIso8601(item.updatedAt), item.serverId)
                )

                // Get the inserted ID
                val idCursor = db.rawQuery("SELECT last_insert_rowid()", null)
                try {
                    localId = if (idCursor.moveToFirst()) idCursor.getLong(0) else -1
                } finally {
                    idCursor.close()
                }
                return localId
            }
        } finally {
            cursor.close()
        }
    }

    private fun upsertCategory(db: SQLiteDatabase, item: SyncRepository.SyncableItem): Long {
        if (item.isDeleted) {
            // Mark as deleted
            db.execSQL(
                "UPDATE categories SET is_deleted = 1, sync_status = 'synced' WHERE server_id = ?",
                arrayOf(item.serverId)
            )
            return -1
        }

        val name = item.data["name"] as? String ?: ""

        // Check if exists
        val cursor = db.rawQuery(
            "SELECT _id FROM categories WHERE server_id = ?",
            arrayOf(item.serverId)
        )

        try {
            val localId: Long
            if (cursor.moveToFirst()) {
                // Update existing - but only if server version is actually newer
                localId = cursor.getLong(0)

                // Get local sync_status and updated_at
                val localInfoCursor = db.rawQuery(
                    "SELECT sync_status, updated_at FROM categories WHERE server_id = ?",
                    arrayOf(item.serverId)
                )
                try {
                    if (localInfoCursor.moveToFirst()) {
                        val syncStatus = localInfoCursor.getString(0)
                        val localUpdatedAt = localInfoCursor.getString(1)

                        // Check if category is being edited in memory (protects unsaved changes)
                        if (EditingTracker.isCategoryBeingEdited(localId)) {
                            Log.d("WriterSyncRepository", "Skipping server update for category ${item.serverId} - currently being edited")
                            return localId
                        }

                        // Skip update if user has pending local changes
                        if (syncStatus == "pending") {
                            Log.d("WriterSyncRepository", "Skipping server update for category ${item.serverId} - has pending local changes")
                            return localId
                        }

                        // Compare timestamps - only update if server is actually newer
                        val localUpdatedAtIso = convertToIso8601(localUpdatedAt)
                        val serverUpdatedAtIso = item.updatedAt

                        if (localUpdatedAtIso >= serverUpdatedAtIso) {
                            Log.d("WriterSyncRepository", "Skipping server update for category ${item.serverId} - local version is same or newer (local: $localUpdatedAtIso, server: $serverUpdatedAtIso)")
                            return localId
                        }
                    }
                } finally {
                    localInfoCursor.close()
                }

                db.execSQL(
                    "UPDATE categories SET name = ?, updated_at = ?, sync_status = 'synced', last_synced_at = datetime('now') WHERE server_id = ?",
                    arrayOf(name, convertFromIso8601(item.updatedAt), item.serverId)
                )
                return localId
            } else {
                // Insert new
                db.execSQL(
                    "INSERT INTO categories (name, created_at, updated_at, server_id, sync_status, last_synced_at) VALUES (?, ?, ?, ?, 'synced', datetime('now'))",
                    arrayOf(name, convertFromIso8601(item.createdAt), convertFromIso8601(item.updatedAt), item.serverId)
                )

                // Get the inserted ID
                val idCursor = db.rawQuery("SELECT last_insert_rowid()", null)
                try {
                    localId = if (idCursor.moveToFirst()) idCursor.getLong(0) else -1
                } finally {
                    idCursor.close()
                }
                return localId
            }
        } finally {
            cursor.close()
        }
    }

    override fun getLastSyncTimestamp(): String {
        return prefs.getString(KEY_LAST_SYNC, DEFAULT_LAST_SYNC) ?: DEFAULT_LAST_SYNC
    }

    override fun setLastSyncTimestamp(timestamp: String) {
        prefs.edit().putString(KEY_LAST_SYNC, timestamp).apply()
    }

    override fun getItemByServerId(serverId: String, itemType: String): SyncRepository.SyncableItem? {
        val db = dbHandler.readableDatabase

        when (itemType) {
            TYPE_ENTRY -> {
                val cursor = db.rawQuery(
                    "SELECT _id, title, body, is_encrypted, category_id, created_at, updated_at, is_deleted FROM entries WHERE server_id = ?",
                    arrayOf(serverId)
                )
                try {
                    if (cursor.moveToFirst()) {
                        return SyncRepository.SyncableItem(
                            localId = cursor.getLong(0),
                            serverId = serverId,
                            itemType = TYPE_ENTRY,
                            data = mapOf(
                                "title" to (cursor.getString(1) ?: ""),
                                "body" to (cursor.getString(2) ?: ""),
                                "is_encrypted" to (cursor.getInt(3) == 1),
                                "category_id" to cursor.getString(4)
                            ),
                            createdAt = convertToIso8601(cursor.getString(5)),
                            updatedAt = convertToIso8601(cursor.getString(6)),
                            isDeleted = cursor.getInt(7) == 1
                        )
                    }
                } finally {
                    cursor.close()
                }
            }
            TYPE_CATEGORY -> {
                val cursor = db.rawQuery(
                    "SELECT _id, name, created_at, updated_at, is_deleted FROM categories WHERE server_id = ?",
                    arrayOf(serverId)
                )
                try {
                    if (cursor.moveToFirst()) {
                        return SyncRepository.SyncableItem(
                            localId = cursor.getLong(0),
                            serverId = serverId,
                            itemType = TYPE_CATEGORY,
                            data = mapOf("name" to cursor.getString(1)),
                            createdAt = convertToIso8601(cursor.getString(2)),
                            updatedAt = convertToIso8601(cursor.getString(3)),
                            isDeleted = cursor.getInt(4) == 1
                        )
                    }
                } finally {
                    cursor.close()
                }
            }
        }

        return null
    }

    override fun deleteItem(serverId: String, itemType: String) {
        val db = dbHandler.writableDatabase
        val table = if (itemType == TYPE_ENTRY) "entries" else "categories"

        db.execSQL(
            "UPDATE $table SET is_deleted = 1, sync_status = 'synced' WHERE server_id = ?",
            arrayOf(serverId)
        )
    }

    override fun getAllItems(itemType: String): List<SyncRepository.SyncableItem> {
        val db = dbHandler.readableDatabase
        val items = mutableListOf<SyncRepository.SyncableItem>()

        when (itemType) {
            TYPE_ENTRY -> {
                val cursor = db.rawQuery(
                    "SELECT _id, title, body, is_encrypted, category_id, created_at, updated_at, server_id, is_deleted FROM entries",
                    null
                )
                try {
                    while (cursor.moveToNext()) {
                        items.add(
                            SyncRepository.SyncableItem(
                                localId = cursor.getLong(0),
                                serverId = cursor.getString(7),
                                itemType = TYPE_ENTRY,
                                data = mapOf(
                                    "title" to (cursor.getString(1) ?: ""),
                                    "body" to (cursor.getString(2) ?: ""),
                                    "is_encrypted" to (cursor.getInt(3) == 1),
                                    "category_id" to cursor.getString(4)
                                ),
                                createdAt = convertToIso8601(cursor.getString(5)),
                                updatedAt = convertToIso8601(cursor.getString(6)),
                                isDeleted = cursor.getInt(8) == 1
                            )
                        )
                    }
                } finally {
                    cursor.close()
                }
            }
            TYPE_CATEGORY -> {
                val cursor = db.rawQuery(
                    "SELECT _id, name, created_at, updated_at, server_id, is_deleted FROM categories",
                    null
                )
                try {
                    while (cursor.moveToNext()) {
                        items.add(
                            SyncRepository.SyncableItem(
                                localId = cursor.getLong(0),
                                serverId = cursor.getString(4),
                                itemType = TYPE_CATEGORY,
                                data = mapOf("name" to cursor.getString(1)),
                                createdAt = convertToIso8601(cursor.getString(2)),
                                updatedAt = convertToIso8601(cursor.getString(3)),
                                isDeleted = cursor.getInt(5) == 1
                            )
                        )
                    }
                } finally {
                    cursor.close()
                }
            }
        }

        return items
    }

    private fun convertToIso8601(sqliteTimestamp: String): String {
        return try {
            val date = sqliteDateFormat.parse(sqliteTimestamp)
            iso8601Format.format(date!!)
        } catch (e: Exception) {
            sqliteTimestamp
        }
    }

    private fun convertFromIso8601(iso8601: String): String {
        return try {
            val date = iso8601Format.parse(iso8601)
            sqliteDateFormat.format(date!!)
        } catch (e: Exception) {
            iso8601
        }
    }
}
