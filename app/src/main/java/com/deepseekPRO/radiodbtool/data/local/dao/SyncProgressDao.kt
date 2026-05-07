package com.deepseekPRO.radiodbtool.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.deepseekPRO.radiodbtool.data.local.entity.SyncProgressEntity

@Dao
interface SyncProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: SyncProgressEntity)

    @Query("SELECT offset FROM sync_progress WHERE filter_key = :key")
    suspend fun getOffset(key: String): Int?

    @Query("DELETE FROM sync_progress WHERE filter_key = :key")
    suspend fun deleteProgress(key: String)
}