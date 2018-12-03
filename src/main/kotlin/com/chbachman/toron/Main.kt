package com.chbachman.toron

import com.chbachman.toron.api.anilist.AniList
import com.chbachman.toron.api.pushshift.PushShift
import com.chbachman.toron.api.reddit.RedditSearch
import com.chbachman.toron.api.reddit.RedditSearchPostData
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
import okio.*
import java.io.File

val homeDir = File(System.getProperty("user.home"))

class ShowPost(post: RedditSearchPostData) {
    val links: List<String> by lazy {
        val postText = post.data.selftext_html

        regex.findAll(postText)
            .map { it.groupValues[1] }
            .filter { it.startsWith("https://redd.it/") }
            .map { it.substringAfterLast('/') }
            .map { "t3_$it" }
            .distinct()
            .toList()
    }

    val title = post.data.title
    val fullname = post.data.name
    val permalink = post.data.permalink

    val showTitles: List<String> by lazy {
        // Created by a bot, so we can parse the text.
        if (post.data.author == "AutoLovepon") {
            // Since the format seems to be "Rate this episode" after Titles
            // Drop everything after that.
            val text = post.data.selftext
                .replaceAfter('#', "").dropLast(1)

            // All the titles are italicized, so we can just grab those.
            Regex("\\*(.*)\\*").findAll(text)
                .map { it.groupValues.drop(1) }
                .flatten()
                .toList()
        } else {
            listOf(post.data.title)
        }
    }

    val episode: Int? by lazy {
        if (post.data.author == "AutoLovepon") {
            Regex("Episode\\s+([\\d]+?)\\s+", option = RegexOption.IGNORE_CASE)
                .findAll(post.data.title)
                .map { it.groupValues.drop(1) }
                .flatten()
                .mapNotNull { it.toIntOrNull() }
                .firstOrNull()
        } else {
            null
        }
    }

    companion object {
        private val regex = Regex("a href=\"(.*)\"")
    }

    override fun toString(): String {
        return "ShowPost(links=$links, title='$title', fullname='$fullname')"
    }
}

data class GroupedData(
    val showInfo: AniList?,
    val discussion: List<PushShift>
)

val folder = File(homeDir, "toron/database")
val out = File(folder, "database.json")
val error = File(folder, "error.txt")
val runs = File(folder, "runs.txt")
val originalData = File(folder, "originalBinary")
val data = File(folder, "result")
val result = File(folder, "result2")

fun main(args: Array<String>) = runBlocking<Unit> {
    val originalList = loadData()
    val list = originalList
        .asSequence()
        .filter { it.num_comments > 0 }
        .filter { it.score > 0 }
        .filter { it.is_self }
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

fun writeData(file: File, list: List<PushShift>) {
    val sink = file.sink().buffer()
    list.forEach { it.write(sink) }
    sink.flush()
}

fun loadData(): List<PushShift> {
    val buffer = data.source().buffer()

    val list = mutableListOf<PushShift>()

    while (!buffer.exhausted()) {
        list.add(PushShift.read(buffer))
    }

    return list
}

suspend fun getRequestData(): RedditSearch {
    var request = RedditSearch("flair:episode", params = listOf(Pair("restrict_sr", "on")))

    repeat(10) {
        request = request.loadMore()
    }

    return request
}