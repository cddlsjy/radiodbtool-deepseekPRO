我们将这个 Python 工具的功能完整映射到 Android 平台，下面是一份结构化、无歧义的实现方案，可直接交给 Trae 等 AI 编程助手生成代码。方案涵盖架构、UI、数据层、网络、导出及所有关键细节。

***

## 一、功能需求总结

1. **服务器连接**：可配置 API 地址（默认 `https://de1.api.radio-browser.info`）。
2. **列表加载**：启动时从服务器拉取**所有国家列表**和**所有语言列表**，填充下拉选择框。
3. **两种同步模式**：
   - **全量同步**：分页下载全部电台（每页 500 条），带断点续传（记录 offset）。
   - **条件同步**：按国家、语言、关键词筛选下载，每次从头开始，不使用断点。
4. **本地数据库**：存储已下载电台，支持插入或忽略（按 UUID 去重）。
5. **进度反馈**：实时更新进度条和状态文本，支持取消。
6. **数据预览**：在主界面以表格形式显示已下载电台的前 20 条，可按国家/语言/关键词过滤。
7. **导出功能**：将筛选结果导出为 M3U、CSV、JSON 或 SQLite DB 文件，通过系统文件选择器保存。
8. **导出筛选条件**：基于本地数据库，支持下拉选择已存在的国家/语言，也可输入关键词。

***

## 二、技术栈与核心依赖

| 类别      | 选择                                        | 说明                                |
| :------ | :---------------------------------------- | :-------------------------------- |
| 开发语言    | Kotlin                                    | 简洁、安全、与协程无缝集成                     |
| 最低 SDK  | API 26 (Android 8.0)                      | 保证现代 API 可用                       |
| 架构模式    | MVVM + Repository                         | 官方推荐，职责清晰                         |
| 异步处理    | Kotlin Coroutines + Flow                  | 网络请求、数据库操作均用挂起函数                  |
| 网络请求    | Retrofit2 + OkHttp + Gson                 | REST API 调用、JSON 解析               |
| 本地数据库   | Room (SQLite 封装)                          | 线程安全、编译时 SQL 校验                   |
| UI 框架   | Jetpack Compose                           | 现代声明式 UI（也可改用 XML，方案以 Compose 为主） |
| 架构组件    | ViewModel, LiveData/StateFlow, Navigation | 生命周期管理、UI 状态                      |
| 文件选择/保存 | ActivityResultContracts (SAF)             | 安全访问外部存储（适配 Android 10+）          |
| 权限      | 无需特殊权限（导出通过 SAF，Android 10+ 无需存储权限）       | <br />                            |
| 构建工具    | Gradle (Kotlin DSL)                       | <br />                            |

***

## 三、项目结构（包名示例：`com.example.radiodbtool`）

```
com.example.radiodbtool
├── data
│   ├── local
│   │   ├── dao            // Room DAO 接口
│   │   │   ├── StationDao.kt
│   │   │   └── SyncProgressDao.kt
│   │   ├── entity         // Room 实体
│   │   │   ├── StationEntity.kt
│   │   │   └── SyncProgressEntity.kt
│   │   └── RadioDatabase.kt
│   ├── remote
│   │   ├── api            // Retrofit API 接口
│   │   │   └── RadioApiService.kt
│   │   └── dto            // 网络 DTO (直接解析 JSON)
│   │       ├── StationDto.kt          // 对应服务器返回的单条电台数据
│   │       ├── CountryDto.kt          // {"name":"China","stationcount":123}
│   │       └── LanguageDto.kt         // 类似
│   └── repository
│       ├── RadioRepository.kt         // 网络+本地数据协调
│       └── SyncManager.kt             // 专门处理分页下载、断点续传、取消
├── domain
│   └── model
│       └── Station.kt                 // 可选的领域模型（可省略，直接使用 Entity）
├── ui
│   ├── compose
│   │   ├── MainScreen.kt             // 主界面 (服务器设置，同步模式，筛选条件)
│   │   ├── SyncProgressSection.kt    // 进度条 + 取消按钮
│   │   ├── ExportSection.kt          // 导出条件 + 格式 + 导出按钮
│   │   ├── StationPreviewList.kt     // 电台预览表格 (LazyColumn)
│   │   └── theme                      // 可不用
│   └── viewmodel
│       └── MainViewModel.kt
├── util
│   ├── Extensions.kt                 // 扩展函数
│   └── FileSaver.kt                  // SAF 文件保存工具
└── RadioDbToolApp.kt                // Application 类 (初始化数据库)
```

***

## 四、数据库设计 (Room)

**表1：stations**

```kotlin
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
    val lastcheckok: Int?,        // 0/1
    val lastchangetime: String?,
    val clickcount: Int?,
    val clicktrend: Int?,
    val votes: Int?
)
```

