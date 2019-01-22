package com.chbachman.toron.api.reddit

import com.chbachman.toron.util.*
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class Service(
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
            regex.find(url)?.groupValues?.lastOrNull()?.toIntOrNull()
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

val RedditPost.created: LocalDateTime
    get() = createdUtc.toUTCDate()

val RedditPost.outdated: Boolean
    get() = when (created.daysAgo) {
        in 0..7 -> fetched.hoursAgo > 1 // One Week
        in 7..14 -> fetched.daysAgo > 1 // Two Weeks
        in 14..31 -> fetched.daysAgo > 2 // Three-Four Weeks
        in 31..31*2 -> fetched.daysAgo > 7 // 2 Months
        in 31*2..31*3 -> fetched.daysAgo > 14 // 3 Months
        in 31*3..31*6 -> fetched.daysAgo > 28 // 3-6 Months
        else -> fetched.minus(created, ChronoUnit.MONTHS) > 6 // Archived Posts
    }

val RedditPost.links: List<Service>?
    get() = if (selftext != null) {
        urlRegex.findAll(selftext).mapNotNull { it.value }.toList().map { Service(it) }
    } else {
        null
    }

class ShowTest {
    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
//            val result = list.asSequence()
//                .map { it.value }
//                .filter { it.selftext != null }
//                .filter { it.selftext != "[removed]" }
//                .filter { it.selftext != "[deleted]" }
//                .filter { it.score > 1 }
//                .filter { it.isSelf }
//                .filterNot { it.links.isNullOrEmpty() }
//                .filter { it.links?.any { it.id == null && it.type == ServiceType.AniList } ?: false }
//                .take(100)
//                .forEach { post ->
//                    println(post.url)
//
//                    post.links!!
//                        .filter { it.id == null && it.type == ServiceType.AniList }
//                        .forEach { println(it.url) }
//
////                    post.links?.forEach { link ->
////                        println("   $link -> ${categorize(link)}")
////                    }
//
//                    post.links!!
//                        .mapNotNull { service ->
//                            if (service.id != null) {
//                                when (service.type) {
//                                    ServiceType.AniList -> AniListApi.byId(service.id)
//                                    ServiceType.MyAnimeList -> AniListApi.byMALId(service.id)
//                                    else -> null
//                                }
//                            } else {
//                                null
//                            }
//                        }
//                        .forEach {
//                            println("   ${it.title.english} ${it.id}")
//                        }
//                }
//
//            println(result)
        }

        fun getID(url: String): Int? {
            val regex = "anime/(\\d*)(?:/|\$)".toRegex()

            return regex.find(url)?.groupValues?.lastOrNull()?.toIntOrNull()
        }
    }
}