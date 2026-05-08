package com.deepseekPRO.radiodbtool.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.deepseekPRO.radiodbtool.ui.viewmodel.SyncMode
import com.deepseekPRO.radiodbtool.ui.viewmodel.UiState

@Composable
fun ServerSettingsCard(
    uiState: UiState,
    onServerUrlChange: (String) -> Unit,
    onRefreshLists: () -> Unit,
    isRefreshing: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "服务器设置",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.serverUrl,
                    onValueChange = onServerUrlChange,
                    label = { Text("API 地址") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onRefreshLists() })
                )
                IconButton(
                    onClick = onRefreshLists,
                    enabled = !isRefreshing
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新列表")
                }
            }
        }
    }
}

@Composable
fun SyncModeCard(
    uiState: UiState,
    onSyncModeChange: (SyncMode) -> Unit,
    onCountryChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onKeywordChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "同步模式",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = uiState.syncMode == SyncMode.FULL,
                        onClick = { onSyncModeChange(SyncMode.FULL) }
                    )
                    Text("全量同步")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = uiState.syncMode == SyncMode.FILTERED,
                        onClick = { onSyncModeChange(SyncMode.FILTERED) }
                    )
                    Text("条件同步")
                }
            }
            if (uiState.syncMode == SyncMode.FILTERED) {
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownSelector(
                        label = "国家",
                        items = uiState.availableCountries,
                        selected = uiState.selectedCountry,
                        onSelected = onCountryChange
                    )
                    DropdownSelector(
                        label = "语言",
                        items = uiState.availableLanguages,
                        selected = uiState.selectedLanguage,
                        onSelected = onLanguageChange
                    )
                    OutlinedTextField(
                        value = uiState.filterKeyword,
                        onValueChange = onKeywordChange,
                        label = { Text("关键词") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("搜索电台名称或标签") }
                    )
                }
            }
        }
    }
}

@Composable
fun DropdownSelector(
    label: String,
    items: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val displayText = if (selected.isEmpty()) "全部" else selected
    
    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("全部") },
                onClick = {
                    onSelected("")
                    expanded = false
                }
            )
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item) },
                    onClick = {
                        onSelected(item)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun SyncProgressSection(
    uiState: UiState,
    onStartSync: () -> Unit,
    onCancelSync: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "同步控制",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onStartSync,
                    enabled = !uiState.isSyncing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("开始同步")
                }
                Button(
                    onClick = onCancelSync,
                    enabled = uiState.isSyncing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("取消")
                }
            }
            if (uiState.isSyncing) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    LinearProgressIndicator(
                        progress = uiState.syncProgress,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = uiState.syncMessage,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ExportSection(
    uiState: UiState,
    onCountryChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onKeywordChange: (String) -> Unit,
    onFormatChange: (String) -> Unit,
    onExport: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "导出设置",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownSelector(
                    label = "国家",
                    items = uiState.localCountries,
                    selected = uiState.exportCountry,
                    onSelected = onCountryChange
                )
                DropdownSelector(
                    label = "语言",
                    items = uiState.localLanguages,
                    selected = uiState.exportLanguage,
                    onSelected = onLanguageChange
                )
                OutlinedTextField(
                    value = uiState.exportKeyword,
                    onValueChange = onKeywordChange,
                    label = { Text("关键词") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索电台名称或标签") }
                )
                DropdownSelector(
                    label = "导出格式",
                    items = listOf("M3U", "CSV", "JSON", "SQLite DB"),
                    selected = uiState.exportFormat.name,
                    onSelected = onFormatChange
                )
                Button(
                    onClick = onExport,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("导出筛选结果")
                }
            }
        }
    }
}

@Composable
fun StationPreviewList(
    stations: List<com.deepseekPRO.radiodbtool.data.local.entity.StationEntity>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "已下载电台预览（前20条）",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            if (stations.isEmpty()) {
                Text(text = "暂无数据，请先同步电台或导入数据库")
            } else {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "名称", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        Text(text = "国家", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        Text(text = "语言", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        Text(text = "点击数", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.5f))
                        Text(text = "码率", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.5f))
                    }
                    stations.forEach { station ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = station.name ?: "", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(text = station.country ?: "", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(text = station.language ?: "", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(text = station.clickcount?.toString() ?: "", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.5f))
                            Text(text = station.bitrate?.toString() ?: "", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.5f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImportSection(
    isImporting: Boolean,
    onImport: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "导入数据库",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Button(
                onClick = onImport,
                enabled = !isImporting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isImporting) "正在导入..." else "从本地DB文件导入")
            }
            Text(
                text = "支持导入 SQLite 数据库文件（.db格式）",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}