package com.flawiddsouza.writer.sync

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class ConflictMetadata(
    val noteId: Long,
    val localVersion: Map<String, Any>,
    val serverVersion: Map<String, Any>,
    val detectedAt: Long // Timestamp
)

object ConflictStorage {
    private const val PREFS_NAME = "writer_sync_conflicts"
    private const val KEY_CONFLICTS = "conflicts"

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun addConflict(noteId: Long, localVersion: Map<String, Any>, serverVersion: Map<String, Any>) {
        val conflicts = getConflicts().toMutableList()

        // Remove existing conflict for this note if any
        conflicts.removeAll { it.noteId == noteId }

        // Add new conflict
        val conflict = ConflictMetadata(
            noteId = noteId,
            localVersion = localVersion,
            serverVersion = serverVersion,
            detectedAt = System.currentTimeMillis()
        )
        conflicts.add(conflict)

        saveConflicts(conflicts)
    }

    fun getConflicts(): List<ConflictMetadata> {
        val json = prefs.getString(KEY_CONFLICTS, null) ?: return emptyList()
        val type = object : TypeToken<List<ConflictMetadata>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getConflict(noteId: Long): ConflictMetadata? {
        return getConflicts().find { it.noteId == noteId }
    }

    fun hasConflicts(): Boolean {
        return getConflicts().isNotEmpty()
    }

    fun getConflictCount(): Int {
        return getConflicts().size
    }

    fun resolveConflict(noteId: Long) {
        val conflicts = getConflicts().toMutableList()
        conflicts.removeAll { it.noteId == noteId }
        saveConflicts(conflicts)
    }

    fun clearAllConflicts() {
        prefs.edit().remove(KEY_CONFLICTS).apply()
    }

    private fun saveConflicts(conflicts: List<ConflictMetadata>) {
        val json = gson.toJson(conflicts)
        prefs.edit().putString(KEY_CONFLICTS, json).apply()
    }
}
