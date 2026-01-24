# Schema-Agnostic Sync Server Refactor

## Overview

The sync server has been refactored to use a **schema-agnostic blob storage approach**, making it reusable for any app with different data structures (not just Writer).

## Key Changes

### 1. Server-Side Changes (sync-server/)

**Database Schema** (`src/db/schema.ts`):
- ✅ Replaced `notes` and `categories` tables with generic `syncItems` table
- ✅ New schema:
  ```typescript
  syncItems {
    id: UUID (primary key)
    userId: UUID (foreign key to users)
    itemType: TEXT (e.g., "note", "category", "todo", "task")
    encryptedData: TEXT (encrypted JSON blob)
    createdAt: TIMESTAMP
    updatedAt: TIMESTAMP
    deletedAt: TIMESTAMP (nullable, for soft deletes)
  }
  ```

**API Routes** (`src/routes/sync.ts`):
- ✅ Updated to use generic `syncItems` table
- ✅ Single `processItem()` function handles all item types
- ✅ API still returns separate `entries` and `categories` arrays for backward compatibility

**Branding** (`src/index.ts`):
- ✅ Removed "Writer" from console logs

### 2. Client-Side Changes (Android)

**SyncEncryptionHelper.kt**:
- ✅ Added `encryptNoteData()` - encrypts entire note as JSON blob
- ✅ Added `decryptNoteData()` - decrypts JSON blob to map
- ✅ Added `encryptCategoryData()` - encrypts category as JSON blob
- ✅ Added `decryptCategoryData()` - decrypts category blob
- ✅ Includes simple JSON parser/serializer (no Gson dependency in helper)

**Data Classes** (`ApiClient.kt`):
- ✅ `SyncEntry` now has `encrypted_data` field instead of `title`, `body`, `isEncrypted`, `categoryId`
- ✅ `SyncCategory` now has `encrypted_data` field instead of `name`
- ✅ `ServerEntry` and `ServerCategory` updated to match

**SyncEngine.kt**:
- ✅ `getPendingEntries()` - encrypts entire note as blob before sending
- ✅ `getPendingCategories()` - encrypts entire category as blob
- ✅ `handleServerChanges()` - decrypts blobs when receiving from server
- ✅ Conflict handling updated to work with encrypted blobs

## Privacy Improvements

### Before (Metadata Leakage):
```
Server knows:
- Which notes have "extra encryption" (isEncrypted flag)
- Which category each note belongs to (categoryId visible)
- Note exists, category exists (table structure)
```

### After (Zero-Knowledge):
```
Server knows:
- User X has N items of type "note" and M items of type "category"
- When items were modified (timestamps for conflict resolution)
- NOTHING else (all data inside encrypted blob)
```

**What's now encrypted:**
- ✅ Note title
- ✅ Note body
- ✅ isEncrypted flag (whether note has additional password encryption)
- ✅ categoryId (which category a note belongs to)
- ✅ Category name

**What remains plaintext (required for sync):**
- itemType ("note" vs "category") - needed for client to parse
- Timestamps (createdAt, updatedAt, deletedAt) - needed for conflict resolution
- userId - needed for data isolation

## Reusability

### Using This Server for Other Apps

The server is now **completely generic**. To use with a different app:

1. **No server changes needed** - it stores encrypted blobs
2. **Client changes only:**
   - Define your data structure (e.g., todo app: `title`, `description`, `dueDate`, `priority`)
   - Encrypt as JSON blob: `{"title":"...", "description":"...", "dueDate":"...", "priority":1}`
   - Set `itemType` appropriately: `"todo"`, `"project"`, `"tag"`, etc.

### Example: Todo App

```kotlin
// Todo app client
fun encryptTodoData(
    title: String,
    description: String,
    dueDate: String,
    priority: Int
): String {
    val jsonData = """
    {
        "title":"${escapeJson(title)}",
        "description":"${escapeJson(description)}",
        "due_date":"${escapeJson(dueDate)}",
        "priority":$priority
    }
    """.trimIndent()
    return encrypt(jsonData)
}
```

Server doesn't care - it just stores the encrypted blob!

## Migration Required

### Server Database Migration

You'll need to run a migration to convert from old schema to new:

```sql
-- Create new table
CREATE TABLE sync_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    item_type TEXT NOT NULL,
    encrypted_data TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP
);

-- Migrate existing notes
INSERT INTO sync_items (id, user_id, item_type, encrypted_data, created_at, updated_at, deleted_at)
SELECT
    id,
    user_id,
    'note' as item_type,
    json_build_object(
        'title', title,
        'body', body,
        'is_encrypted', is_encrypted,
        'category_id', category_id
    )::text as encrypted_data,
    created_at,
    updated_at,
    deleted_at
FROM notes;

-- Migrate existing categories
INSERT INTO sync_items (id, user_id, item_type, encrypted_data, created_at, updated_at, deleted_at)
SELECT
    id,
    user_id,
    'category' as item_type,
    json_build_object('name', name)::text as encrypted_data,
    created_at,
    updated_at,
    deleted_at
FROM categories;

-- Drop old tables (AFTER verifying migration worked)
-- DROP TABLE notes;
-- DROP TABLE categories;
```

**Note:** This migration assumes your existing data is already encrypted. If you're migrating from a fresh deployment, you can skip this.

### Client Compatibility

The client changes are **backward compatible during development** - old and new clients can coexist during testing. However, once you deploy the new server schema, all clients must be updated.

## Drawbacks (Minimal)

1. **Debugging:** Server database now shows encrypted blobs instead of readable fields
   - **Mitigation:** Check client logs for plaintext data during debugging

2. **Payload Size:** ~50-100 extra bytes per item (JSON field names included in encryption)
   - **Impact:** Negligible for typical note sizes (1-10KB)

3. **No Server-Side Features:** Server can never add features that require knowing data structure
   - **Impact:** This is the point! Zero-knowledge means true privacy.

## Benefits

✅ **Reusable** - One server codebase for all your note-taking apps
✅ **Privacy** - True zero-knowledge, server sees only encrypted blobs
✅ **Flexible** - Add new fields without server changes
✅ **Secure** - Eliminates metadata leakage (isEncrypted flag, categoryId relationships)
✅ **Simple** - Generic code is easier to maintain than app-specific logic

## Testing Checklist

After deployment, test:

- [ ] New note sync (create note on device A, appears on device B)
- [ ] Note update sync (edit on A, updates on B)
- [ ] Note deletion sync (delete on A, deletes on B)
- [ ] Category sync (same flows as notes)
- [ ] Conflict resolution (edit same note offline on both devices, then sync)
- [ ] New user registration and first sync
- [ ] Multi-device sync with key import/export

## Deployment Steps

1. **Backup your production database** (critical!)
2. Run database migration script (see above)
3. Verify migration:
   ```sql
   SELECT COUNT(*) FROM sync_items WHERE item_type = 'note';
   SELECT COUNT(*) FROM sync_items WHERE item_type = 'category';
   ```
4. Deploy new server code
5. Deploy new Android app to users
6. Monitor logs for encryption/decryption errors
7. After confirming everything works, drop old tables

## Future Extensibility

To add support for new item types (e.g., tags, attachments):

**Server:** No changes needed!

**Client:**
1. Add encryption/decryption methods for new type
2. Add itemType string ("tag", "attachment", etc.)
3. Send with appropriate encrypted_data blob

That's it! The server will store and sync any item type.
