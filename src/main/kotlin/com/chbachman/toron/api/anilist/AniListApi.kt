package com.chbachman.toron.api.anilist

import com.beust.klaxon.Json
import com.chbachman.toron.jedis.transaction
import com.chbachman.toron.util.*
import kotlinx.coroutines.delay
import mu.KotlinLogging
import okio.Buffer
import java.time.LocalDateTime

data class AniListSearch(
    val media: List<Int>,
    val retrieved: LocalDateTime = LocalDateTime.now()
) {
    companion object: Codable<AniListSearch> {
        override fun write(input: AniListSearch, buffer: Buffer): Buffer =
            buffer.writeList(Buffer::writeInt, input.media).writeLong(input.retrieved.toUTC())

        override fun read(buffer: Buffer): AniListSearch =
            AniListSearch(buffer.readList(Buffer::readInt), buffer.readLong().toUTCDate())
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
        private val blockSet = mutableSetOf(
            "al:21273",
            "al:99894",
            "mal:36198",
            "mal:34551",
            "mal:27821",
            "al:98203",
            "mal:34823",
            "mal:29317",
            "mal:2136",
            "mal:35338",
            "mal:23819",
            "mal:1639",
            "mal:32034",
            "mal:36186"
        )

        private val searchQuery = GraphQLQuery(
            loadResource("series_search.gql"),
            "https://graphql.anilist.co"
        )

        suspend fun search(query: String): List<AniList> {
            val cached = transaction { anilistSearches()[query] }

            return if (cached == null || cached.retrieved.hoursAgo > 1) {
                logger.debug { "Loading search:`$query` from AniList." }

                val response = searchQuery.get<String>(mapOf("query" to query))

                logger.debug { "Loaded Result from AniList." }

                val result = response.parseJSON<GraphQLData>()!!.data.page

                logger.debug { "Parsed Result from AniList." }

                transaction {
                    val searchCache = anilistSearches()
                    val shows = anilistShows()

                    // Add to the stored list.
                    result.media.forEach { shows[it.id] = it }

                    // Store the IDs
                    val search = result.media.map { it.id }

                    searchCache[query] = AniListSearch(search)
                }

                delay(1000)
                result.media
            } else {
                transaction { anilistShows()[cached.media] }
            }
        }

        private val idQuery = GraphQLQuery(
            loadResource("series_id.gql"),
            "https://graphql.anilist.co"
        )

        suspend fun byID(id: Int): AniList? {
            val blocked = blockSet.contains("al:$id")

            if (blocked) { return null }

            val cached = transaction { anilistShows()[id] }

            return if (cached == null || cached.retrieved.daysAgo > 7) {
                logger.debug { "Loading id:`$id` from AniList." }

                val response = idQuery.get<String>(mapOf("query" to id))

                logger.debug { "Loaded Result from AniList." }

                val result = response.parseJSON<GraphQLData>()!!.data.page.media.singleOrNull()

                delay(1000)

                if (result == null) {
                    blockSet.add("al:$id")
                    null
                } else {
                    transaction { anilistShows()[result.id] = result }
                    result
                }
            } else {
                cached
            }
        }

        private val idMalQuery = GraphQLQuery(
            loadResource("series_idMal.gql"),
            "https://graphql.anilist.co"
        )
        
        suspend fun byMalID(id: Int): AniList? {
            val blocked = blockSet.contains("mal:$id")

            if (blocked) { return null }

            val cached = transaction { anilistShows().getMAL(id) }

            return if (cached == null || cached.retrieved.daysAgo > 7) {
                logger.debug { "Loading malId:`$id` from AniList." }
                delay(1000)
                val response = idMalQuery.get<String>(mapOf("query" to id))
                val result = response.parseJSON<GraphQLData>()!!.data.page.media.singleOrNull()

                if (result == null) {
                    blockSet.add("mal:$id")
                    null
                } else {
                    transaction { anilistShows()[result.id] = result }
                    result
                }
            } else {
                return cached
            }
        }
    }
}