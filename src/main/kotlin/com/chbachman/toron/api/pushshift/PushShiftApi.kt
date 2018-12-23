package com.chbachman.toron.api.pushshift

import com.chbachman.toron.api.reddit.RedditPost
import com.chbachman.toron.util.parseJSON
import io.ktor.client.HttpClient
import io.ktor.client.features.BadResponseStatusException
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.userAgent

private data class PushShiftDataHolder(
    val data: List<RedditPost>
)

private val client = HttpClient()
private const val url = "https://api.pushshift.io/reddit/submission/search"
private const val urlv2 = "https://apiv2.pushshift.io/reddit/submission/search"

class PushShiftApi {
    companion object {
        suspend fun getData(after: Long): List<RedditPost>? = retry(3) {
            val raw = client.get<String>(url) {
                parameter("subreddit", "anime")
                parameter("after", after)
            }

            val data = raw.parseJSON<PushShiftDataHolder>()?.data

            if (data.isNullOrEmpty()) {
                null
            } else {
                data
            }
        }

        suspend fun update(id: List<String>) = retry(3) {
            try {
                val raw = client.get<String>(urlv2) {
                    parameter("subreddit", "anime")
                    parameter("ids", id.take(3).joinToString(","))
                    userAgent("kotlin:com.chbachman.toron:0.0.1")
                }

                val data = raw.parseJSON<PushShiftDataHolder>()?.data

                if (data.isNullOrEmpty()) {
                    null
                } else {
                    data
                }
            } catch (e: BadResponseStatusException) {
                println(e.statusCode)
                println(e.response)
                error("Error")
            }
        }
    }
}

inline fun <T> retry(times: Int, closure: () -> T?): T? {
    repeat(times) {
        val result = closure()

        if (result != null) {
            return result
        }
    }

    return null
}