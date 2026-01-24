package com.flawiddsouza.writer;

import java.util.Date;

public class Entry {
    public String title;
    public String body;
    public Long categoryId;
    public Date createdAt;
    public Date updatedAt;
    public boolean isEncrypted;

    // Sync-related fields
    public String syncStatus;  // 'pending', 'synced', 'conflict'
    public Date lastSyncedAt;
    public String serverId;    // UUID from server
    public boolean isDeleted;
}
