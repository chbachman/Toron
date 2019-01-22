package com.chbachman.toron

import com.chbachman.toron.api.anilist.AniList
import com.chbachman.toron.api.anilist.AniListApi
import com.chbachman.toron.api.reddit.RedditCache
import com.chbachman.toron.api.reddit.RedditPost
import com.chbachman.toron.util.*
import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CORS
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.File

val homeDir = File(System.getProperty("user.home"), "toron")

data class GroupedData(
    val showInfo: AniList?,
    val discussion: List<RedditPost>
)

fun main(args: Array<String>) = runBlocking<Unit> {
    val logger = KotlinLogging.logger {}

    // RedditCache.start()

    transaction {
        logger.info { "Loading initial list." }
        val originalList = redditPosts()

        logger.info { "Starting up. Doing initial filtering." }

        val list = originalList.mapNotNull { originalList[it] }
            .filter { it.numComments > 2 }
            .filter { it.score > 1 }
            .filter { it.isSelf }
            .filter { it.episode != null }
            .filter { it.title.contains("\\d".toRegex()) }
            .filterNot { it.selftext?.deleteInside(Char::isOpening, Char::isClosing).isNullOrBlank() }
            .toList()

        logger.info { "Initial Filtering completed with a size of ${list.size}" }

        val grouped = list
            .asSequence()
            .groupBy { it.showTitle }
            .toList()
            .map { it.first to it.second.sortedBy { it.episode?.first } }
            .map { (title, episodes) ->
                title to episodes
                    .groupBy { it.episode }
                    .mapNotNull { (_, episodes) ->
                        episodes.maxBy { it.score }
                    }
            }.sortedByDescending { (_, post) ->
                post.sumBy { it.score }
            }

        logger.info { "Grouping Completed with a size of ${grouped.size}" }

        val searched = grouped
            .map { GroupedData(AniListApi.search(it.first).firstOrNull(), it.second) }
            .groupBy { it.showInfo?.id to it.showInfo?.season }
            .map { GroupedData(it.value.first().showInfo, it.value.flatMap { it.discussion }) }
            .filter { it.showInfo != null }
            .sortedBy { it.showInfo?.title?.english }

        logger.info { "Searching Completed with a size of ${searched.size}" }

        val server = embeddedServer(Netty, port = 8081) {
            install(ContentNegotiation) {
                jackson {
                    enable(SerializationFeature.INDENT_OUTPUT)
                }
            }
            install(CORS) {
                anyHost()
            }
            routing {
                route("/toron") {
                    get("/list") {
                        logger.info { "Fetching List" }
                        val list = searched
                            .sortedByDescending { (_, posts) -> posts.sumBy { it.score } }
                            .take(25)
                            .map { it.showInfo }

                        println(list.size)

                        call.respond(list)
                    }
                    get("/show/{id}") {
                        val id = call.parameters["id"]?.toInt()
                        val result = searched.find { show -> show.showInfo?.id == id }

                        if (result != null) {
                            call.respond(result)
                        } else {
                            call.respond(HttpStatusCode.NotFound)
                        }
                    }
                }

            }
        }
        server.start(wait = true)
    }


}