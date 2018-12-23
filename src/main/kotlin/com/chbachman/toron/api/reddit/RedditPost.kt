package com.chbachman.toron.api.reddit

import com.chbachman.toron.util.*
import org.mapdb.DataInput2
import org.mapdb.DataOutput2
import org.mapdb.Serializer
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

data class RedditPost(
    val author: String,
    val createdUtc: Long,
    val id: String,
    val isSelf: Boolean,
    val numComments: Int,
    val over18: Boolean,
    val permalink: String,
    val score: Int,
    val selftext: String? = null,
    val title: String,
    val url: String,
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

    val showTitle: String by lazy {
        title
            .deleteInside(Char::isOpening, Char::isClosing)
            .remove("Discussion", ignoreCase = true)
            .remove("Spoilers", ignoreCase = true)
            .remove("Thread", ignoreCase = true)
            .replace(Regex(":?\\s*(?:Episodes?|Ep\\.?)\\s*([\\d-]+)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+-\\s+"), " ")
            .trim()
    }

    val created: LocalDateTime
        get() = createdUtc.toUTCDate()

    val outdated: Boolean
        get() =
            when (created.daysAgo) {
                in 0..7 -> fetched.hoursAgo > 1
                in 8..31 -> fetched.daysAgo > 1
                in 32..31*6 -> fetched.daysAgo > 7
                else -> fetched.minus(created, ChronoUnit.MONTHS) > 6
            }

    fun update(post: RedditPost): RedditPost {
        return copy(
            numComments = post.numComments,
            score = post.score,
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

            return RedditPost(author, createdUTC, id, isSelf, numComments, over18, permalink, score, selfText, title, url, fetched)
        }
    }
}