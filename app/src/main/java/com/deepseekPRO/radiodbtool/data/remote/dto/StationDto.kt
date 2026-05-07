package com.deepseekPRO.radiodbtool.data.remote.dto

import com.google.gson.annotations.SerializedName

data class StationDto(
    @SerializedName("stationuuid") val stationuuid: String,
    @SerializedName("name") val name: String?,
    @SerializedName("url") val url: String?,
    @SerializedName("homepage") val homepage: String?,
    @SerializedName("favicon") val favicon: String?,
    @SerializedName("country") val country: String?,
    @SerializedName("countrycode") val countrycode: String?,
    @SerializedName("state") val state: String?,
    @SerializedName("language") val language: String?,
    @SerializedName("tags") val tags: String?,
    @SerializedName("codec") val codec: String?,
    @SerializedName("bitrate") val bitrate: Int?,
    @SerializedName("lastcheckok") val lastcheckok: Int?,
    @SerializedName("lastchangetime") val lastchangetime: String?,
    @SerializedName("clickcount") val clickcount: Int?,
    @SerializedName("clicktrend") val clicktrend: Int?,
    @SerializedName("votes") val votes: Int?
)