package com.deepseekPRO.radiodbtool.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.deepseekPRO.radiodbtool.data.local.entity.StationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStations(stations: List<StationEntity>)

    @Query("""
        SELECT * FROM stations
        WHERE (:country = '' OR country = :country)
        AND (:language = '' OR language = :language)
        AND (:keyword = '' OR name LIKE '%' || :keyword || '%' OR tags LIKE '%' || :keyword || '%')
        LIMIT 20
    """)
    fun getFilteredStations(country: String, language: String, keyword: String): Flow<List<StationEntity>>

    @Query("""
        SELECT * FROM stations
        WHERE (:country = '' OR country = :country)
        AND (:language = '' OR language = :language)
        AND (:keyword = '' OR name LIKE '%' || :keyword || '%' OR tags LIKE '%' || :keyword || '%')
    """)
    suspend fun getFilteredStationsForExport(country: String, language: String, keyword: String): List<StationEntity>

    @Query("SELECT DISTINCT country FROM stations WHERE country != '' ORDER BY country")
    fun getAllCountries(): Flow<List<String>>

    @Query("SELECT DISTINCT language FROM stations WHERE language != '' ORDER BY language")
    fun getAllLanguages(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM stations")
    suspend fun getStationCount(): Int

    @Query("SELECT * FROM stations LIMIT :limit OFFSET :offset")
    suspend fun getStationsPage(limit: Int, offset: Int): List<StationEntity>
}