package com.flawiddsouza.writer

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.flawiddsouza.writer.sync.ConflictStorage

class ConflictResolutionActivity : AppCompatActivity() {

    private lateinit var localVersionText: TextView
    private lateinit var serverVersionText: TextView
    private lateinit var localTimestampText: TextView
    private lateinit var serverTimestampText: TextView
    private lateinit var keepLocalButton: Button
    private lateinit var keepServerButton: Button
    private lateinit var mergeButton: Button

    private var noteId: Long = -1
    private var localVersion: Map<String, Any>? = null
    private var serverVersion: Map<String, Any>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conflict_resolution)

        // Get note ID from intent
        noteId = intent.getLongExtra("note_id", -1)
        if (noteId == -1L) {
            Toast.makeText(this, "Error: Invalid note ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Initialize views
        localVersionText = findViewById(R.id.local_version_text)
        serverVersionText = findViewById(R.id.server_version_text)
        localTimestampText = findViewById(R.id.local_timestamp)
        serverTimestampText = findViewById(R.id.server_timestamp)
        keepLocalButton = findViewById(R.id.keep_local_button)
        keepServerButton = findViewById(R.id.keep_server_button)
        mergeButton = findViewById(R.id.merge_button)

        // Initialize ConflictStorage
        ConflictStorage.initialize(this)

        // Load conflict data
        loadConflictData()

        // Set click listeners
        keepLocalButton.setOnClickListener { handleKeepLocal() }
        keepServerButton.setOnClickListener { handleKeepServer() }
        mergeButton.setOnClickListener { handleMerge() }
    }

    private fun loadConflictData() {
        val conflict = ConflictStorage.getConflict(noteId)

        if (conflict == null) {
            Toast.makeText(this, "Conflict not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        localVersion = conflict.localVersion
        serverVersion = conflict.serverVersion

        // Display local version
        val localTitle = localVersion?.get("title") as? String ?: ""
        val localBody = localVersion?.get("body") as? String ?: ""
        val localUpdated = localVersion?.get("updated_at") as? String ?: ""

        localVersionText.text = "$localTitle\n\n$localBody"
        localTimestampText.text = "Modified: $localUpdated"

        // Display server version
        val serverTitle = serverVersion?.get("title") as? String ?: ""
        val serverBody = serverVersion?.get("body") as? String ?: ""
        val serverUpdated = serverVersion?.get("updated_at") as? String ?: ""

        serverVersionText.text = "$serverTitle\n\n$serverBody"
        serverTimestampText.text = "Modified: $serverUpdated"

        // Set activity title
        title = "Resolve Conflict: $localTitle"
    }

    private fun handleKeepLocal() {
        val db = WriterDatabaseHandler.getInstance(this)

        // Update the entry to mark it as pending for re-sync
        // The local version is already in the database, we just need to:
        // 1. Mark it as pending (will be pushed in next sync)
        // 2. Clear the conflict status
        db.writableDatabase.execSQL(
            "UPDATE entries SET sync_status = 'pending' WHERE _id = ?",
            arrayOf(noteId.toString())
        )

        // Clear conflict
        ConflictStorage.resolveConflict(noteId)

        Toast.makeText(this, "Kept local version. Will sync to server.", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun handleKeepServer() {
        val db = WriterDatabaseHandler.getInstance(this)

        // Update local entry with server version
        val serverTitle = serverVersion?.get("title") as? String ?: ""
        val serverBody = serverVersion?.get("body") as? String ?: ""
        val serverUpdated = serverVersion?.get("updated_at") as? String ?: ""

        db.writableDatabase.execSQL(
            "UPDATE entries SET title = ?, body = ?, updated_at = ?, sync_status = 'synced' WHERE _id = ?",
            arrayOf(serverTitle, serverBody, serverUpdated, noteId.toString())
        )

        // Clear conflict
        ConflictStorage.resolveConflict(noteId)

        Toast.makeText(this, "Accepted server version.", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun handleMerge() {
        // Create merged content (concatenate both versions)
        val localTitle = localVersion?.get("title") as? String ?: ""
        val localBody = localVersion?.get("body") as? String ?: ""
        val serverTitle = serverVersion?.get("title") as? String ?: ""
        val serverBody = serverVersion?.get("body") as? String ?: ""

        val mergedTitle = localTitle
        val mergedBody = """
            === LOCAL VERSION ===
            $localBody

            === SERVER VERSION ===
            $serverBody

            === END OF CONFLICT ===

        """.trimIndent()

        // Update entry with merged content
        val db = WriterDatabaseHandler.getInstance(this)
        db.writableDatabase.execSQL(
            "UPDATE entries SET title = ?, body = ?, sync_status = 'pending', updated_at = datetime('now') WHERE _id = ?",
            arrayOf(mergedTitle, mergedBody, noteId.toString())
        )

        // Clear conflict
        ConflictStorage.resolveConflict(noteId)

        Toast.makeText(this, "Merged versions. Please review and edit.", Toast.LENGTH_LONG).show()
        finish()
    }
}
