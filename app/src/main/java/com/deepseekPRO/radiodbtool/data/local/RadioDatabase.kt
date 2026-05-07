package com.deepseekPRO.radiodbtool.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.deepseekPRO.radiodbtool.data.local.dao.StationDao
import com.deepseekPRO.radiodbtool.data.local.dao.SyncProgressDao
import com.deepseekPRO.radiodbtool.data.local.entity.StationEntity
import com.deepseekPRO.radiodbtool.data.local.entity.SyncProgressEntity

@Database(
    entities = [StationEntity::class, SyncProgressEntity::class],
    version = 1,
    exportSchema = false
)
abstract class RadioDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao
    abstract fun syncProgressDao(): SyncProgressDao

    companion object {
        @Volatile
        private var INSTANCE: RadioDatabase? = null

        fun getInstance(context: Context): RadioDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RadioDatabase::class.java,
                    "radio_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}