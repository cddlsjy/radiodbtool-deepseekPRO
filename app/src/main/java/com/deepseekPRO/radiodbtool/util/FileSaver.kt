package com.deepseekPRO.radiodbtool.util

import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.deepseekPRO.radiodbtool.data.local.RadioDatabase
import com.deepseekPRO.radiodbtool.data.local.entity.StationEntity
import com.deepseekPRO.radiodbtool.ui.viewmodel.ExportFormat
import com.deepseekPRO.radiodbtool.ui.viewmodel.MainViewModel
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

object FileSaver {
    fun exportStations(viewModel: MainViewModel, format: ExportFormat, uri: Uri, context: Context) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val stations = viewModel.getStationsForExport()
                when (format) {
                    ExportFormat.M3U -> exportM3U(stations, uri, context)
                    ExportFormat.CSV -> exportCSV(stations, uri, context)
                    ExportFormat.JSON -> exportJSON(stations, uri, context)
                    ExportFormat.DB -> exportDB(stations, uri, context)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun exportM3U(stations: List<StationEntity>, uri: Uri, context: Context) {
        val content = StringBuilder().apply {
            append("#EXTM3U\n\n")
            stations.forEach { station ->
                append("#RADIOBROWSERUUID:${station.stationuuid}\n")
                append("#EXTINF:-1,${station.name ?: "Unknown"}\n")
                station.favicon?.let { append("#EXTIMG:$it\n") }
                append("${station.url ?: ""}\n\n")
            }
        }.toString()
        writeToFile(content, uri, context)
    }

    private fun exportCSV(stations: List<StationEntity>, uri: Uri, context: Context) {
        val content = StringBuilder().apply {
            append("stationuuid,name,url,homepage,favicon,country,countrycode,state,language,tags,codec,bitrate,lastcheckok,lastchangetime,clickcount,clicktrend,votes\n")
            stations.forEach { station ->
                append("${escapeCSV(station.stationuuid)},")
                append("${escapeCSV(station.name)},")
                append("${escapeCSV(station.url)},")
                append("${escapeCSV(station.homepage)},")
                append("${escapeCSV(station.favicon)},")
                append("${escapeCSV(station.country)},")
                append("${escapeCSV(station.countrycode)},")
                append("${escapeCSV(station.state)},")
                append("${escapeCSV(station.language)},")
                append("${escapeCSV(station.tags)},")
                append("${escapeCSV(station.codec)},")
                append("${station.bitrate},")
                append("${station.lastcheckok},")
                append("${escapeCSV(station.lastchangetime)},")
                append("${station.clickcount},")
                append("${station.clicktrend},")
                append("${station.votes}\n")
            }
        }.toString()
        writeToFile(content, uri, context)
    }

    private fun exportJSON(stations: List<StationEntity>, uri: Uri, context: Context) {
        val gson = Gson()
        val content = gson.toJson(stations)
        writeToFile(content, uri, context)
    }

    private fun exportDB(stations: List<StationEntity>, uri: Uri, context: Context) {
        val tempFile = File(context.cacheDir, "temp_radio.db")
        if (tempFile.exists()) {
            tempFile.delete()
        }

        val tempDb = Room.databaseBuilder(
            context,
            RadioDatabase::class.java,
            tempFile.name
        ).build()

        try {
            runBlocking {
                tempDb.stationDao().insertStations(stations)
            }
            tempDb.close()

            FileInputStream(tempFile).use { input ->
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    input.copyTo(output)
                }
            }
        } finally {
            tempDb.close()
            tempFile.delete()
        }
    }

    private fun writeToFile(content: String, uri: Uri, context: Context) {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(content.toByteArray())
        }
    }

    private fun escapeCSV(value: String?): String {
        val escaped = value?.replace("\"", "\"\"") ?: ""
        return "\"$escaped\""
    }

    private fun <T> runBlocking(block: suspend () -> T): T {
        return kotlinx.coroutines.runBlocking { block() }
    }
}