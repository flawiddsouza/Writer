package com.flawiddsouza.writer;

import java.util.Date;

public class Category {
    public String name;
    public Date createdAt;
    public Date updatedAt;

    // Sync-related fields
    public String syncStatus;  // 'pending', 'synced', 'conflict'
    public Date lastSyncedAt;
    public String serverId;    // UUID from server
    public boolean isDeleted;
}
