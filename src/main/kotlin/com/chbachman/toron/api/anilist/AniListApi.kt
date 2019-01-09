package com.chbachman.toron.api.anilist

import com.beust.klaxon.Json
import com.chbachman.toron.serial.dbMap
import com.chbachman.toron.serial.transaction
import com.chbachman.toron.util.*
import kotlinx.coroutines.delay
import mu.KotlinLogging
import org.mapdb.DataInput2
import org.mapdb.DataOutput2
import org.mapdb.Serializer

data class AniListSearch(
    val search: String,
    val media: List<Int>
) {
    companion object: Serializer<AniListSearch> {
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
            loadFile("series_search.gql"),
            "https://graphql.anilist.co"
        )

        private val searchCache = dbMap<String, AniListSearch>("anilist-cache")
        private val anilist = dbMap<Int, AniList>("anilist")

        suspend fun search(query: String): List<AniList> = transaction {
            val cached = searchCache[query]

            if (cached == null) {
                logger.debug { "Loading `$query`" }
                delay(1000)

                val response = graphQLQuery.get<String>(mapOf("query" to query))
                val result = response.parseJSON<GraphQLData>()!!.data.page

                // Add to the stored list.
                result.media.forEach { anilist[it.id] = it }

                // Store the IDs
                val search = result.media.map { it.id }

                searchCache[query] = AniListSearch(query, search)
                result.media
            } else {
                cached.media.map { anilist[it]!! }
            }
        }

        fun byId(id: Int): AniList? {
            return anilist[id]
        }

        fun byMALId(id: Int): AniList? {
            return anilist.values.filterNotNull().find { it.idMal == id }
        }
    }
}