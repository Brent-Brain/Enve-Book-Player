package com.enve.hearth.library

import com.enve.core.data.model.Book
import com.enve.core.data.model.BookSource
import com.enve.engine.personal.PersonalBookState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryAdvancedFilterPolicyTest {
    @Test
    fun combinesFacetsWhileKeepingSelectionsWithinFacetInclusive() {
        val fantasy = book(
            "fantasy",
            categories = listOf("Fantasy", "Adventure"),
            language = "English",
            rating = 4.4f,
            series = "Earthsea",
        )
        val mystery = book(
            "mystery",
            categories = listOf("Mystery"),
            language = "English",
            rating = 4.2f,
        )
        val translated = book(
            "translated",
            categories = listOf("Fantasy"),
            language = "Spanish",
            rating = 4.8f,
            series = "Saga",
        )
        val filters = LibraryAdvancedFilters(
            genres = setOf("Fantasy", "Mystery"),
            languages = setOf("English"),
            minimumRating = 4f,
        )

        assertTrue(LibraryAdvancedFilterPolicy.matches(fantasy, filters))
        assertTrue(LibraryAdvancedFilterPolicy.matches(mystery, filters))
        assertFalse(LibraryAdvancedFilterPolicy.matches(translated, filters))
    }

    @Test
    fun ratingAndSeriesPresenceNarrowTogether() {
        val seriesBook = book(
            "series",
            categories = listOf("Science Fiction"),
            language = "English",
            rating = 4.5f,
            series = "The Expanse",
        )
        val standalone = book(
            "standalone",
            categories = listOf("Science Fiction"),
            language = "English",
            rating = 4.8f,
        )
        val filters = LibraryAdvancedFilters(
            minimumRating = 4.5f,
            series = SeriesFilter.IN_SERIES,
        )

        assertTrue(LibraryAdvancedFilterPolicy.matches(seriesBook, filters))
        assertFalse(LibraryAdvancedFilterPolicy.matches(standalone, filters))
    }

    @Test
    fun optionsDeduplicateCaseAndWhitespace() {
        val first = book(
            "one",
            categories = listOf(" Fantasy ", "Adventure"),
            language = "English",
        )
        val second = book(
            "two",
            categories = listOf("fantasy"),
            language = " english ",
        )

        val options = LibraryAdvancedFilterPolicy.options(listOf(first, second))

        assertEquals(2, options.genres.size)
        assertEquals(listOf("English"), options.languages)
    }

    @Test
    fun personalFiltersUseFavoritesStarsAndSpice() {
        val plain = book("plain", listOf("Romance"), "English")
        val loved = PersonalBookState(stars = 4, spice = 3, favorite = true)

        assertFalse(LibraryAdvancedFilterPolicy.matches(plain, LibraryAdvancedFilters(favoritesOnly = true)))
        assertTrue(LibraryAdvancedFilterPolicy.matches(plain, LibraryAdvancedFilters(favoritesOnly = true), loved))
        assertTrue(LibraryAdvancedFilterPolicy.matches(plain, LibraryAdvancedFilters(minimumStars = 4, minimumSpice = 3), loved))
        assertFalse(LibraryAdvancedFilterPolicy.matches(plain, LibraryAdvancedFilters(minimumStars = 5), loved))
        assertFalse(LibraryAdvancedFilterPolicy.matches(plain, LibraryAdvancedFilters(minimumSpice = 4), loved))
    }

    @Test
    fun yearRangeNeedsAPublishedDateInsideTheBounds() {
        val dated = book("dated", listOf("Romance"), "English").copy(publishedDate = "2019-05-01")
        val undated = book("undated", listOf("Romance"), "English")
        val range = LibraryAdvancedFilters(yearFrom = 2018, yearTo = 2020)

        assertTrue(LibraryAdvancedFilterPolicy.matches(dated, range))
        assertFalse(LibraryAdvancedFilterPolicy.matches(undated, range))
        assertFalse(LibraryAdvancedFilterPolicy.matches(dated, LibraryAdvancedFilters(yearFrom = 2020)))
        assertFalse(LibraryAdvancedFilterPolicy.matches(dated, LibraryAdvancedFilters(yearTo = 2018)))
    }

    private fun book(
        id: String,
        categories: List<String>,
        language: String,
        rating: Float? = null,
        series: String? = null,
    ) = Book(
        id = id,
        title = id,
        categories = categories,
        language = language,
        goodreadsRating = rating,
        seriesName = series,
        source = BookSource.LOCAL,
        connectionId = "unit",
        libraryId = "library",
    )
}
