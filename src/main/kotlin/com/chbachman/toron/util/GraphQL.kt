package com.chbachman.toron.util

import io.ktor.content.TextContent
import io.ktor.http.ContentType
import java.net.URL

data class GraphQLQuery(
    val query: String,
    val url: String
) {
    constructor(file: URL, url: String): this(
        file.readText().replace("\n".toRegex(), ""),
        url
    )

    fun content(variables: Map<String, String>): TextContent {
        val variablesStr = variables
            .map { it.key to it.value.replace("\"", "\\\"") }
            .joinToString(",") { (name, value) ->
                """ "$name": "$value" """
            }

        return TextContent(
            """ {"query": "$query", "variables": {$variablesStr}} """,
            contentType = ContentType.Application.Json
        )
    }
}