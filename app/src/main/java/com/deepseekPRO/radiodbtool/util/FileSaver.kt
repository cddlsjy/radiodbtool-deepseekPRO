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
        val content = buildString {
            append("#EXTM3U\n\n")
            stations.forEach { station ->
                if (!station.url.isNullOrEmpty()) {
                    append("#EXTINF:-1,${station.name?.replace("\n", "") ?: "Unknown"}\n")
                    station.favicon?.let { append("#EXTIMG:$it\n") }
                    append("${station.url}\n")
                    append("#RADIOBROWSERUUID:${station.stationuuid}\n\n")
                }
            }
        }
        writeToFile(content, uri, context)
    }

    private fun exportCSV(stations: List<StationEntity>, uri: Uri, context: Context) {
        val headers = listOf("stationuuid", "name", "url", "homepage", "favicon", "country", "countrycode", "state", "language", "tags", "codec", "bitrate", "lastcheckok", "lastchangetime", "clickcount", "clicktrend", "votes")
        val out = StringBuilder()
        out.append(headers.joinToString(",")).append("\n")
        for (s in stations) {
            val row = headers.map { header ->
                val value = when (header) {
                    "stationuuid" -> s.stationuuid
                    "name" -> s.name
                    "url" -> s.url
                    "homepage" -> s.homepage
                    "favicon" -> s.favicon
                    "country" -> s.country
                    "countrycode" -> s.countrycode
                    "state" -> s.state
                    "language" -> s.language
                    "tags" -> s.tags
                    "codec" -> s.codec
                    "bitrate" -> s.bitrate?.toString()
                    "lastcheckok" -> s.lastcheckok?.toString()
                    "lastchangetime" -> s.lastchangetime
                    "clickcount" -> s.clickcount?.toString()
                    "clicktrend" -> s.clicktrend?.toString()
                    "votes" -> s.votes?.toString()
                    else -> ""
                }
                escapeCsv(value)
            }
            out.append(row.joinToString(",")).append("\n")
        }
        writeToFile(out.toString(), uri, context)
    }

    private fun escapeCsv(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        return if (value.contains(",") || value.contains("\n") || value.contains("\"")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value
    }

    private fun exportJSON(stations: List<StationEntity>, uri: Uri, context: Context) {
        val content = Gson().toJson(stations)
        writeToFile(content, uri, context)
    }

    private fun exportDB(stations: List<StationEntity>, uri: Uri, context: Context) {
        val tempFile = File(context.cacheDir, "temp_export_${System.currentTimeMillis()}.db")
        val tempDb = Room.databaseBuilder(context, RadioDatabase::class.java, tempFile.absolutePath)
            .build()
        try {
            runBlocking {
                tempDb.stationDao().insertStations(stations)
            }
            tempDb.close()
            context.contentResolver.openOutputStream(uri)?.use { output ->
                FileInputStream(tempFile).use { input ->
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

    private fun <T> runBlocking(block: suspend () -> T): T {
        return kotlinx.coroutines.runBlocking { block() }
    }
}