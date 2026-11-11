package com.enve.app.data.personal

import com.enve.core.data.local.BookPersonal
import com.enve.core.data.local.PersonalFavorite
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PersonalBookEntry(
    val s: Int = 0,
    val p: Int = 0,
    val w: Int = 0,
    val f: Boolean = false,
    val t: Long = 0L,
)

@Serializable
data class PersonalFlagEntry(
    val f: Boolean = false,
    val t: Long = 0L,
)

@Serializable
data class PersonalDocument(
    val v: Int = 1,
    val books: Map<String, PersonalBookEntry> = emptyMap(),
    val series: Map<String, PersonalFlagEntry> = emptyMap(),
    val authors: Map<String, PersonalFlagEntry> = emptyMap(),
)

object PersonalDocumentCodec {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    fun decode(raw: String?): PersonalDocument =
        raw?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<PersonalDocument>(it) }.getOrNull() }
            ?: PersonalDocument()

    fun encode(document: PersonalDocument): String = json.encodeToString(document)
}

object PersonalMerge {
    fun book(local: BookPersonal, remote: PersonalBookEntry): BookPersonal {
        val splash = maxOf(local.splash, remote.w)
        return if (remote.t > local.updatedAt) {
            local.copy(
                stars = remote.s,
                spice = remote.p,
                splash = splash,
                favorite = remote.f,
                updatedAt = remote.t,
            )
        } else {
            local.copy(splash = splash)
        }
    }

    fun favorite(local: PersonalFavorite?, kind: String, name: String, remote: PersonalFlagEntry): PersonalFavorite =
        if (local == null || remote.t > local.updatedAt) {
            PersonalFavorite(kind, name, remote.f, remote.t)
        } else {
            local
        }

    fun entry(row: BookPersonal) = PersonalBookEntry(
        s = row.stars,
        p = row.spice,
        w = row.splash,
        f = row.favorite,
        t = row.updatedAt,
    )

    fun isDefault(row: BookPersonal): Boolean =
        row.stars == 0 && row.spice == 0 && row.splash == 0 && !row.favorite
}
