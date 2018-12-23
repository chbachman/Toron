package com.chbachman.toron

import com.chbachman.toron.api.anilist.AniList
import com.chbachman.toron.api.reddit.RedditPost
import com.chbachman.toron.serial.dbSet
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
import java.io.File

val homeDir = File(System.getProperty("user.home"), "toron")

data class GroupedData(
    val showInfo: AniList?,
    val discussion: List<RedditPost>
)

fun main(args: Array<String>) = runBlocking<Unit> {
    val originalList = dbSet<RedditPost>("reddit")
    val list = originalList
        .asSequence()
        .filter { it.numComments > 0 }
        .filter { it.score > 0 }
        .filter { it.isSelf }
        .filter { it.title.contains(Regex("\\d")) }
        .filter { it.episode != null }
        .filterNot { it.title.contains("watch", ignoreCase = true) }
        .toList()

    val grouped = list
        .asSequence()
        .groupBy { it.showTitle }
        .toList()
        .map { it.first to it.second.sortedBy { it.episode?.first } }
        .filter { it.second.size > 20 }
        // Pick the episode discussion with a higher score if we have two.
        .map { (title, episodes) ->
            title to episodes
                .groupBy { it.episode }
                .mapNotNull { (_, episodes) ->
                    episodes.maxBy { it.score }
                }
        }
        .map { GroupedData(AniList.search(it.first).firstOrNull(), it.second) }
        .filter { it.showInfo != null }
        //.sortedByDescending { it.discussion.size }

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
                    call.respond(grouped.map { it.showInfo })
                }
                get("/show/{id}") {
                    val id = call.parameters["id"]?.toInt()
                    val result = grouped.find { show -> show.showInfo?.id == id }

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