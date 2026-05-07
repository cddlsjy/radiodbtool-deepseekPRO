package com.deepseekPRO.radiodbtool.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_progress")
data class SyncProgressEntity(
    @PrimaryKey val filter_key: String,
    val offset: Int
)