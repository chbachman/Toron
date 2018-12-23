package com.chbachman.toron.api.reddit

import com.chbachman.toron.api.pushshift.retry
import com.chbachman.toron.util.parseJSON
import io.ktor.client.HttpClient
import io.ktor.client.features.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.userAgent

private const val userAgent = "kotlin:com.chbachman.toron:0.0.1"
private const val searchUrl = "https://api.reddit.com/r/anime/new"
private const val infoUrl = "https://api.reddit.com/api/info"

private val client = HttpClient().config {
    defaultRequest {
        userAgent(userAgent)
    }
}

data class RedditSearchResult(
    val data: List<RedditPost>,
    val after: String?,
    val count: Int
) {
    suspend fun next() = RedditApi.getNew(this)
}

class RedditApi {
    companion object {
        suspend fun update(list: List<String>) = retry(3) {
            val raw = client.get<String>(infoUrl) {
                parameter("id", list.joinToString(",") { "t3_$it" })
            }

            parseResponse(raw)
        }

        suspend fun getNew(after: RedditSearchResult) = retry(3) {
            val raw = client.get<String>(searchUrl) {
                parameter("count", after.count)
                parameter("after", after.after)
            }

            parseResponse(raw, after.count)
        }

        suspend fun getNew() = retry(3) {
            val raw = client.get<String>(searchUrl)

            parseResponse(raw)
        }

        private fun parseResponse(raw: String, previousCount: Int = 0): RedditSearchResult? {
            val response = raw.parseJSON<RedditSearchRequest>() ?: return null
            val data = response.data.children.map { it.data }

            if (data.isEmpty()) {
                return null
            }

            return RedditSearchResult(
                data,
                response.data.after,
                previousCount + response.data.dist
            )
        }
    }
}