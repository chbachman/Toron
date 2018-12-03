package com.chbachman.toron.api.pushshift

import com.beust.klaxon.Klaxon
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter

private val klaxon = Klaxon()
private val client = HttpClient()
private val url = "https://api.pushshift.io/reddit/submission/search"

suspend fun getData(after: Long): List<PushShift>? {
    val raw = getRawData(after) ?: return null
    return klaxon.parse<PushShiftDataHolder>(raw)?.data
}

suspend fun getRawData(after: Long): String? {
    return client.get<String>(url) {
        parameter("subreddit", "anime")
        parameter("after", after)
    }
}