package com.chbachman.toron.util

import io.ktor.client.HttpClient
import io.ktor.client.request.post

val defaultClient = HttpClient()

suspend inline fun <reified T> GraphQLQuery.get(variables: Map<String, String>): T =
    defaultClient.post(url) {
        body = content(variables)
    }