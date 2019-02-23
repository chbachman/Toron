package com.chbachman.toron.util

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import java.net.URL

suspend inline fun <reified T> GraphQLQuery.get(variables: Map<String, Any>): T =
    HttpClient().use { it.post<T>(url, body = content(variables)) }

data class GraphQLQuery(
    val query: String,
    val url: String
) {
    constructor(file: URL, url: String): this(
        file.readText().replace("\n".toRegex(), ""),
        url
    )

    fun content(variables: Map<String, Any>): TextContent {
        val variablesStr = variables
            .map { it.key to it.value.toString().replace("\"", "\\\"") }
            .joinToString(",") { (name, value) ->
                """ "$name": "$value" """
            }

        return TextContent(
            """ {"query": "$query", "variables": {$variablesStr}} """,
            contentType = ContentType.Application.Json
        )
    }
}