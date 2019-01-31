package com.chbachman.toron.api.anilist

import com.beust.klaxon.Json
import com.chbachman.toron.jedis.mapOf
import com.chbachman.toron.jedis.transaction
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
        private val blockSet = mutableSetOf<String>()

        private val searchQuery = GraphQLQuery(
            loadResource("series_search.gql"),
            "https://graphql.anilist.co"
        )

        suspend fun search(query: String): List<AniList> = transaction {
            val searchCache = mapOf<String, AniListSearch>("alsearch")
            val shows = anilistShows()

            val cached = searchCache[query]

            if (cached == null) {
                logger.debug { "Loading search:`$query` from AniList." }
                delay(1000)

                val response = searchQuery.get<String>(mapOf("query" to query))
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

        private val idQuery = GraphQLQuery(
            loadResource("series_id.gql"),
            "https://graphql.anilist.co"
        )

        suspend fun byID(id: Int): AniList? = transaction {
            val shows = anilistShows()
            val blocked = blockSet.contains("al:$id")

            if (blocked) { return@transaction null }

            val cached = shows[id]

            if (cached == null) {
                logger.debug { "Loading id:`$id` from AniList." }
                delay(1000)

                val response = idQuery.get<String>(mapOf("query" to id))
                val result = response.parseJSON<GraphQLData>()!!.data.page.media.singleOrNull()

                if (result == null) {
                    blockSet.add("al:$id")
                    null
                } else {
                    shows[result.id] = result
                    result
                }
            } else {
                return cached
            }
        }

        private val idMalQuery = GraphQLQuery(
            loadResource("series_idMal.gql"),
            "https://graphql.anilist.co"
        )
        
        suspend fun byMalID(id: Int): AniList? = transaction {
            val shows = anilistShows()
            val blocked = blockSet.contains("mal:$id")

            if (blocked) { return@transaction null }

            val cached = shows.getMAL(id)

            if (cached == null) {
                logger.debug { "Loading malId:`$id` from AniList." }
                delay(1000)
                val response = idMalQuery.get<String>(mapOf("query" to id))
                val result = response.parseJSON<GraphQLData>()!!.data.page.media.singleOrNull()

                if (result == null) {
                    blockSet.add("mal:$id")
                    null
                } else {
                    shows[result.id] = result
                    result
                }
            } else {
                return cached
            }
        }
    }
}