package com.enve.core.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "book_personal")
data class BookPersonal(
    @PrimaryKey val bookKey: String,
    val source: String,
    val connectionId: String?,
    val bookId: String,
    val stars: Int = 0,
    val spice: Int = 0,
    val splash: Int = 0,
    val favorite: Boolean = false,
    val updatedAt: Long = 0L,
)

@Entity(tableName = "personal_favorite", primaryKeys = ["kind", "name"])
data class PersonalFavorite(
    val kind: String,
    val name: String,
    val active: Boolean,
    val updatedAt: Long,
)

object PersonalFavoriteKind {
    const val SERIES = "SERIES"
    const val AUTHOR = "AUTHOR"
}

@Dao
interface PersonalLibraryDao {
    @Query("SELECT * FROM book_personal")
    fun observeBooks(): Flow<List<BookPersonal>>

    @Query("SELECT * FROM book_personal WHERE bookKey = :bookKey")
    suspend fun book(bookKey: String): BookPersonal?

    @Query("SELECT * FROM book_personal WHERE source = :source AND connectionId = :connectionId")
    suspend fun booksForConnection(source: String, connectionId: String): List<BookPersonal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBook(row: BookPersonal)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBooks(rows: List<BookPersonal>)

    @Query("SELECT * FROM personal_favorite")
    fun observeFavorites(): Flow<List<PersonalFavorite>>

    @Query("SELECT * FROM personal_favorite")
    suspend fun favorites(): List<PersonalFavorite>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(row: PersonalFavorite)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorites(rows: List<PersonalFavorite>)
}
