package com.deepseekPRO.radiodbtool.ui.compose

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deepseekPRO.radiodbtool.data.local.RadioDatabase
import com.deepseekPRO.radiodbtool.ui.viewmodel.ExportFormat
import com.deepseekPRO.radiodbtool.ui.viewmodel.MainViewModel
import com.deepseekPRO.radiodbtool.ui.viewmodel.SyncMode
import com.deepseekPRO.radiodbtool.util.FileSaver

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val db = RadioDatabase.getInstance(context)
    val viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(db))
    
    val uiState = viewModel.uiState.collectAsState()
    
    val showPermissionDialogState = remember { mutableStateOf(false) }
    
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument()
    ) { uri ->
        uri?.let {
            viewModel.uiState.value.let { state ->
                FileSaver.exportStations(viewModel, state.exportFormat, it, context)
            }
        }
    }
    
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importFromDbFile(context, it)
        }
    }
    
    if (showPermissionDialogState.value) {
        AlertDialog(
            onDismissRequest = { showPermissionDialogState.value = false },
            title = { Text("需要存储权限") },
            text = { Text("导出文件需要存储权限") },
            confirmButton = {
                TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        requestPermissions(
                            context,
                            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                            1
                        )
                    }
                    showPermissionDialogState.value = false
                }) {
                    Text("授权")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialogState.value = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("RadioDB Tool") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ServerSettingsCard(
                uiState = uiState.value,
                onServerUrlChange = { viewModel.updateServerUrl(it) },
                onRefreshLists = { viewModel.loadServerLists() },
                isRefreshing = uiState.value.loadingLists
            )
            
            if (uiState.value.loadingLists) {
                Text("正在加载国家/语言列表...")
            } else if (uiState.value.listsError != null) {
                Text(text = "加载失败: ${uiState.value.listsError}")
                TextButton(onClick = { viewModel.loadServerLists() }) {
                    Text("重试")
                }
            } else {
                SyncModeCard(
                    uiState = uiState.value,
                    onSyncModeChange = { viewModel.setSyncMode(it) },
                    onCountryChange = { viewModel.setSelectedCountry(it) },
                    onLanguageChange = { viewModel.setSelectedLanguage(it) },
                    onKeywordChange = { viewModel.setFilterKeyword(it) }
                )
                
                SyncProgressSection(
                    uiState = uiState.value,
                    onStartSync = { viewModel.startSync() },
                    onCancelSync = { viewModel.cancelSync() }
                )
                
                ExportSection(
                    uiState = uiState.value,
                    onCountryChange = { viewModel.setExportCountry(it) },
                    onLanguageChange = { viewModel.setExportLanguage(it) },
                    onKeywordChange = { viewModel.setExportKeyword(it) },
                    onFormatChange = { format ->
                        val exportFormat = when (format) {
                            "CSV" -> ExportFormat.CSV
                            "JSON" -> ExportFormat.JSON
                            "SQLite DB" -> ExportFormat.DB
                            else -> ExportFormat.M3U
                        }
                        viewModel.setExportFormat(exportFormat)
                    },
                    onExport = {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ) == PackageManager.PERMISSION_GRANTED
                            
                            if (!hasPermission) {
                                showPermissionDialogState.value = true
                                return@ExportSection
                            }
                        }
                        val fileName = "radio_stations.${uiState.value.exportFormat.extension}"
                        exportLauncher.launch(fileName)
                    }
                )
                
                ImportSection(
                    isImporting = uiState.value.isImporting,
                    onImport = {
                        importLauncher.launch(arrayOf("application/x-sqlite3", "application/octet-stream"))
                    }
                )
                
                StationPreviewList(stations = uiState.value.previewStations)
            }
        }
    }
    
    if (uiState.value.toastMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearToast() },
            text = { Text(uiState.value.toastMessage ?: "") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearToast() }) {
                    Text("确定")
                }
            }
        )
    }
}

fun requestPermissions(context: android.content.Context, permissions: Array<String>, requestCode: Int) {
    if (context is android.app.Activity) {
        context.requestPermissions(permissions, requestCode)
    }
}

class MainViewModelFactory(private val db: RadioDatabase) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return MainViewModel(db) as T
    }
}