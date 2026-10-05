package com.example.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [CapturedItem::class], version = 1, exportSchema = false)
abstract class BridgeDatabase : RoomDatabase() {
    abstract fun capturedItemDao(): CapturedItemDao
}
