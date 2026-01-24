package com.flawiddsouza.writer.sync

import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks which notes are currently being edited in memory.
 *
 * This prevents sync from overwriting notes while user is actively editing them.
 * Memory-based so it's automatically cleared on app restart/force close.
 */
object EditingTracker {
    private val editingEntryIds = ConcurrentHashMap.newKeySet<Long>()
    private val editingCategoryIds = ConcurrentHashMap.newKeySet<Long>()

    fun markEntryAsEditing(entryId: Long) {
        editingEntryIds.add(entryId)
    }

    fun markEntryAsNotEditing(entryId: Long) {
        editingEntryIds.remove(entryId)
    }

    fun isEntryBeingEdited(entryId: Long): Boolean {
        return editingEntryIds.contains(entryId)
    }

    fun markCategoryAsEditing(categoryId: Long) {
        editingCategoryIds.add(categoryId)
    }

    fun markCategoryAsNotEditing(categoryId: Long) {
        editingCategoryIds.remove(categoryId)
    }

    fun isCategoryBeingEdited(categoryId: Long): Boolean {
        return editingCategoryIds.contains(categoryId)
    }

    fun clearAll() {
        editingEntryIds.clear()
        editingCategoryIds.clear()
    }
}
