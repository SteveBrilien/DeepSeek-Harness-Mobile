package com.dsh.mobile;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface CacheDao {
    @Query("SELECT * FROM mobile_cache WHERE id = :id LIMIT 1")
    CacheRecord read(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void write(CacheRecord record);

    @Query("DELETE FROM mobile_cache")
    void clear();
}
