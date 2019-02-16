package com.chbachman.toron

import com.chbachman.toron.api.anilist.AniList
import com.chbachman.toron.api.anilist.AniListApi
import com.chbachman.toron.api.reddit.RedditCache
import com.chbachman.toron.api.reddit.RedditPost
import com.chbachman.toron.jedis.closeDB
import com.chbachman.toron.jedis.transaction
import com.chbachman.toron.link.Show
import com.chbachman.toron.link.linker
import com.chbachman.toron.util.anilistSearches
import com.chbachman.toron.util.anilistShows
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
import java.lang.management.ManagementFactory

val homeDir = File(System.getProperty("user.home"), "toron")

fun main(args: Array<String>) = runBlocking<Unit> {
    val logger = KotlinLogging.logger {}

    logger.info { ManagementFactory.getRuntimeMXBean().name }


    logger.info { "Starting migration of AniList part 2." }
    transaction {
        val anilist = anilistSearches()

        anilist.scanKeysGroup { keys ->
            val values = get(keys)

            set(keys.zip(values))
        }
    }
    logger.info { "Finished Migration of AniList part 2." }

    RedditCache.start()

    transaction {
        val server = embeddedServer(Netty, port = 8081) {
            install(ContentNegotiation) { jackson() }
            install(CORS) { anyHost() }
            routing {
                route("/toron") {
                    get("/list") {
                        logger.info { "Fetching List" }

                        val topList = linker { linker ->
                            linker
                                .sortedByDescending { show -> show.threads.sumBy { it.score } / show.threads.size }
                                .take(25)
                        }

                        call.respond(topList)
                    }
                    get("/show/{id}") {
                        val result = call.parameters["id"]?.toInt()
                            ?.let { AniListApi.byID(it) }
                            ?.let { show ->
                                linker { linker -> linker[show.id]?.let { Show(show, it) } }
                            }

                        if (result != null) {
                            call.respond(result)
                        } else {
                            call.respond(HttpStatusCode.NotFound)
                        }
                    }
                    get("/search") {
                        val result = call.parameters["q"]
                            ?.let { AniListApi.search(it)}
                            ?.let { linker { linker -> it.mapNotNull { show ->
                                linker[show.id]?.let { Show(show, it) }
                            } } }

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
        closeDB()
    }
}