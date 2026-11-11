package com.enve.app.data.personal

import com.enve.core.data.local.BookPersonal
import com.enve.core.data.local.PersonalFavorite
import com.enve.core.data.local.PersonalFavoriteKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalDocumentTest {
    private val row = BookPersonal(
        bookKey = "c1:7:8",
        source = "KAVITA",
        connectionId = "c1",
        bookId = "7:8",
        stars = 3,
        spice = 2,
        splash = 4,
        favorite = false,
        updatedAt = 100L,
    )

    @Test
    fun documentRoundTrips() {
        val document = PersonalDocument(
            books = mapOf("7:8" to PersonalBookEntry(s = 4, p = 5, w = 9, f = true, t = 200L)),
            series = mapOf("Saga" to PersonalFlagEntry(f = true, t = 50L)),
            authors = mapOf("Ann Author" to PersonalFlagEntry(f = false, t = 60L)),
        )

        assertEquals(document, PersonalDocumentCodec.decode(PersonalDocumentCodec.encode(document)))
    }

    @Test
    fun blankOrCorruptDocumentDecodesEmpty() {
        assertEquals(PersonalDocument(), PersonalDocumentCodec.decode(null))
        assertEquals(PersonalDocument(), PersonalDocumentCodec.decode("not json"))
    }

    @Test
    fun newerRemoteWinsFieldsButSplashKeepsHighest() {
        val merged = PersonalMerge.book(row, PersonalBookEntry(s = 5, p = 1, w = 2, f = true, t = 200L))

        assertEquals(5, merged.stars)
        assertEquals(1, merged.spice)
        assertTrue(merged.favorite)
        assertEquals(4, merged.splash)
        assertEquals(200L, merged.updatedAt)
    }

    @Test
    fun olderRemoteKeepsLocalButRaisesSplash() {
        val merged = PersonalMerge.book(row, PersonalBookEntry(s = 1, p = 1, w = 11, f = true, t = 50L))

        assertEquals(3, merged.stars)
        assertFalse(merged.favorite)
        assertEquals(11, merged.splash)
        assertEquals(100L, merged.updatedAt)
    }

    @Test
    fun favoriteFlagFollowsNewestTimestamp() {
        val local = PersonalFavorite(PersonalFavoriteKind.SERIES, "Saga", active = true, updatedAt = 10L)

        assertFalse(PersonalMerge.favorite(local, PersonalFavoriteKind.SERIES, "Saga", PersonalFlagEntry(false, 20L)).active)
        assertTrue(PersonalMerge.favorite(local, PersonalFavoriteKind.SERIES, "Saga", PersonalFlagEntry(false, 5L)).active)
        assertTrue(PersonalMerge.favorite(null, PersonalFavoriteKind.AUTHOR, "A", PersonalFlagEntry(true, 1L)).active)
    }
}
