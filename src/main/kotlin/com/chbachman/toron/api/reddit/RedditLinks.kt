package com.chbachman.toron.api.reddit

data class Service(
    val url: String
) {
    val type = ServiceType.values().find { type ->
        type.domains.any { domain ->
            url.contains(domain)
        }
    } ?: ServiceType.Other

    val id = when(type) {
        ServiceType.AniList, ServiceType.MyAnimeList -> {
            val regex = "anime/(\\d*)(?:/|\$)".toRegex()
            regex.find(url)?.groupValues?.lastOrNull()
        }
        ServiceType.Reddit -> {
            val regex = "(?:comments|\\.it)/([a-z0-9]*)(?:/|\$)".toRegex()
            regex.find(url)?.groupValues?.lastOrNull()
        }
        else -> null
    }
}

enum class ServiceType(vararg val domains: String) {
    AniList("anilist.co"),
    Imgur("imgur.com"),
    Crunchyroll("crunchyroll.com"),
    Reddit("reddit.com", "redd.it"),
    MyAnimeList("myanimelist.net"),
    Kitsu("kitsu.io"),
    Funimation("funimation.com"),
    Hulu("hulu.com"),
    Other()
}

private const val urlRegexStr =
    """https?:\/\/(www\.)?[-a-zA-Z0-9@:%._\+~#=]{2,256}\.[a-z]{2,6}\b([-a-zA-Z0-9@:%_\+.~#?&//=]*)"""
private val urlRegex = urlRegexStr.toRegex(RegexOption.IGNORE_CASE)

fun getLinks(text: String) =
    urlRegex
        .findAll(text)
        .mapNotNull { it.value }
        .toList()
        .map { Service(it) }

val RedditPost.links: List<Service>?
    get() = if (selftext != null) {
        urlRegex.findAll(selftext).mapNotNull { it.value }.toList().map { Service(it) }
    } else {
        null
    }