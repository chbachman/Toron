package com.chbachman.toron.api.pushshift

import com.chbachman.toron.api.reddit.RedditPost
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.utf8Size
import org.mapdb.DataInput2
import org.mapdb.DataOutput2
import org.mapdb.Serializer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.experimental.and
import kotlin.experimental.or

data class PushShiftDataHolder(
    val data: List<PushShift>
)

private fun Char.isOpening(): Boolean {
    return this == '(' || this == '[' || this == '{' || this == '<'
}

private fun Char.isClosing(): Boolean {
    return this == ')' || this == ']' || this == '}' || this == '>'
}

// Reading strings needs somewhere to stop reading.
// There isn't anything built in for that, so we get the length of the string and save it before the string.
// This way, we know the exact length of the string.
private fun BufferedSink.write(s: String): BufferedSink {
    return writeLong(s.utf8Size()).writeUtf8(s)
}

private fun BufferedSource.read(): String {
    return readUtf8(readLong())
}

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
    val fetched: LocalDate
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
        var insideBrackets = false
        title
            .filter {
                when {
                    it.isOpening() -> insideBrackets = true
                    it.isClosing() -> {
                        insideBrackets = false
                        return@filter false
                    }
                }

                !insideBrackets
            }
            .replace("Discussion", "", ignoreCase = true)
            .replace("Spoilers", "", ignoreCase = true)
            .replace("Thread", "", ignoreCase = true)
            .replace(Regex(":?\\s*(?:Episodes?|Ep\\.?)\\s*([\\d-]+)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+-\\s+"), " ")
            .trim()
    }

    val created: LocalDate
        get() {
            return LocalDateTime.ofEpochSecond(created_utc, 0, ZoneOffset.UTC).toLocalDate()
        }

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

    fun write(sink: BufferedSink) {
        sink
            .write(author)
            .writeLong(created_utc)
            .write(full_link)
            .write(id)
            .writeInt(num_comments)
            .write(permalink)
            .writeInt(score)
            .write(title)
            .write(url)
            .writeLong(fetched.toEpochDay())

        var x: Byte = 0

        // To save on not very many bytes, we store booleans in the individual bits in a byte.
        // This is putting the bits as 0 or 1 on save.
        if (is_self) { x = x or 0b00000001 }
        if (over_18) { x = x or 0b00000010 }

        // Since the selftext is optional, we want to avoid reading it if possible.
        // We save the existence as a boolean, and then save the end.
        if (selftext != null) {
            x = x or 0b00000100
            sink.writeByte(x.toInt())

            sink.write(selftext)
        } else {
            sink.writeByte(x.toInt())
        }
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

        private const val zeroByte: Byte = 0

        fun read(source: BufferedSource): PushShift {
            val author = source.read()
            val createdUTC = source.readLong()
            val fullLink = source.read()
            val id = source.read()
            val numComments = source.readInt()
            val permalink = source.read()
            val score = source.readInt()
            val title = source.read()
            val url = source.read()
            val fetched = LocalDate.ofEpochDay(source.readLong())

            val byte = source.readByte()

            val isSelf = byte and 0b00000001 != zeroByte
            val over18 = byte and 0b00000010 != zeroByte
            val selfTextExists = byte and 0b00000100 != zeroByte

            val selfText =
                if (selfTextExists) {
                    source.read()
                } else {
                    null
                }

            return PushShift(author, createdUTC, fullLink, id, isSelf, numComments, over18, permalink, score, selfText, title, url, fetched)
        }
    }
}