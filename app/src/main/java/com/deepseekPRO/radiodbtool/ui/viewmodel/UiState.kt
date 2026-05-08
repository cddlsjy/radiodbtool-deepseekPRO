package com.deepseekPRO.radiodbtool.ui.viewmodel

import com.deepseekPRO.radiodbtool.data.local.entity.StationEntity

data class UiState(
    val serverUrl: String = "https://de1.api.radio-browser.info",
    val syncMode: SyncMode = SyncMode.FILTERED,
    val availableCountries: List<String> = emptyList(),
    val availableLanguages: List<String> = emptyList(),
    val selectedCountry: String = "",
    val selectedLanguage: String = "",
    val filterKeyword: String = "",
    val isSyncing: Boolean = false,
    val syncProgress: Float = 0f,
    val syncMessage: String = "",
    val exportCountry: String = "",
    val exportLanguage: String = "",
    val exportKeyword: String = "",
    val exportFormat: ExportFormat = ExportFormat.M3U,
    val previewStations: List<StationEntity> = emptyList(),
    val localCountries: List<String> = emptyList(),
    val localLanguages: List<String> = emptyList(),
    val loadingLists: Boolean = true,
    val listsError: String? = null,
    val toastMessage: String? = null,
    val importDbPath: String = "",
    val isImporting: Boolean = false
)

enum class SyncMode {
    FULL, FILTERED
}

enum class ExportFormat(val extension: String, val mimeType: String) {
    M3U("m3u", "audio/x-mpegurl"),
    CSV("csv", "text/csv"),
    JSON("json", "application/json"),
    DB("db", "application/x-sqlite3")
}