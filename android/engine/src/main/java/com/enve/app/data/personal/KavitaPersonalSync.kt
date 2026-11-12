package com.enve.app.data.personal

import android.util.Log
import com.enve.app.data.remote.GrimmoryApi
import com.enve.app.data.remote.dto.KavitaCreateReadingListDto
import com.enve.app.data.remote.dto.KavitaRatingDto
import com.enve.app.data.remote.dto.KavitaReadingListDto
import com.enve.app.data.remote.dto.KavitaSeriesIdsDto
import com.enve.app.data.remote.dto.KavitaUpdateReadingListDto
import com.enve.app.data.repository.kavitaChapterId
import com.enve.app.data.sync.ConnectionHealth
import com.enve.app.data.sync.isAuthFailure
import com.enve.app.data.sync.requireOk
import com.enve.app.data.repository.kavitaSeriesId
import com.enve.core.data.local.BookPersonal
import com.enve.core.data.local.ConnectionRegistry
import com.enve.core.data.local.PersonalFavorite
import com.enve.core.data.local.PersonalFavoriteKind
import com.enve.core.data.local.PersonalLibraryDao
import com.enve.core.data.model.BookSource
import com.enve.core.data.remote.ConnectionScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "KavitaPersonalSync"
private const val LIST_TITLE = "Enve Data"
private const val PUSH_DEBOUNCE_MS = 3_000L

@Singleton
class KavitaPersonalSync @Inject constructor(
    private val api: GrimmoryApi,
    private val dao: PersonalLibraryDao,
    private val connectionRegistry: ConnectionRegistry,
    private val health: ConnectionHealth,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()
    private var pushJob: Job? = null

    fun pushSoon() {
        pushJob?.cancel()
        pushJob = scope.launch {
            delay(PUSH_DEBOUNCE_MS)
            syncAll()
        }
    }

    suspend fun syncAll() {
        val connections = connectionRegistry.connections.first()
            .filter { it.enabled && it.source == BookSource.KAVITA }
        mutex.withLock {
            connections.forEach { connection ->
                try {
                    withContext(ConnectionScope.asContextElement(connection.id)) { sync(connection.id) }
                    health.markHealthy(connection.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "sync failed for ${connection.id}: ${e.message}")
                    if (e.isAuthFailure()) health.markRejected(connection.id)
                }
            }
        }
    }

    suspend fun mirrorStars(bookId: String, stars: Int) {
        try {
            val body = KavitaRatingDto(
                seriesId = kavitaSeriesId(bookId),
                chapterId = kavitaChapterId(bookId),
                userRating = stars.toFloat(),
                rating = stars.toFloat(),
            )
            if (body.chapterId == null) api.kavitaRateSeries(body) else api.kavitaRateChapter(body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "star mirror failed: ${e.message}")
        }
    }

    private suspend fun sync(connectionId: String) {
        val list = findOrCreateList() ?: return
        val remote = PersonalDocumentCodec.decode(list.summary)
        val localRows = dao.booksForConnection(BookSource.KAVITA.name, connectionId).associateBy { it.bookId }
        val favorites = dao.favorites().associateBy { it.kind to it.name }

        val mergedBooks = remote.books.map { (bookId, entry) ->
            val local = localRows[bookId] ?: BookPersonal(
                bookKey = "$connectionId:$bookId",
                source = BookSource.KAVITA.name,
                connectionId = connectionId,
                bookId = bookId,
            )
            PersonalMerge.book(local, entry)
        }
        val mergedFavorites = mergeFavorites(PersonalFavoriteKind.SERIES, remote.series, favorites) +
            mergeFavorites(PersonalFavoriteKind.AUTHOR, remote.authors, favorites)
        dao.upsertBooks(mergedBooks)
        dao.upsertFavorites(mergedFavorites)

        val rows = dao.booksForConnection(BookSource.KAVITA.name, connectionId)
        val all = dao.favorites()
        val document = PersonalDocument(
            books = rows.filterNot(PersonalMerge::isDefault).associate { it.bookId to PersonalMerge.entry(it) },
            series = all.filter { it.kind == PersonalFavoriteKind.SERIES }
                .associate { it.name to PersonalFlagEntry(it.active, it.updatedAt) },
            authors = all.filter { it.kind == PersonalFavoriteKind.AUTHOR }
                .associate { it.name to PersonalFlagEntry(it.active, it.updatedAt) },
        )
        if (document != remote) {
            val response = api.kavitaUpdateReadingList(
                KavitaUpdateReadingListDto(list.id, LIST_TITLE, PersonalDocumentCodec.encode(document)),
            )
            response.requireOk("reading list update")
        }
        mirrorFavorites(rows)
    }

    private fun mergeFavorites(
        kind: String,
        remote: Map<String, PersonalFlagEntry>,
        local: Map<Pair<String, String>, PersonalFavorite>,
    ): List<PersonalFavorite> = remote.map { (name, entry) ->
        PersonalMerge.favorite(local[kind to name], kind, name, entry)
    }

    private suspend fun mirrorFavorites(rows: List<BookPersonal>) {
        rows.filter { ":" !in it.bookId }.forEach { row ->
            val body = KavitaSeriesIdsDto(listOf(kavitaSeriesId(row.bookId)))
            if (row.favorite) api.kavitaWantToReadAdd(body) else if (row.updatedAt > 0L) api.kavitaWantToReadRemove(body)
        }
    }

    private suspend fun findOrCreateList(): KavitaReadingListDto? {
        findList()?.let { return it }
        val response = api.kavitaCreateReadingList(KavitaCreateReadingListDto(LIST_TITLE))
        return response.bodyOrNull()
    }

    private suspend fun findList(): KavitaReadingListDto? {
        val response = api.kavitaReadingLists()
        response.requireOk("reading lists")
        return response.body().orEmpty().firstOrNull { it.title == LIST_TITLE }
    }

    private fun <T> Response<T>.bodyOrNull(): T? = if (isSuccessful) body() else null
}
