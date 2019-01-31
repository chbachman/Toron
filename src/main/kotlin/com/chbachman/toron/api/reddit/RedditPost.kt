package com.chbachman.toron.api.reddit

import com.chbachman.toron.api.anilist.AniList
import com.chbachman.toron.api.anilist.AniListApi
import com.chbachman.toron.util.*
import com.fasterxml.jackson.annotation.JsonIgnore
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import mu.KotlinLogging
import okio.Buffer
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

data class RedditPost @JvmOverloads constructor(
    val author: String,
    @FuzzyLong
    val createdUtc: Long,
    val id: String,
    val isSelf: Boolean,
    val numComments: Int,
    val permalink: String,
    val score: Int,
    val title: String,
    val url: String,
    val over18: Boolean = false,
    val selftext: String? = null,
    val fetched: LocalDateTime = LocalDateTime.now()
) {
    private val logger = KotlinLogging.logger {}

    val episode: IntRange? by lazy {
        val str = Regex(".*(?:Episodes?|Ep\\.?)\\s*([\\d-]+).*", RegexOption.IGNORE_CASE)
            .matchEntire(title)
            ?.groupValues
            ?.get(1) ?: return@lazy null

        if (str.contains('-')) {
            val split = str
                .split('-')
                .mapNotNull { it.toIntOrNull() }

            when {
                split.isEmpty() -> null
                split.size == 1 -> IntRange(split.first(), split.first())
                else -> IntRange(split.first(), split.last())
            }
        } else {
            IntRange(str.toInt(), str.toInt())
        }
    }

    val season: Int? by lazy {
        val str = Regex("(\\S*)\\s+(?:Season)\\s+(\\S*)", RegexOption.IGNORE_CASE)
            .matchEntire(title)
            ?.groupValues

        0
    }

    val showTitle: String by lazy {
        title
            .deleteInside(Char::isOpening, Char::isClosing)
            .remove("Discussion", ignoreCase = true)
            .remove("Spoilers", ignoreCase = true)
            .remove("Thread", ignoreCase = true)
            .replace(Regex(":?\\s*(?:Episodes?|Ep\\.?)\\s*([\\d-]+)", RegexOption.IGNORE_CASE), "")
            .remove("Final", ignoreCase = true)
            .remove("Episode", ignoreCase = true)
            .replace(Regex("\\s+-\\s+"), " ")
            .trim()
            .removeSuffix("-")
            .removeSuffix("–")
            .trim()
    }

    val rewatch: Boolean by lazy {
        title.contains("[rewatch]", ignoreCase = true)
    }

    @JsonIgnore
    val show = GlobalScope.async(start = CoroutineStart.LAZY) {
        aniListShow() ?: myAnimeListShow() ?: titleShow()
    }

    val subreddit: String
        get() {
            val trimmed = permalink.removePrefix("/r/")
            return trimmed.removeRange(trimmed.indexOf('/'), trimmed.length)
        }

    val created: LocalDateTime
        get() = createdUtc.toUTCDate()

    val outdated: Boolean
        get() = when (created.daysAgo) {
            in 0..7 -> fetched.hoursAgo > 1 // One Week
            in 7..14 -> fetched.daysAgo > 1 // Two Weeks
            in 14..31 -> fetched.daysAgo > 2 // Three-Four Weeks
            in 31..31*2 -> fetched.daysAgo > 7 // 2 Months
            in 31*2..31*3 -> fetched.daysAgo > 14 // 3 Months
            in 31*3..31*6 -> fetched.daysAgo > 28 // 3-6 Months
            else -> fetched.minus(created, ChronoUnit.MONTHS) > 6 // Archived Posts
        }

    private suspend fun aniListShow(): AniList? {
        return if (!selftext.isNullOrBlank()) {
            val links = getLinks(selftext)

            links.mapNotNull { service ->
                val id = service.id?.toIntOrNull()
                if (id != null && service.type == ServiceType.AniList) {
                    AniListApi.byID(id)
                } else {
                    null
                }
            }.maxBy {
                // The choice here for popularity is a bad one.
                // I just don't see multiple links happening in a discussion thread.
                it.popularity
            }
        } else {
            null
        }
    }

    private suspend fun myAnimeListShow(): AniList? {
        return if (!selftext.isNullOrBlank()) {
            val links = getLinks(selftext)

            links.mapNotNull { service ->
                val id = service.id?.toIntOrNull()
                if (id != null && service.type == ServiceType.MyAnimeList) {
                    AniListApi.byMalID(id)
                } else {
                    null
                }
            }.maxBy {
                // The choice here for popularity is a bad one.
                // I just don't see multiple links happening in a discussion thread.
                it.popularity
            }
        } else {
            null
        }
    }

    private suspend fun titleShow(): AniList? =
        AniListApi.search(showTitle).firstOrNull()

    fun update(post: RedditPost): RedditPost {
        return copy(
            numComments = post.numComments,
            score = post.score,
            selftext = post.selftext,
            fetched = LocalDateTime.now()
        )
    }

    companion object: Codable<RedditPost> {
        override fun write(input: RedditPost, buffer: Buffer): Buffer {
            buffer.writeString(input.author)
            buffer.writeLong(input.createdUtc)
            buffer.writeString(input.id)
            buffer.writeInt(input.numComments)
            buffer.writeString(input.permalink)
            buffer.writeInt(input.score)
            buffer.writeString(input.title)
            buffer.writeString(input.url)
            buffer.writeBoolean(input.isSelf)
            buffer.writeBoolean(input.over18)
            buffer.writeBoolean(input.selftext != null)
            buffer.writeLong(input.fetched.toEpochSecond(ZoneOffset.UTC))
            buffer.writeInt(input.fetched.nano)

            if (input.selftext != null) {
                buffer.writeString(input.selftext)
            }

            return buffer
        }

        override fun read(buffer: Buffer): RedditPost {
            val author = buffer.readString()
            val createdUTC = buffer.readLong()
            val id = buffer.readString()
            val numComments = buffer.readInt()
            val permalink = buffer.readString()
            val score = buffer.readInt()
            val title = buffer.readString()
            val url = buffer.readString()
            val isSelf = buffer.readBoolean()
            val over18 = buffer.readBoolean()
            val selfTextExists = buffer.readBoolean()
            val utcSecond = buffer.readLong()
            val nano = buffer.readInt()
            val fetched = LocalDateTime.ofEpochSecond(utcSecond, nano, ZoneOffset.UTC)

            val selfText =
                if (selfTextExists) {
                    buffer.readString()
                } else {
                    null
                }

            return RedditPost(
                author = author,
                createdUtc = createdUTC,
                id = id,
                isSelf = isSelf,
                numComments = numComments,
                permalink = permalink,
                score = score,
                title = title,
                url = url,
                over18 = over18,
                selftext = selfText,
                fetched = fetched
            )
        }
    }
}