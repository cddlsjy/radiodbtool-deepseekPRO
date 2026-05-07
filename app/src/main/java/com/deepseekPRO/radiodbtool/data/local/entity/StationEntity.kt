package com.deepseekPRO.radiodbtool.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stations")
data class StationEntity(
    @PrimaryKey val stationuuid: String,
    val name: String?,
    val url: String?,
    val homepage: String?,
    val favicon: String?,
    val country: String?,
    val countrycode: String?,
    val state: String?,
    val language: String?,
    val tags: String?,
    val codec: String?,
    val bitrate: Int?,
    val lastcheckok: Int?,
    val lastchangetime: String?,
    val clickcount: Int?,
    val clicktrend: Int?,
    val votes: Int?
)