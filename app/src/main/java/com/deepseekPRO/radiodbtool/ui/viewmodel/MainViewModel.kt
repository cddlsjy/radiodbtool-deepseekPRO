package com.deepseekPRO.radiodbtool.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepseekPRO.radiodbtool.data.local.RadioDatabase
import com.deepseekPRO.radiodbtool.data.local.entity.StationEntity
import com.deepseekPRO.radiodbtool.data.remote.ApiClient
import com.deepseekPRO.radiodbtool.data.repository.RadioRepository
import com.deepseekPRO.radiodbtool.data.repository.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class MainViewModel(private val db: RadioDatabase) : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var apiClient: ApiClient = createApiClient()
    private var repository: RadioRepository = RadioRepository(apiClient, db)
    private var syncManager: SyncManager = SyncManager(repository, db)
    private var syncJob: Job? = null

    private fun createApiClient(): ApiClient {
        return ApiClient(_uiState.value.serverUrl)
    }

    init {
        loadServerLists()
        observeLocalData()
    }

    fun loadServerLists() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loadingLists = true, listsError = null)
            try {
                val countries = repository.fetchAvailableCountries()
                val languages = repository.fetchAvailableLanguages()
                _uiState.value = _uiState.value.copy(
                    availableCountries = countries,
                    availableLanguages = languages,
                    loadingLists = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loadingLists = false,
                    listsError = e.message ?: "加载失败"
                )
            }
        }
    }

    fun updateServerUrl(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url)
        apiClient = createApiClient()
        repository = RadioRepository(apiClient, db)
        syncManager = SyncManager(repository, db)
    }

    fun setSyncMode(mode: SyncMode) {
        _uiState.value = _uiState.value.copy(syncMode = mode)
    }

    fun setSelectedCountry(country: String) {
        _uiState.value = _uiState.value.copy(selectedCountry = country)
    }

    fun setSelectedLanguage(language: String) {
        _uiState.value = _uiState.value.copy(selectedLanguage = language)
    }

    fun setFilterKeyword(keyword: String) {
        _uiState.value = _uiState.value.copy(filterKeyword = keyword)
    }

    fun startSync() {
        if (_uiState.value.isSyncing) return

        syncJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isSyncing = true, syncProgress = 0f)
            try {
                if (_uiState.value.syncMode == SyncMode.FULL) {
                    syncManager.syncFull { current, total, msg ->
                        _uiState.value = _uiState.value.copy(
                            syncProgress = current.toFloat() / total,
                            syncMessage = msg
                        )
                    }
                    showToast("全量同步完成")
                } else {
                    val country = _uiState.value.selectedCountry
                    val language = _uiState.value.selectedLanguage
                    val keyword = _uiState.value.filterKeyword
                    syncManager.syncFiltered(country, language, keyword) { downloaded, _, msg ->
                        _uiState.value = _uiState.value.copy(
                            syncProgress = (downloaded % 100).toFloat() / 100f,
                            syncMessage = msg
                        )
                    }
                    showToast("条件同步完成")
                }
                refreshLocalDropdowns()
            } catch (e: kotlinx.coroutines.CancellationException) {
                _uiState.value = _uiState.value.copy(syncMessage = "已取消")
            } catch (e: Exception) {
                showToast("同步失败: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isSyncing = false)
            }
        }
    }

    fun cancelSync() {
        syncJob?.cancel()
        syncJob = null
        _uiState.value = _uiState.value.copy(isSyncing = false, syncMessage = "已取消")
    }

    fun setExportCountry(country: String) {
        _uiState.value = _uiState.value.copy(exportCountry = country)
    }

    fun setExportLanguage(language: String) {
        _uiState.value = _uiState.value.copy(exportLanguage = language)
    }

    fun setExportKeyword(keyword: String) {
        _uiState.value = _uiState.value.copy(exportKeyword = keyword)
    }

    fun setExportFormat(format: ExportFormat) {
        _uiState.value = _uiState.value.copy(exportFormat = format)
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    suspend fun getStationsForExport(): List<StationEntity> {
        val state = _uiState.value
        return repository.getFilteredStationsForExport(
            state.exportCountry,
            state.exportLanguage,
            state.exportKeyword
        )
    }

    private fun refreshLocalDropdowns() {
        viewModelScope.launch {
            db.stationDao().getAllCountries().collectLatest { countries ->
                _uiState.value = _uiState.value.copy(localCountries = countries)
            }
        }
        viewModelScope.launch {
            db.stationDao().getAllLanguages().collectLatest { languages ->
                _uiState.value = _uiState.value.copy(localLanguages = languages)
            }
        }
    }

    private fun observeLocalData() {
        refreshLocalDropdowns()
        viewModelScope.launch {
            db.stationDao().getFilteredStations("", "", "").collectLatest { stations ->
                _uiState.value = _uiState.value.copy(previewStations = stations)
            }
        }
    }

    fun filterPreviewStations(country: String, language: String, keyword: String) {
        viewModelScope.launch {
            db.stationDao().getFilteredStations(country, language, keyword).collectLatest { stations ->
                _uiState.value = _uiState.value.copy(previewStations = stations)
            }
        }
    }

    fun importFromDbFile(context: Context, uri: Uri) {
        if (_uiState.value.isImporting) return
        
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isImporting = true)
            try {
                val tempFile = File(context.cacheDir, "imported_${System.currentTimeMillis()}.db")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val importDb = android.database.sqlite.SQLiteDatabase.openDatabase(
                    tempFile.absolutePath,
                    null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                )

                try {
                    val cursor = importDb.query(
                        "stations",
                        arrayOf("stationuuid", "name", "url", "homepage", "favicon", "country", "countrycode", "state", "language", "tags", "codec", "bitrate", "lastcheckok", "lastchangetime", "clickcount", "clicktrend", "votes"),
                        null,
                        null,
                        null,
                        null,
                        null
                    )

                    val stations = mutableListOf<StationEntity>()
                    while (cursor.moveToNext()) {
                        val station = StationEntity(
                            stationuuid = cursor.getString(cursor.getColumnIndexOrThrow("stationuuid")),
                            name = cursor.getString(cursor.getColumnIndex("name")),
                            url = cursor.getString(cursor.getColumnIndex("url")),
                            homepage = cursor.getString(cursor.getColumnIndex("homepage")),
                            favicon = cursor.getString(cursor.getColumnIndex("favicon")),
                            country = cursor.getString(cursor.getColumnIndex("country")),
                            countrycode = cursor.getString(cursor.getColumnIndex("countrycode")),
                            state = cursor.getString(cursor.getColumnIndex("state")),
                            language = cursor.getString(cursor.getColumnIndex("language")),
                            tags = cursor.getString(cursor.getColumnIndex("tags")),
                            codec = cursor.getString(cursor.getColumnIndex("codec")),
                            bitrate = if (cursor.getColumnIndex("bitrate") >= 0) cursor.getInt(cursor.getColumnIndex("bitrate")) else null,
                            lastcheckok = if (cursor.getColumnIndex("lastcheckok") >= 0) cursor.getInt(cursor.getColumnIndex("lastcheckok")) else null,
                            lastchangetime = cursor.getString(cursor.getColumnIndex("lastchangetime")),
                            clickcount = if (cursor.getColumnIndex("clickcount") >= 0) cursor.getInt(cursor.getColumnIndex("clickcount")) else null,
                            clicktrend = if (cursor.getColumnIndex("clicktrend") >= 0) cursor.getInt(cursor.getColumnIndex("clicktrend")) else null,
                            votes = if (cursor.getColumnIndex("votes") >= 0) cursor.getInt(cursor.getColumnIndex("votes")) else null
                        )
                        stations.add(station)
                    }
                    cursor.close()

                    if (stations.isNotEmpty()) {
                        repository.insertStations(stations)
                        showToast("成功导入 ${stations.size} 条电台数据")
                        refreshLocalDropdowns()
                    } else {
                        showToast("数据库文件中没有数据")
                    }
                } finally {
                    importDb.close()
                    tempFile.delete()
                }
            } catch (e: Exception) {
                showToast("导入失败: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isImporting = false)
            }
        }
    }

    private fun showToast(message: String) {
        _uiState.value = _uiState.value.copy(toastMessage = message)
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            clearToast()
        }
    }
}