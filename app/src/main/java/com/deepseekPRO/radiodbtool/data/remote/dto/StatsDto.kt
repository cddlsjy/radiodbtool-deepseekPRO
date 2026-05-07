package com.deepseekPRO.radiodbtool.data.remote.dto

import com.google.gson.annotations.SerializedName

data class StatsDto(
    @SerializedName("stations") val stations: Int
)