**表2：sync\_progress**

```kotlin
@Entity(tableName = "sync_progress")
data class SyncProgressEntity(
    @PrimaryKey val filter_key: String,
    val offset: Int
)
```

**DAO 接口**：

- `StationDao`：
  - `@Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertStations(stations: List<StationEntity>)`
  - `@Query("SELECT * FROM stations WHERE ...")` 自定义筛选（支持国家、语言、关键词模糊匹配），返回 `Flow<List<StationEntity>>` 或挂起函数。
  - `@Query("SELECT DISTINCT country FROM stations WHERE country != '' ORDER BY country")` 返回国家列表。
  - `@Query("SELECT DISTINCT language FROM ...")` 返回语言列表。
  - `@Query("SELECT COUNT(*) FROM stations")` 返回总数。
- `SyncProgressDao`：
  - `@Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveProgress(progress: SyncProgressEntity)`
  - `@Query("SELECT offset FROM sync_progress WHERE filter_key = :key") suspend fun getOffset(key: String): Int?`
  - `@Query("DELETE FROM sync_progress WHERE filter_key = :key") suspend fun deleteProgress(key: String)`

**数据库类** `RadioDatabase` 使用 Room 单例。

***

## 五、网络层设计

**API 接口** (`RadioApiService`)：

```kotlin
interface RadioApiService {
    @GET("json/stats")
    suspend fun getStats(): StatsDto   // { "stations": 12345 }

    @GET("json/countries")
    suspend fun getCountries(): List<CountryDto>   // [{ "name": "..." }]

    @GET("json/languages")
    suspend fun getLanguages(): List<LanguageDto>

    // 分页/筛选下载主接口
    @GET("json/stations")
    suspend fun getStations(
        @Query("limit") limit: Int = 500,
        @Query("offset") offset: Int,
        @Query("country") country: String? = null,
        @Query("language") language: String? = null,
        @Query("name") name: String? = null        // 关键词匹配 name
    ): List<StationDto>
}
```

DTO 字段与 JSON 完全对应（`StationDto` 所有字段与 Python 脚本中的 `s.get(...)` 键名一致）。注意 `lastcheckok` 等字段可以是 `Int?`。

**Retrofit 实例**：基地址可动态更改（通过 `@Url` 全路径或配置多 Base URL）。我们采用轻量方案：不直接动态改 Base URL，而是在用户更改服务器地址时重新创建 Retrofit 实例。提供一个 `class ApiClient(baseUrl: String)` 用于构建。

`RadioRepository` 持有 `ApiClient` 实例并提供方法：

- `fetchAvailableCountries()` / `fetchAvailableLanguages()` 直接调用 API。
- `fetchStats()` 获取总数。
- `fetchStationPage(...)` 获取一页电台数据。

**注意**：服务器可能返回空数组表示无更多数据，我们根据返回数量判断分页结束。

***

## 六、同步逻辑 (SyncManager)

核心难点：分页、断点续传、取消、两种模式。

**全量同步** (`syncFull`):

1. 获取总电台数 `total`。
2. 从 `sync_progress` 读取 `filter_key = "full"` 的 offset，若无则为 0。
3. 若 offset >= total，直接完成。
4. 循环请求 `offset/500` 页，每次请求后：
   - 插入数据库。
   - 保存新的 offset 到 `sync_progress`。
   - 通过回调更新进度 `(offset, total)`。
   - 检查 `cancelFlag`（`AtomicBoolean` 或协程 `isActive`）。
   - 如果当前页数据量 < limit，终止循环。
5. 完成后删除 `"full"` 的 progress 记录。

**条件同步** (`syncFiltered`):

1. 生成唯一 `filter_key = MD5("country|language|keyword")`。
2. 主动删除该 key 的旧进度（确保每次条件同步都重新开始）。
3. 在循环中调用 `fetchStationPage`，携带 `country`、`language`、`name`（关键词）。
4. offset 每次增加实际返回的条数。
5. 下载总数未知，进度条使用“已下载 N 条”模式（进度条不确定或展示计数）。
6. 循环直到返回空数组。
7. 结束后删除 progress。

所有操作在协程中执行，通过 `withContext(Dispatchers.IO)` 或指定作用域，支持 `job.cancel()` 取消。

***

## 七、UI 设计 (Compose)

主界面 `MainScreen` 使用 `Scaffold` + `Column(verticalScroll)`，从上到下包含：

### 1. 服务器设置卡片

- `OutlinedTextField` 输入 API 地址，默认值。
- 刷新国家/语言列表按钮（图标按钮）。

### 2. 同步模式卡片

