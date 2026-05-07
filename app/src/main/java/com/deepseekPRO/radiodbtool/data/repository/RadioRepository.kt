package com.deepseekPRO.radiodbtool.data.repository

import com.deepseekPRO.radiodbtool.data.local.RadioDatabase
import com.deepseekPRO.radiodbtool.data.local.entity.StationEntity
import com.deepseekPRO.radiodbtool.data.remote.ApiClient
import com.deepseekPRO.radiodbtool.data.remote.dto.StationDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigInteger
import java.security.MessageDigest

class RadioRepository(private val apiClient: ApiClient, private val db: RadioDatabase) {
    suspend fun fetchAvailableCountries(): List<String> {
        return withContext(Dispatchers.IO) {
            val countries = apiClient.apiService.getCountries()
            countries.map { it.name }.sorted()
        }
    }

    suspend fun fetchAvailableLanguages(): List<String> {
        return withContext(Dispatchers.IO) {
            val languages = apiClient.apiService.getLanguages()
            languages.map { it.name }.sorted()
        }
    }

    suspend fun fetchStats(): Int {
        return withContext(Dispatchers.IO) {
            apiClient.apiService.getStats().stations
        }
    }

    suspend fun fetchStationPage(
        offset: Int,
        limit: Int = 500,
        country: String? = null,
        language: String? = null,
        name: String? = null
    ): List<StationEntity> {
        return withContext(Dispatchers.IO) {
            val dtos = apiClient.apiService.getStations(
                limit = limit,
                offset = offset,
                country = if (country.isNullOrEmpty()) null else country,
                language = if (language.isNullOrEmpty()) null else language,
                name = if (name.isNullOrEmpty()) null else name
            )
            dtos.map { it.toEntity() }
        }
    }

    suspend fun insertStations(stations: List<StationEntity>) {
        withContext(Dispatchers.IO) {
            db.stationDao().insertStations(stations)
        }
    }

    suspend fun getFilteredStationsForExport(country: String, language: String, keyword: String): List<StationEntity> {
        return withContext(Dispatchers.IO) {
            db.stationDao().getFilteredStationsForExport(country, language, keyword)
        }
    }

    private fun StationDto.toEntity(): StationEntity {
        return StationEntity(
            stationuuid = stationuuid,
            name = name,
            url = url,
            homepage = homepage,
            favicon = favicon,
            country = country,
            countrycode = countrycode,
            state = state,
            language = language,
            tags = tags,
            codec = codec,
            bitrate = bitrate,
            lastcheckok = lastcheckok,
            lastchangetime = lastchangetime,
            clickcount = clickcount,
            clicktrend = clicktrend,
            votes = votes
        )
    }

    companion object {
        fun md5(input: String): String {
            val md = java.security.MessageDigest.getInstance("MD5")
            return BigInteger(1, md.digest(input.toByteArray())).toString(16).padStart(32, '0')
        }
    }
}