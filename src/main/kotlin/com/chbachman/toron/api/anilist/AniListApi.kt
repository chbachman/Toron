package com.chbachman.toron.api.anilist

import com.beust.klaxon.Json
import com.chbachman.toron.Codable
import com.chbachman.toron.serial.repo
import com.chbachman.toron.util.*
import kotlinx.coroutines.delay
import mu.KotlinLogging
import okio.Buffer
import org.dizitart.kno2.filters.eq
import org.dizitart.no2.IndexType
import org.dizitart.no2.objects.Index
import org.dizitart.no2.objects.Indices
import org.mapdb.DataInput2
import org.mapdb.DataOutput2
import org.mapdb.Serializer

@Indices(Index(value = "search", type = IndexType.Fulltext))
data class AniListSearch(
    val search: String,
    val media: List<Int>
) {
    companion object: Serializer<AniListSearch>, Codable<AniListSearch> {
        override fun write(input: AniListSearch, buffer: Buffer): Buffer =
            buffer.writeList(Buffer::writeInt, input.media)

        override fun read(buffer: Buffer): AniListSearch =
            AniListSearch("", buffer.readList(Buffer::readInt))

        override fun serialize(out: DataOutput2, value: AniListSearch) {
            out.writeList(DataOutput2::writeInt, value.media)
        }

        override fun deserialize(input: DataInput2, available: Int): AniListSearch {
            return AniListSearch("", input.readList(DataInput2::readInt))
        }
    }
}

data class AniListPage(
    val media: List<AniList>
) {
    companion object: Serializer<AniListPage> {
        override fun serialize(out: DataOutput2, value: AniListPage) {
            out.writeList(AniList.writer(), value.media)
        }

        override fun deserialize(input: DataInput2, available: Int): AniListPage {
            return AniListPage(input.readList(AniList.reader()))
        }
    }
}

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

        suspend fun search(query: String): List<AniList> = transaction { jedis ->
            val searchCache = jedis.mapOf<String, AniListSearch>("alsearch")
            val shows = jedis.mapOf<Int, AniList>("show")

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