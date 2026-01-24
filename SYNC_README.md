# Writer Sync Implementation

## Architecture - Isolated Design

Sync uses an **isolated architecture** enabling merge years later even if database schema changes.

### Components

```
SyncEngine (isolated core)
    ↓ uses interface
SyncRepository (contract)
    ↓ implemented by
WriterSyncRepository (adapter - ONLY file with DB knowledge)
    ↓ accesses
WriterDatabaseHandler
```

**Key files:**
- `SyncRepository.kt` - Interface (portable)
- `SyncEngine.kt` - Sync logic (portable, zero DB dependencies)
- `WriterSyncRepository.kt` - Adapter (app-specific, only file knowing DB schema)
- `SyncEncryptionHelper.kt` - Bitwarden-style encryption
- `ApiClient.kt` - Network layer

### Usage

```kotlin
// Create repository and engine
val repository = WriterSyncRepository(context)
val syncEngine = SyncEngine(context, repository)

// Configure
syncEngine.setServerUrl("https://sync.example.com")
syncEngine.setUserToken(token)

// Sync
val result = syncEngine.performSync()

// Get last sync (from repository, not engine)
val lastSync = repository.getLastSyncTimestamp()
```

### Why Isolated?

**Mergeable**: If schema changes years later, update only `WriterSyncRepository.kt`
- Old: `SELECT title, body FROM entries`
- New: `SELECT doc_title, doc_body FROM documents`
- SyncEngine unchanged!

**Removable**: Delete `sync/` package if you don't want sync

**Testable**: Mock repository, test without database

**Portable**: Copy to other apps, write new adapter

## Encryption - Bitwarden Style

### How It Works

1. Random 256-bit **master key** encrypts all data
2. User **password** → PBKDF2 (600k iterations) → **derived key**
3. Derived key encrypts master key
4. **Encrypted master key** stored on server
5. Any device with password can decrypt master key

### First Device Flow

1. User creates encryption password (12+ chars, strong requirements)
2. App generates random master key
3. Master key encrypted with password → uploaded to server
4. Master key saved locally (Android Keystore)
5. All notes encrypted with master key before sync

### Additional Devices

1. User enters same password
2. Downloads encrypted master key from server
3. Decrypts with password → saves locally
4. Can now decrypt all synced notes

### Security

- **Zero-knowledge**: Server stores only encrypted blobs
- **ChaCha20-Poly1305**: Data encryption
- **AES-GCM**: Master key encryption
- **PBKDF2**: 600k iterations (slow brute force)
- **Password requirements**: 12+ chars, uppercase, lowercase, number, special char

## Database Schema

Sync adds columns to existing tables:

```sql
-- Entries table
ALTER TABLE entries ADD COLUMN sync_status TEXT DEFAULT 'pending';
ALTER TABLE entries ADD COLUMN server_id TEXT;
ALTER TABLE entries ADD COLUMN last_synced_at TEXT;
ALTER TABLE entries ADD COLUMN is_deleted INTEGER DEFAULT 0;

-- Categories table
ALTER TABLE categories ADD COLUMN sync_status TEXT DEFAULT 'pending';
ALTER TABLE categories ADD COLUMN server_id TEXT;
ALTER TABLE categories ADD COLUMN last_synced_at TEXT;
ALTER TABLE categories ADD COLUMN is_deleted INTEGER DEFAULT 0;
```

## Server Endpoints

**Auth**
- `POST /auth/register` - Register
- `POST /auth/login` - Login
- `GET /auth/master-key` - Get encrypted master key
- `POST /auth/master-key` - Upload encrypted master key
- `POST /auth/change-master-key-password` - Change encryption password

**Sync**
- `GET /sync/changes?since=<timestamp>` - Pull changes
- `POST /sync/push` - Push local changes

## Testing

1. **First device**: Login → Create encryption password → Sync notes
2. **Second device**: Login → Unlock with same password → Verify notes decrypt
3. **Wrong password**: Try wrong password → Verify rejection and retry
4. **Multi-device**: Edit on both devices → Sync → Resolve conflicts

## Migration Example

Schema changed from `entries` to `documents`?

**Update only WriterSyncRepository.kt:**

```kotlin
// Old
override fun getPendingItems(itemType: String): List<SyncableItem> {
    val cursor = db.rawQuery(
        "SELECT _id, title, body FROM entries WHERE sync_status = 'pending'",
        null
    )
    // Convert to SyncableItem...
}

// New (schema changed!)
override fun getPendingItems(itemType: String): List<SyncableItem> {
    val cursor = db.rawQuery(
        "SELECT id, doc_title, doc_body FROM documents WHERE sync_pending = true",
        null
    )
    // Convert to SyncableItem... (same format!)
}
```

**SyncEngine.kt unchanged!** Works years later!

## Requirements

- Android API 28+ (ChaCha20-Poly1305)
- Server: Node.js/Bun + PostgreSQL (in `sync-server/`)

## Quick Start

**Backend:**
```bash
cd sync-server
bun install
bun db:migrate
bun dev  # http://localhost:3000
```

**Android:**
- Settings → Sync Settings
- Enter server URL, email, password
- Login/Register → Create encryption password
- Sync!

## Files Overview

**Portable (copy to other apps):**
- `SyncEngine.kt` - No DB dependencies
- `SyncRepository.kt` - Interface only
- `SyncEncryptionHelper.kt` - Standalone
- `ApiClient.kt` - Network only

**App-specific:**
- `WriterSyncRepository.kt` - Adapter (only file with DB access!)
- `SyncSettingsActivity.kt` - UI
- `SyncWorker.kt` - Background sync
