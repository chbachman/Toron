package com.chbachman.api.reddit

import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.beust.klaxon.Parser
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.userAgent
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async

private val userAgent = "kotlin:com.chbachman.toron:0.0.1"
private val client = HttpClient()

class RedditPost constructor(
    val id: String
) {
    suspend fun data(): RedditCommentPost {
        val response = client.get<String>("https://api.reddit.com/comments/$id") {
            userAgent(userAgent)
        }

        val obj = (Parser().parse(response) as JsonArray<JsonObject>).first()

        return Klaxon().parseFromJsonObject<RedditCommentRequest>(obj)!!.data.children.first().data
    }
}

class RedditSearch private constructor(
    val search: String,
    val params: List<Pair<String, String>> = emptyList(),
    private val previousData: List<RedditSearchPostData>?,
    private val after: String?,
    private val previousCount: Int
) {
    constructor(
        search: String,
        params: List<Pair<String, String>> = emptyList()
    ): this(search, params, null,null, 0)

    private val asyncRequest = GlobalScope.async(start = CoroutineStart.LAZY) {
        val response = client.get<String>("https://api.reddit.com/r/anime/search") {
            userAgent(userAgent)
            params.forEach { parameter(it.first, it.second) }
            parameter("q", search)
            parameter("count", previousCount)
            parameter("after", after)
            parameter("sort", "new")
            parameter("t", "all")
            parameter("type", "sr")
        }

        Klaxon().parse<RedditSearchRequest>(response)!!
    }

    private val asyncData = GlobalScope.async(start = CoroutineStart.LAZY) {
        if (previousData == null) {
            asyncRequest.await().data.children
        } else {
            previousData + asyncRequest.await().data.children
        }
    }

    suspend fun data(): List<RedditSearchPostData> {
        return asyncData.await()
    }

    suspend fun loadMore(): RedditSearch {
        return RedditSearch(
            search,
            params,
            data(),
            asyncRequest.await().data.after,
            previousCount + asyncRequest.await().data.dist
        )
    }
}