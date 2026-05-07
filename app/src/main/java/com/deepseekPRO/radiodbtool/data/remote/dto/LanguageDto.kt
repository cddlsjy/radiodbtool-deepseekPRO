package com.deepseekPRO.radiodbtool.data.remote.dto

import com.google.gson.annotations.SerializedName

data class LanguageDto(
    @SerializedName("name") val name: String,
    @SerializedName("stationcount") val stationcount: Int
)