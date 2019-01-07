package com.chbachman.toron.api.reddit

import com.chbachman.toron.api.anilist.AniList
import com.chbachman.toron.util.*
import kotlinx.coroutines.CoroutineStart
import org.mapdb.DataInput2
import org.mapdb.DataOutput2
import org.mapdb.Serializer
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

        println(str)

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

    fun update(post: RedditPost): RedditPost {
        return copy(
            numComments = post.numComments,
            score = post.score,
            selftext = post.selftext,
            fetched = LocalDateTime.now()
        )
    }

    companion object: Serializer<RedditPost> {
        override fun serialize(out: DataOutput2, value: RedditPost) {
            out.writeUTF(value.author)
            out.writeLong(value.createdUtc)
            out.writeUTF(value.id)
            out.writeInt(value.numComments)
            out.writeUTF(value.permalink)
            out.writeInt(value.score)
            out.writeUTF(value.title)
            out.writeUTF(value.url)
            out.writeBoolean(value.isSelf)
            out.writeBoolean(value.over18)
            out.writeBoolean(value.selftext != null)
            out.writeLong(value.fetched.toEpochSecond(ZoneOffset.UTC))
            out.writeInt(value.fetched.nano)

            if (value.selftext != null) {
                out.writeUTF(value.selftext)
            }
        }

        override fun deserialize(source: DataInput2, available: Int): RedditPost {
            val author = source.readUTF()
            val createdUTC = source.readLong()
            val id = source.readUTF()
            val numComments = source.readInt()
            val permalink = source.readUTF()
            val score = source.readInt()
            val title = source.readUTF()
            val url = source.readUTF()
            val isSelf = source.readBoolean()
            val over18 = source.readBoolean()
            val selfTextExists = source.readBoolean()
            val utcSecond = source.readLong()
            val nano = source.readInt()
            val fetched = LocalDateTime.ofEpochSecond(utcSecond, nano, ZoneOffset.UTC)

            val selfText =
                if (selfTextExists) {
                    source.readUTF()
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