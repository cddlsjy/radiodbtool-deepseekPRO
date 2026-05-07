package com.deepseekPRO.radiodbtool.data.remote.api

import com.deepseekPRO.radiodbtool.data.remote.dto.CountryDto
import com.deepseekPRO.radiodbtool.data.remote.dto.LanguageDto
import com.deepseekPRO.radiodbtool.data.remote.dto.StatsDto
import com.deepseekPRO.radiodbtool.data.remote.dto.StationDto
import retrofit2.http.GET
import retrofit2.http.Query

interface RadioApiService {
    @GET("json/stats")
    suspend fun getStats(): StatsDto

    @GET("json/countries")
    suspend fun getCountries(): List<CountryDto>

    @GET("json/languages")
    suspend fun getLanguages(): List<LanguageDto>

    @GET("json/stations")
    suspend fun getStations(
        @Query("limit") limit: Int = 500,
        @Query("offset") offset: Int,
        @Query("country") country: String? = null,
        @Query("language") language: String? = null,
        @Query("name") name: String? = null
    ): List<StationDto>
}