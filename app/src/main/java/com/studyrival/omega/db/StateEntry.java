package com.studyrival.omega.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "state")
public class StateEntry {
    @PrimaryKey
    @NonNull
    public String key;

    @androidx.room.ColumnInfo(name = "value")
    public String value;

    public StateEntry(@NonNull String key, String value) {
        this.key   = key;
        this.value = value;
    }
}
