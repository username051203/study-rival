package com.studyrival.omega.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface StateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(StateEntry entry);

    @Query("SELECT value FROM state WHERE `key` = :key LIMIT 1")
    String get(String key);

    @Query("DELETE FROM state WHERE `key` = :key")
    void delete(String key);

    @Query("DELETE FROM state")
    void clear();
}
