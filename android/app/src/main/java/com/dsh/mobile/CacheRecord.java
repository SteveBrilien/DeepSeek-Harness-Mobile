package com.dsh.mobile;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "mobile_cache")
public final class CacheRecord {
    @PrimaryKey
    @NonNull
    public final String id;
    @NonNull
    public final String encryptedPayload;
    public final long updatedAt;

    public CacheRecord(@NonNull String id, @NonNull String encryptedPayload, long updatedAt) {
        this.id = id;
        this.encryptedPayload = encryptedPayload;
        this.updatedAt = updatedAt;
    }
}
