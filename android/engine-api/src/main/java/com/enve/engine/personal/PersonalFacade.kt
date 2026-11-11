package com.enve.engine.personal

import com.enve.core.data.model.Book
import kotlinx.coroutines.flow.Flow

data class PersonalBookState(
    val stars: Int = 0,
    val spice: Int = 0,
    val splash: Int = 0,
    val favorite: Boolean = false,
)

interface PersonalFacade {
    val bookStates: Flow<Map<String, PersonalBookState>>
    val favoriteSeries: Flow<Set<String>>
    val favoriteAuthors: Flow<Set<String>>

    suspend fun setStars(book: Book, stars: Int)
    suspend fun setSpice(book: Book, spice: Int)
    suspend fun incrementSplash(book: Book)
    suspend fun setFavorite(book: Book, favorite: Boolean)
    suspend fun setSeriesFavorite(name: String, favorite: Boolean)
    suspend fun setAuthorFavorite(name: String, favorite: Boolean)
    suspend fun syncNow()
}