- `RadioButton` 组：全量同步 / 条件同步。
- 条件同步下可用的筛选区（可见性根据模式变化）：
  - `ExposedDropdownMenuBox` 或 `DropdownMenu` 选择国家（从服务器获取的列表）。
  - 语言下拉（同上）。
  - 关键词输入框。

> 启动时自动调用 `loadServerLists()`，请求国家/语言 API 并填充下拉选项。显示加载状态“正在加载...”或错误信息。

### 3. 控制按钮与进度区

- 按钮：“开始同步”（开始后禁用）、“取消”（仅同步中启用）。
- `LinearProgressIndicator` 横向进度条，进度由 ViewModel 状态驱动。
- `Text` 显示当前状态消息。

### 4. 导出区域

- 国家下拉（从本地数据库获取，可空）。
- 语言下拉（同上）。
- 关键词输入。
- 导出格式下拉（M3U, CSV, JSON, SQLite DB）。
- “导出筛选结果”按钮。

### 5. 电台预览区

- 标题：“已下载电台预览（前20条）”
- 表格形式：使用 `LazyColumn` + `Row` 模拟表头。
- 列：电台名称、国家、语言、点击数、码率。
- 数据来源于 ViewModel 的筛选查询，当导出条件变化时自动刷新（加防抖）。

***

## 八、ViewModel 设计 (`MainViewModel`)

**状态**（使用 `StateFlow` 或 `MutableState`）：

```kotlin
data class UiState(
    val serverUrl: String = "https://de1.api.radio-browser.info",
    val syncMode: SyncMode = SyncMode.FILTERED, // enum FULL, FILTERED
    val availableCountries: List<String> = emptyList(),
    val availableLanguages: List<String> = emptyList(),
    val selectedCountry: String = "",
    val selectedLanguage: String = "",
    val filterKeyword: String = "",
    val isSyncing: Boolean = false,
    val syncProgress: Float = 0f,          // 0~1 or current count
    val syncMessage: String = "",
    val exportCountry: String = "",
    val exportLanguage: String = "",
    val exportKeyword: String = "",
    val exportFormat: ExportFormat = ExportFormat.M3U, // enum
    val previewStations: List<StationEntity> = emptyList(),
    val localCountries: List<String> = emptyList(),
    val localLanguages: List<String> = emptyList(),
    val loadingLists: Boolean = true,
    val listsError: String? = null
)
```

**操作**：

- `loadServerLists()`：调用仓库获取国家/语言，更新 `availableCountries/availableLanguages`，捕获异常显示错误。
- `startSync()`：根据模式设置状态 `isSyncing = true`，启动协程，调用 `syncManager` 对应方法，传递进度回调（通过 `syncProgress` 和 `syncMessage` 更新状态），同步完成后设置 `isSyncing = false`，刷新本地下拉数据和预览。
- `cancelSync()`：取消协程作业。
- `refreshLocalDropdowns()`：从数据库读取国家/语言列表，更新 `localCountries/languages`。
- `filterPreviewStations()`：根据导出筛选条件从数据库查询，更新 `previewStations`（取前20条）。此方法可在导出条件下拉选择或关键词输入时调用，使用 `snapshotFlow` 或 `collectAsState` 方式，也可手动触发。
- `exportStations()`：调用数据库筛选，根据格式写入文件，通过 `FileSaver` 启动 SAF 保存流程，结果通过回调通知 UI。

**依赖注入**：建议使用手动依赖注入（构造传入）或 Hilt（若想让 Trae 简单，可以不使用 Hilt，直接在 ViewModel 工厂中创建 Repository 和 Database 实例）。

***

## 九、导出文件实现 (FileSaver)

使用 `ActivityResultLauncher` 启动系统文件选择器（保存）。

步骤：

1. 在 Composable 中注册 `rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(...))`。
2. 点击导出按钮后，调用仓库进行数据查询（挂起函数），得到 `List<StationEntity>`。
3. 根据格式生成内容：
   - **M3U**：字符串拼接 `#EXTM3U\n` 和 `#EXTINF:-1,name\nurl`。
   - **CSV**：使用 `StringBuilder`，以标准 CSV 格式写入（各字段用逗号分隔，加引号转义）。
   - **JSON**：将列表序列化为 JSON（Gson `toJson`），写入。
   - **SQLite DB**：创建新 Room 数据库文件（使用 `Room.databaseBuilder` 新建一个临时数据库），批量插入数据，然后关闭。将文件复制到指定 URI。
4. 通过 `ContentResolver.openOutputStream(uri)` 写入数据。
5. 显示成功提示。

推荐将文件写入逻辑放在 `Dispatchers.IO` 中，完成后通知 UI。

***

## 十、分页与断点续传关键细节

