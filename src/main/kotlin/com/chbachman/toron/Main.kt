package com.chbachman.toron

import com.chbachman.toron.api.anilist.AniListApi
import com.chbachman.toron.api.reddit.RedditCache
import com.chbachman.toron.jedis.closeDB
import com.chbachman.toron.jedis.transaction
import com.chbachman.toron.link.Linker
import com.chbachman.toron.link.Show
import com.chbachman.toron.link.linker
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
import java.time.LocalDateTime

val homeDir = File(System.getProperty("user.home"), "toron")

fun main(args: Array<String>) = runBlocking<Unit> {
    val logger = KotlinLogging.logger {}

    logger.info { ManagementFactory.getRuntimeMXBean().name }

//    transaction {
//        anilistShows().scanValuesGroup { values ->
//            set(values.map { it.copy(retrieved = LocalDateTime.MIN) }.map { it.id to it })
//        }
//    }

    RedditCache.start()

    val server = embeddedServer(Netty, port = 8081) {
        install(ContentNegotiation) { jackson() }
        install(CORS) { anyHost() }
        routing {
            route("/toron") {
                get("/list") {
                    val topList = Linker.data.await().top.await()
                    println("Got Top List")

                    call.respond(topList)
                }
                get("/show/{id}") {
                    logger.debug { "Getting Show: ${call.parameters["id"]}" }
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