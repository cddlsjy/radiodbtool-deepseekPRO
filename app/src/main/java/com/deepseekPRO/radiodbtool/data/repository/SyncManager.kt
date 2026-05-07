package com.deepseekPRO.radiodbtool.data.repository

import com.deepseekPRO.radiodbtool.data.local.RadioDatabase
import com.deepseekPRO.radiodbtool.data.local.entity.SyncProgressEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class SyncManager(private val repository: RadioRepository, private val db: RadioDatabase) {
    private companion object {
        const val FULL_SYNC_KEY = "full"
        const val PAGE_SIZE = 500
    }

    suspend fun syncFull(onProgress: (Int, Int, String) -> Unit) {
        withContext(Dispatchers.IO) {
            val total = repository.fetchStats()
            if (total == 0) {
                onProgress(0, 0, "无法获取电台总数")
                return@withContext
            }
            
            val savedOffset = db.syncProgressDao().getOffset(FULL_SYNC_KEY) ?: 0
            
            if (savedOffset >= total) {
                onProgress(total, total, "数据已是最新")
                db.syncProgressDao().deleteProgress(FULL_SYNC_KEY)
                return@withContext
            }

            var offset = savedOffset
            while (offset < total && isActive) {
                val stations = repository.fetchStationPage(offset = offset, limit = PAGE_SIZE)
                if (stations.isEmpty()) break

                repository.insertStations(stations)
                offset += stations.size
                db.syncProgressDao().saveProgress(SyncProgressEntity(FULL_SYNC_KEY, offset))
                
                onProgress(offset, total, "已下载 ${offset.coerceAtMost(total)} / $total")

                delay(50)
            }

            db.syncProgressDao().deleteProgress(FULL_SYNC_KEY)
            onProgress(total, total, "全量同步完成")
        }
    }

    suspend fun syncFiltered(
        country: String,
        language: String,
        keyword: String,
        onProgress: (Int, Int?, String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            val filterKey = RadioRepository.md5("$country|$language|$keyword")
            db.syncProgressDao().deleteProgress(filterKey)

            var offset = 0
            var totalDownloaded = 0

            while (isActive) {
                val stations = repository.fetchStationPage(
                    offset = offset,
                    country = country.takeIf { it.isNotEmpty() },
                    language = language.takeIf { it.isNotEmpty() },
                    name = keyword.takeIf { it.isNotEmpty() }
                )

                if (stations.isEmpty()) break

                repository.insertStations(stations)
                totalDownloaded += stations.size
                offset += stations.size

                db.syncProgressDao().saveProgress(SyncProgressEntity(filterKey, offset))
                onProgress(totalDownloaded, null, "已下载 $totalDownloaded 条")
            }

            db.syncProgressDao().deleteProgress(filterKey)
        }
    }
}