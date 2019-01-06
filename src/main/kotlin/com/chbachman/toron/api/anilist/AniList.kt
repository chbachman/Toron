package com.chbachman.toron.api.anilist

import com.beust.klaxon.Json
import com.chbachman.toron.serial.dbMap
import com.chbachman.toron.serial.transaction
import com.chbachman.toron.util.*
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import kotlinx.coroutines.delay
import mu.KotlinLogging
import org.mapdb.DataInput2
import org.mapdb.DataOutput2
import org.mapdb.Serializer
import java.net.URL

data class Score(
    val score: Int,
    val amount: Int
) {
    companion object: Serializer<Score> {
        override fun serialize(out: DataOutput2, value: Score) {
            out.writeInt(value.score)
            out.writeInt(value.amount)
        }

        override fun deserialize(input: DataInput2, available: Int): Score {
            return Score(input.readInt(), input.readInt())
        }
    }
}

data class MediaStats(
    val scoreDistribution: List<Score>
)

data class MediaTitle(
    val romaji: String,
    val native: String = romaji,
    val english: String = romaji
)

data class CoverImage(
    val medium: String,
    val large: String,
    val extraLarge: String
)

private fun loadFile(name: String): URL {
    return Thread.currentThread().contextClassLoader.getResource(name)
}

data class Page(
    val media: List<AniList>
) {
    companion object: Serializer<Page> {
        override fun serialize(out: DataOutput2, value: Page) {
            out.writeList(AniList.writer(), value.media)
        }

        override fun deserialize(input: DataInput2, available: Int): Page {
            return Page(input.readList(AniList.reader()))
        }
    }
}

private data class GraphQLDataPage(
    @Json("Page")
    val page: Page
)

private data class GraphQLData(
    val data: GraphQLDataPage
)

data class AniList(
    val id: Int,
    val idMal: Int? = null,
    val title: MediaTitle,
    val coverImage: CoverImage,
    val bannerImage: String? = null,
    val format: String,
    val status: String,
    val season: String? = null,
    val synonyms: List<String>,
    val averageScore: Int? = null,
    val popularity: Int,
    val episodes: Int = 0,
    val isLocked: Boolean,
    val siteUrl: String,
    val description: String? = null,
    val stats: MediaStats
) {
    companion object: Serializer<AniList> {
        private val logger = KotlinLogging.logger {}

        override fun serialize(out: DataOutput2, value: AniList) {
            out.writeInt(value.id)
            out.writeIf(DataOutput2::writeInt, value.idMal)
            out.writeUTF(value.title.romaji)
            out.writeUTF(value.title.native)
            out.writeUTF(value.title.english)
            out.writeUTF(value.coverImage.extraLarge)
            out.writeUTF(value.coverImage.large)
            out.writeUTF(value.coverImage.medium)
            out.writeIf(DataOutput2::writeUTF, value.bannerImage)
            out.writeUTF(value.format)
            out.writeUTF(value.status)
            out.writeIf(DataOutput2::writeUTF, value.season)
            out.writeList(DataOutput2::writeUTF, value.synonyms)
            out.writeIf(DataOutput2::writeInt, value.averageScore)
            out.writeInt(value.popularity)
            out.writeInt(value.episodes)
            out.writeBoolean(value.isLocked)
            out.writeUTF(value.siteUrl)
            out.writeIf(DataOutput2::writeUTF, value.description)
            out.writeList(Score.writer(), value.stats.scoreDistribution)
        }

        override fun deserialize(input: DataInput2, available: Int): AniList {
            val id = input.readInt()
            val idMal = input.readIf(DataInput2::readInt)
            val title = MediaTitle(
                romaji = input.readUTF(),
                native = input.readUTF(),
                english = input.readUTF()
            )
            val coverImage = CoverImage(
                extraLarge = input.readUTF(),
                large = input.readUTF(),
                medium = input.readUTF()
            )
            val bannerImage = input.readIf(DataInput2::readUTF)
            val format = input.readUTF()
            val status = input.readUTF()
            val season = input.readIf(DataInput2::readUTF)
            val synonyms = input.readList(DataInput2::readUTF)
            val averageScore = input.readIf(DataInput2::readInt)
            val popularity = input.readInt()
            val episodes = input.readInt()
            val isLocked = input.readBoolean()
            val siteUrl = input.readUTF()
            val description = input.readIf(DataInput2::readUTF)
            val stats = MediaStats(input.readList(Score.reader()))

            return AniList(
                id = id,
                idMal = idMal,
                title = title,
                coverImage = coverImage,
                bannerImage = bannerImage,
                format = format,
                status = status,
                season = season,
                synonyms = synonyms,
                averageScore = averageScore,
                popularity = popularity,
                episodes = episodes,
                isLocked = isLocked,
                siteUrl = siteUrl,
                description = description,
                stats = stats
            )
        }

        private val client = HttpClient()
        private val gql = loadFile("series_search.gql")
            .readText()
            .replace("\n".toRegex(), "")

        private val cacheMap = dbMap<String, Page>("anilist-cache")

        suspend fun search(unsanitizedQuery: String): List<AniList> = transaction {
            val cached = cacheMap[unsanitizedQuery]

            if (cached == null) {
                logger.debug { "Loading `$unsanitizedQuery`" }
                delay(1000)
                val query = unsanitizedQuery.replace("\"", "\\\"")
                val response = client.post<String>("https://graphql.anilist.co") {
                    body = TextContent(""" {"query": "$gql", "variables": {"query": "$query"}} """, contentType = ContentType.Application.Json)
                }

                val result = response.parseJSON<GraphQLData>()!!.data.page

                cacheMap[unsanitizedQuery] = result
                result.media
            } else {
                cached.media
            }
        }
    }
}