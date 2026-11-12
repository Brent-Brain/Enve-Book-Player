package com.enve.app.data.sync

import android.content.Context
import android.util.Log
import com.enve.app.data.remote.GrimmoryApi
import com.enve.app.data.remote.dto.KavitaChapterDto
import com.enve.app.data.remote.dto.KavitaSeriesDto
import com.enve.app.data.remote.dto.KavitaSeriesFilterDto
import com.enve.core.data.local.BookCacheDao
import com.enve.core.data.local.CachedBook
import com.enve.core.data.local.ConnectionRegistry
import com.enve.core.data.model.BookSource
import com.enve.core.data.remote.ConnectionScope
import com.enve.core.data.util.FINISHED_PROGRESS_THRESHOLD
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

private const val TAG = "KavitaProgressSync"
private const val PAGE_SIZE = 500
private const val PROGRESS_EPSILON = 0.002f
private const val STAMP_FILE = "kavita-progress-stamps.json"

@Singleton
class KavitaProgressSync @Inject constructor(
    private val api: GrimmoryApi,
    private val bookCache: BookCacheDao,
    private val connectionRegistry: ConnectionRegistry,
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val stampSerializer = MapSerializer(String.serializer(), MapSerializer(String.serializer(), String.serializer()))

    suspend fun sync() {
        val connections = connectionRegistry.connections.first()
            .filter { it.enabled && it.source == BookSource.KAVITA }
        if (connections.isEmpty()) return
        mutex.withLock {
            val stamps = readStamps().toMutableMap()
            connections.forEach { connection ->
                try {
                    val updated = withContext(ConnectionScope.asContextElement(connection.id)) {
                        syncConnection(connection.id, stamps[connection.id].orEmpty())
                    }
                    stamps[connection.id] = updated
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "progress sync failed for ${connection.id}: ${e.message}")
                }
            }
            writeStamps(stamps)
        }
    }

    private suspend fun syncConnection(connectionId: String, known: Map<String, String>): Map<String, String> {
        val series = fetchSeries()
        val fresh = series.associate { it.id.toString() to stampOf(it) }
        val changed = series.filter { item ->
            val key = item.id.toString()
            if (known.isEmpty()) item.pagesRead > 0 else known[key] != fresh[key]
        }
        val failed = mutableSetOf<String>()
        changed.forEach { item ->
            try {
                applySeries(connectionId, item)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed += item.id.toString()
                Log.w(TAG, "series ${item.id} progress failed: ${e.message}")
            }
        }
        return fresh.filterKeys { it !in failed }
    }

    private suspend fun fetchSeries(): List<KavitaSeriesDto> {
        val all = mutableListOf<KavitaSeriesDto>()
        var page = 1
        do {
            val response = api.kavitaSeries(pageNumber = page, pageSize = PAGE_SIZE, filter = KavitaSeriesFilterDto.forLibrary(null))
            if (!response.isSuccessful) error("series list failed (HTTP ${response.code()})")
            val batch = response.body().orEmpty()
            all += batch
            page++
        } while (batch.size == PAGE_SIZE)
        return all
    }

    private suspend fun applySeries(connectionId: String, item: KavitaSeriesDto) {
        val rows = bookCache.getKavitaSeriesRows(connectionId, item.id.toString())
        if (rows.isEmpty()) return
        val seriesUpdatedAt = parseEpochMs(item.latestReadDate) ?: System.currentTimeMillis()
        val chapters: Map<Int, KavitaChapterDto> =
            if (rows.any { ':' in it.id }) {
                val response = api.kavitaVolumes(item.id)
                if (!response.isSuccessful) error("volumes failed (HTTP ${response.code()})")
                response.body().orEmpty().flatMap { it.chapters }.associateBy { it.id }
            } else {
                emptyMap()
            }
        rows.forEach { row ->
            val (pagesRead, pages) = if (':' in row.id) {
                val chapter = chapters[row.id.substringAfter(':').toIntOrNull()] ?: return@forEach
                chapter.pagesRead to chapter.pages
            } else {
                item.pagesRead to item.pages
            }
            if (pages <= 0) return@forEach
            val progress = (pagesRead.toFloat() / pages).coerceIn(0f, 1f)
            if (!differs(row, progress)) return@forEach
            val finished = progress >= FINISHED_PROGRESS_THRESHOLD
            bookCache.applyRemoteProgress(
                cacheKey = row.cacheKey,
                source = row.source,
                progress = progress,
                ebookProgress = progress,
                currentTimeSec = row.currentTime,
                locatorJson = row.epubLocator,
                finished = finished,
                readStatus = if (finished) "READ" else null,
                updatedAt = seriesUpdatedAt,
            )
        }
    }

    private fun differs(row: CachedBook, progress: Float): Boolean {
        val local = row.epubProgress ?: row.readProgress
        val finished = progress >= FINISHED_PROGRESS_THRESHOLD
        return abs(local - progress) > PROGRESS_EPSILON || row.isFinished != finished
    }

    private fun stampOf(item: KavitaSeriesDto): String = "${item.pagesRead}|${item.latestReadDate.orEmpty()}"

    private fun parseEpochMs(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        val parsed = runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
        return parsed?.takeIf { it > 0L }
    }

    private fun readStamps(): Map<String, Map<String, String>> = try {
        val file = File(context.filesDir, STAMP_FILE)
        if (file.exists()) json.decodeFromString(stampSerializer, file.readText()) else emptyMap()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyMap()
    }

    private fun writeStamps(stamps: Map<String, Map<String, String>>) {
        try {
            File(context.filesDir, STAMP_FILE).writeText(json.encodeToString(stampSerializer, stamps))
        } catch (e: Exception) {
            Log.w(TAG, "stamp write failed: ${e.message}")
        }
    }
}
