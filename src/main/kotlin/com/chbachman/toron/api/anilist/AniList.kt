package com.chbachman.toron.api.anilist

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import com.chbachman.toron.serial.Cache
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import kotlinx.coroutines.delay
import java.net.URL

data class Score(
    val score: Int,
    val amount: Int
)

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
)

private data class GraphQLDataPage(
    @Json(name = "Page")
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
    val averageScore: Int = 0,
    val popularity: Int,
    val episodes: Int = 0,
    val isLocked: Boolean,
    val siteUrl: String,
    val description: String? = null,
    val stats: MediaStats
) {
    companion object {
        private val client = HttpClient()
        private val klaxon = Klaxon()

        private val gql = loadFile("series_search.gql")
            .readText()
            .replace("\n".toRegex(), "")

        private val cache = Cache.create<String, Page>("toron-anilist") { unsanitizedQuery ->
            println("Loading `$unsanitizedQuery` from AniList")
            delay(1000)
            val query = unsanitizedQuery.replace("\"", "\\\"")
            val response = client.post<String>("https://graphql.anilist.co") {
                body = TextContent("{\"query\": \"$gql\", \"variables\": {\"query\": \"$query\"}}", contentType = ContentType.Application.Json)
            }

            klaxon.parse<GraphQLData>(response)!!.data.page
        }

        suspend fun search(query: String): List<AniList> {
            return cache.get(query).media
        }
    }
}