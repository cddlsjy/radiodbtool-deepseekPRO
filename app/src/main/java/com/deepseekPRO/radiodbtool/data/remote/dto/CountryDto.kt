package com.deepseekPRO.radiodbtool.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CountryDto(
    @SerializedName("name") val name: String,
    @SerializedName("stationcount") val stationcount: Int
)