package com.chbachman.toron.api.reddit

import com.chbachman.toron.util.parseJSONCamel
import com.chbachman.toron.util.retry
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter

private data class PushShiftDataHolder(
    val data: List<RedditPost>
)

private const val url = "https://api.pushshift.io/reddit/submission/search"
private const val urlv2 = "https://apiv2.pushshift.io/reddit/submission/search"

class PushShiftApi {
    companion object {
        suspend fun getData(after: Long): List<RedditPost>? = retry(3) {
            val raw = HttpClient().use {
                it.get<String>(url) {
                    parameter("subreddit", "anime")
                    parameter("after", after)
                }
            }

            val data = raw.parseJSONCamel<PushShiftDataHolder>()?.data

            if (data.isNullOrEmpty()) {
                null
            } else {
                data
            }
        }
    }
}