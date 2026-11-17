package com.enve.app.data.repository

import android.content.Context
import android.util.Log
import com.enve.app.data.remote.dto.KavitaSeriesDto
import com.enve.app.data.remote.dto.KavitaSeriesMetadataDto
import com.enve.app.data.remote.dto.KavitaVolumeDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "KavitaDetailsCache"
private const val MAX_AGE_MS = 12 * 60 * 60 * 1000L

@Serializable
data class KavitaCachedDetails(
    val seriesId: Int,
    val stamp: String,
    val savedAt: Long,
    val metadata: KavitaSeriesMetadataDto? = null,
    val volumes: List<KavitaVolumeDto> = emptyList(),
)

@Singleton
class KavitaDetailsCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(KavitaCachedDetails.serializer())

    fun stampOf(series: KavitaSeriesDto): String =
        "${series.pages}|${series.pagesRead}|${series.lastChapterAddedUtc.orEmpty()}|${series.latestReadDate.orEmpty()}"

    fun load(connectionId: String?): Map<Int, KavitaCachedDetails> = try {
        val file = fileFor(connectionId)
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        if (file.exists()) {
            json.decodeFromString(serializer, file.readText()).filter { it.savedAt >= cutoff }.associateBy { it.seriesId }
        } else {
            emptyMap()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "load failed: ${e.message}")
        emptyMap()
    }

    fun save(connectionId: String?, entries: Collection<KavitaCachedDetails>) {
        try {
            fileFor(connectionId).writeText(json.encodeToString(serializer, entries.toList()))
        } catch (e: Exception) {
            Log.w(TAG, "save failed: ${e.message}")
        }
    }

    private fun fileFor(connectionId: String?): File {
        val dir = File(context.filesDir, "kavita-details").apply { mkdirs() }
        return File(dir, "${connectionId ?: "default"}.json")
    }
}
