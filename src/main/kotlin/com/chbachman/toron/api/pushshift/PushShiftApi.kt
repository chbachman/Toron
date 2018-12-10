package com.chbachman.toron.api.pushshift

import com.beust.klaxon.Klaxon
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter

private data class PushShiftDataHolder(
    val data: List<PushShift>
)

private val klaxon = Klaxon()
private val client = HttpClient()
private val url = "https://api.pushshift.io/reddit/submission/search"

suspend fun getData(after: Long): List<PushShift>? = retry(3) {
    val raw = client.get<String>(url) {
        parameter("subreddit", "anime")
        parameter("after", after)
    }

    val data = klaxon.parse<PushShiftDataHolder>(raw)?.data

    if (data.isNullOrEmpty()) {
        null
    } else {
        data
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