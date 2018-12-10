package com.chbachman.toron.api.pushshift

import com.chbachman.toron.api.reddit.RedditPost
import com.chbachman.toron.util.*
import org.mapdb.DataInput2
import org.mapdb.DataOutput2
import org.mapdb.Serializer
import java.time.LocalDate

data class PushShift(
    val author: String,
    val created_utc: Long,
    val full_link: String,
    val id: String,
    val is_self: Boolean,
    val num_comments: Int,
    val over_18: Boolean,
    val permalink: String,
    val score: Int,
    val selftext: String? = null,
    val title: String,
    val url: String,
    val fetched: LocalDate = LocalDate.now()
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

    val created: LocalDate
        get() = created_utc.toUTCDate().toLocalDate()

    suspend fun update(): PushShift {
        val post = RedditPost(id).data()

        return PushShift(
            author,
            created_utc,
            full_link,
            id,
            is_self,
            post.num_comments,
            over_18,
            permalink,
            post.score,
            selftext,
            title,
            url,
            LocalDate.now()
        )
    }

    companion object: Serializer<PushShift> {
        override fun serialize(out: DataOutput2, value: PushShift) {
            out.writeUTF(value.author)
            out.writeLong(value.created_utc)
            out.writeUTF(value.full_link)
            out.writeUTF(value.id)
            out.writeInt(value.num_comments)
            out.writeUTF(value.permalink)
            out.writeInt(value.score)
            out.writeUTF(value.title)
            out.writeUTF(value.url)
            out.writeBoolean(value.is_self)
            out.writeBoolean(value.over_18)
            out.writeBoolean(value.selftext != null)
            out.writeLong(value.fetched.toEpochDay())

            if (value.selftext != null) {
                out.writeUTF(value.selftext)
            }
        }

        override fun deserialize(source: DataInput2, available: Int): PushShift {
            val author = source.readUTF()
            val createdUTC = source.readLong()
            val fullLink = source.readUTF()
            val id = source.readUTF()
            val numComments = source.readInt()
            val permalink = source.readUTF()
            val score = source.readInt()
            val title = source.readUTF()
            val url = source.readUTF()
            val isSelf = source.readBoolean()
            val over18 = source.readBoolean()
            val selfTextExists = source.readBoolean()
            val fetched = LocalDate.ofEpochDay(source.readLong())

            val selfText =
                if (selfTextExists) {
                    source.readUTF()
                } else {
                    null
                }

            return PushShift(author, createdUTC, fullLink, id, isSelf, numComments, over18, permalink, score, selfText, title, url, fetched)
        }
    }
}