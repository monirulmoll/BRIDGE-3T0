package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CapturedItemDao {

    @Query("SELECT * FROM captured_items ORDER BY timestamp DESC")
    fun getAllItems(): Flow<List<CapturedItem>>

    @Query("SELECT * FROM captured_items WHERE type = :type ORDER BY timestamp DESC")
    fun getItemsByType(type: String): Flow<List<CapturedItem>>

    @Query("SELECT * FROM captured_items ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentItems(limit: Int): Flow<List<CapturedItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: CapturedItem): Long

    @Query("DELETE FROM captured_items WHERE id = :id")
    suspend fun deleteItemById(id: Long)

    @Query("DELETE FROM captured_items")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM captured_items")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM captured_items WHERE type = 'CHATGPT_CODE'")
    fun getGptCodeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM captured_items WHERE type = 'TERMUX_OUTPUT'")
    fun getTermuxOutputCount(): Flow<Int>
}
