package com.dsh.mobile;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {CacheRecord.class}, version = 1, exportSchema = true)
public abstract class MobileDatabase extends RoomDatabase {
    private static volatile MobileDatabase instance;

    public abstract CacheDao cache();

    public static MobileDatabase get(Context context) {
        MobileDatabase current = instance;
        if (current != null) return current;
        synchronized (MobileDatabase.class) {
            current = instance;
            if (current == null) {
                current = Room.databaseBuilder(
                    context.getApplicationContext(),
                    MobileDatabase.class,
                    "dsh-mobile-cache.db"
                ).build();
                instance = current;
            }
            return current;
        }
    }
}
