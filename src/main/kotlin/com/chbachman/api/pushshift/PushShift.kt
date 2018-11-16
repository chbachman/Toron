package com.chbachman.api.pushshift

import com.chbachman.api.anilist.AniList
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.utf8Size
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
    val url: String
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

//    val aniList = GlobalScope.async(start = CoroutineStart.LAZY) {
//        AniList.search(showTitle)
//    }

    val size: Long by lazy {
        val sink = Buffer()
        write(sink)
        sink.size
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

    companion object {
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

            return PushShift(author, createdUTC, fullLink, id, isSelf, numComments, over18, permalink, score, selfText, title, url)
        }
    }
}