package com.enve.app.hearth

import com.enve.app.data.personal.KavitaPersonalSync
import com.enve.core.data.local.BookPersonal
import com.enve.core.data.local.PersonalFavorite
import com.enve.core.data.local.PersonalFavoriteKind
import com.enve.core.data.local.PersonalLibraryDao
import com.enve.core.data.model.Book
import com.enve.core.data.model.BookSource
import com.enve.core.data.remote.ConnectionScope
import com.enve.engine.personal.PersonalBookState
import com.enve.engine.personal.PersonalFacade
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonalFacadeImpl @Inject constructor(
    private val dao: PersonalLibraryDao,
    private val kavitaSync: KavitaPersonalSync,
) : PersonalFacade {

    override val bookStates: Flow<Map<String, PersonalBookState>> = dao.observeBooks().map { rows ->
        rows.associate { it.bookKey to PersonalBookState(it.stars, it.spice, it.splash, it.favorite) }
    }

    override val favoriteSeries: Flow<Set<String>> = favoriteNames(PersonalFavoriteKind.SERIES)

    override val favoriteAuthors: Flow<Set<String>> = favoriteNames(PersonalFavoriteKind.AUTHOR)

    override suspend fun setStars(book: Book, stars: Int) {
        val clamped = stars.coerceIn(0, 5)
        update(book) { it.copy(stars = clamped) }
        if (book.source == BookSource.KAVITA) {
            withContext(ConnectionScope.asContextElement(book.connectionId.orEmpty())) {
                kavitaSync.mirrorStars(book.id, clamped)
            }
        }
    }

    override suspend fun setSpice(book: Book, spice: Int) = update(book) { it.copy(spice = spice.coerceIn(0, 5)) }

    override suspend fun incrementSplash(book: Book) = update(book) { it.copy(splash = it.splash + 1) }

    override suspend fun setFavorite(book: Book, favorite: Boolean) = update(book) { it.copy(favorite = favorite) }

    override suspend fun setSeriesFavorite(name: String, favorite: Boolean) =
        setFacet(PersonalFavoriteKind.SERIES, name, favorite)

    override suspend fun setAuthorFavorite(name: String, favorite: Boolean) =
        setFacet(PersonalFavoriteKind.AUTHOR, name, favorite)

    override suspend fun syncNow() = kavitaSync.syncAll()

    private fun favoriteNames(kind: String): Flow<Set<String>> = dao.observeFavorites().map { rows ->
        rows.asSequence().filter { it.kind == kind && it.active }.map { it.name }.toSet()
    }

    private suspend fun update(book: Book, change: (BookPersonal) -> BookPersonal) {
        val current = dao.book(book.uniqueKey) ?: BookPersonal(
            bookKey = book.uniqueKey,
            source = book.source.name,
            connectionId = book.connectionId,
            bookId = book.id,
        )
        dao.upsertBook(change(current).copy(updatedAt = System.currentTimeMillis()))
        kavitaSync.pushSoon()
    }

    private suspend fun setFacet(kind: String, name: String, favorite: Boolean) {
        val clean = name.trim().takeIf { it.isNotEmpty() } ?: return
        dao.upsertFavorite(PersonalFavorite(kind, clean, favorite, System.currentTimeMillis()))
        kavitaSync.pushSoon()
    }
}
