package com.chbachman.toron.api.anilist

import com.beust.klaxon.Json
import com.chbachman.toron.util.*
import kotlinx.coroutines.delay
import mu.KotlinLogging
import okio.Buffer

data class AniListSearch(
    val search: String,
    val media: List<Int>
) {
    companion object: Codable<AniListSearch> {
        override fun write(input: AniListSearch, buffer: Buffer): Buffer =
            buffer.writeList(Buffer::writeInt, input.media)

        override fun read(buffer: Buffer): AniListSearch =
            AniListSearch("", buffer.readList(Buffer::readInt))
    }
}

data class AniListPage(val media: List<AniList>)

private data class GraphQLDataPage(
    @Json("Page")
    val page: AniListPage
)

private data class GraphQLData(
    val data: GraphQLDataPage
)

class AniListApi {
    companion object {
        private val logger = KotlinLogging.logger {}

        private val graphQLQuery = GraphQLQuery(
            loadResource("series_search.gql"),
            "https://graphql.anilist.co"
        )

        suspend fun search(query: String): List<AniList> = transaction {
            val searchCache = mapOf<String, AniListSearch>("alsearch")
            val shows = mapOf<Int, AniList>("show")

            val cached = searchCache[query]

            if (cached == null) {
                logger.debug { "Loading `$query`" }
                delay(1000)

                val response = graphQLQuery.get<String>(mapOf("query" to query))
                val result = response.parseJSON<GraphQLData>()!!.data.page

                // Add to the stored list.
                result.media.forEach { shows[it.id] = it }

                // Store the IDs
                val search = result.media.map { it.id }

                searchCache[query] = AniListSearch(query, search)
                return result.media
            } else {
                return shows[cached.media]
            }
        }

        fun byId(id: Int): AniList? {
            return null
//            return anilist.find(AniList::id eq id).firstOrNull()
        }

        fun byMALId(id: Int): AniList? {
            return null
//            return anilist.find(AniList::idMal eq id).firstOrNull()
        }
    }
}