- 使用 Room 的 `insertStations` 批量插入（在事务中执行）。Python 中逐条插入，Android 使用 `@Insert` 批量更高效。
- 断点信息保存在独立的 `sync_progress` 表中，确保应用关闭后数据不丢失。
- 全量同步时 `filter_key = "full"`，条件同步用 MD5 哈希区分。这样不同条件的进度互不影响。
- 取消操作：在 ViewModel 中维护一个 `Job` 对象，取消时调用 `job.cancel()`。在 `SyncManager` 的循环内检查当前协程的 `isActive`，如果为 `false` 则提前返回。

***

## 十一、可能遇到的问题及解决方案

1. **网络请求大量数据导致 UI 卡顿**：所有网络、数据库操作都在 `Dispatchers.IO` 进行，通过 `StateFlow` 收集更新 UI。
2. **动态服务器地址**：每次更改地址后重新创建 Retrofit 和 Repository，放在 ViewModel 中管理，必要时利用 `MutableStateFlow` 驱动。
3. **下拉框数据来自服务器**：初次加载时可能失败，提供重新加载按钮，显示错误信息。国家/语言列表可采用缓存策略。
4. **条件同步的关键词参数**：Radio Browser API 的 `name` 参数支持模糊搜索，直接传递关键词即可。若需同时搜索 `tags`，需额外分析 API 是否支持；如果不支持，可在本地数据库筛选时用 `tags LIKE`。
5. **文件导出权限**：Android 10+ 不需要存储权限，使用 SAF 即可。若兼容 Android 9 以下，需添加 `WRITE_EXTERNAL_STORAGE` 并动态请求（极少见，可忽略）。
6. **性能**：预览只显示 20 条，避免大数据量渲染。

***

## 十二、代码生成指导（给 Trae 的提示）

- 确保所有字符串资源从 `strings.xml` 引用（若用 Compose 可硬编码，但建议资源化）。
- 使用 `@Composable` 函数拆分 UI，遵循单一职责。
- ViewModel 应继承 `androidx.lifecycle.ViewModel`，使用 `viewModelScope` 启动协程。
- Room 数据库的实例化放在 Application 类或使用 `Room.databaseBuilder` 单例。
- Retrofit 的 `BaseUrl` 必须以 `/` 结尾。
- 所有网络 DTO 字段名与 API JSON 完全一致，使用 `@SerializedName` 处理可能的命名差异（通常一致）。
- 导出为 SQLite DB 时，创建一个独立的临时 Room 数据库，勿与主数据库混淆。
- 使用 `MaterialTheme` 提供默认 Material 3 风格。

***

## 十三、附加：关键代码片段示例（伪代码）

**SyncManager 中的条件同步**：

```kotlin
suspend fun syncFiltered(
    country: String,
    language: String,
    keyword: String,
    db: RadioDatabase,
    onProgress: (Int, Int?, String) -> Unit
) {
    val filterKey = md5("$country|$language|$keyword")
    db.syncProgressDao().deleteProgress(filterKey) // 清除断点
    var offset = 0
    var totalDownloaded = 0
    while (isActive) {
        val list = repository.fetchStationPage(offset = offset, country = country, language = language, name = keyword)
        if (list.isEmpty()) break
        db.stationDao().insertStations(list.map { it.toEntity() })
        totalDownloaded += list.size
        offset += list.size
        db.syncProgressDao().saveProgress(SyncProgressEntity(filterKey, offset))
        onProgress(totalDownloaded, null, "已下载 $totalDownloaded 条")
    }
    db.syncProgressDao().deleteProgress(filterKey)
}
```

**ViewModel 调用**：

```kotlin
fun startSync() {
    if (isSyncing) return
    val job = viewModelScope.launch(Dispatchers.IO) {
        isSyncing = true
        try {
            if (syncMode == SyncMode.FULL) {
                syncManager.syncFull(...) { current, total, msg ->
                    _uiState.update { it.copy(syncProgress = current.toFloat() / total, syncMessage = msg) }
                }
            } else {
                syncManager.syncFiltered(...) { downloaded, _, msg ->
                    _uiState.update { it.copy(syncProgress = (downloaded % 100).toFloat() / 100f, syncMessage = msg) }
                }
            }
            // 刷新本地下拉和预览
            refreshLocalDropdowns()
            filterPreviewStations()
        } catch (e: CancellationException) {
            // 取消
        } catch (e: Exception) {
            // 错误处理
        } finally {
            isSyncing = false
        }
    }
    syncJob = job
}
```

***

## 十四、总结

本方案完整覆盖了从 Python 脚本到 Android 应用的转换，包括架构选择、数据流、UI 布局、异步处理、文件导出等所有细节。每个模块的职责清晰，无模糊地带。按照此方案，Trae 等 AI 编程工具可以直接生成可编译运行的 Kotlin 代码。

如果需要在生成过程中进一步细化某一部分，可以要求提供对应的完整类代码模板。
