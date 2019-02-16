package com.chbachman.toron.api.anilist

import com.chbachman.toron.util.*
import okio.Buffer
import java.time.LocalDateTime

data class Score(
    val score: Int,
    val amount: Int
)

data class MediaStats(
    val scoreDistribution: List<Score>
)

data class MediaTitle(
    val romaji: String,
    val native: String = romaji,
    val english: String = romaji
)

data class CoverImage(
    val medium: String,
    val large: String,
    val extraLarge: String
)

data class AniList(
    val id: Int,
    val idMal: Int? = null,
    val title: MediaTitle,
    val coverImage: CoverImage,
    val bannerImage: String? = null,
    val format: String,
    val status: String?,
    val season: String? = null,
    val synonyms: List<String>,
    val averageScore: Int? = null,
    val popularity: Int,
    val episodes: Int = 0,
    val isLocked: Boolean,
    val siteUrl: String,
    val description: String? = null,
    val stats: MediaStats,
    val retrieved: LocalDateTime = LocalDateTime.now()
) {
    companion object: Codable<AniList> {
        override fun write(input: AniList, buffer: Buffer): Buffer {
            buffer.writeInt(input.id)
            buffer.writeIf(Buffer::writeInt, input.idMal)
            buffer.writeString(input.title.romaji)
            buffer.writeString(input.title.native)
            buffer.writeString(input.title.english)
            buffer.writeString(input.coverImage.extraLarge)
            buffer.writeString(input.coverImage.large)
            buffer.writeString(input.coverImage.medium)
            buffer.writeIf(Buffer::writeString, input.bannerImage)
            buffer.writeString(input.format)
            buffer.writeString(input.status ?: "NOT_YET_RELEASED")
            buffer.writeIf(Buffer::writeString, input.season)
            buffer.writeList(Buffer::writeString, input.synonyms)
            buffer.writeIf(Buffer::writeInt, input.averageScore)
            buffer.writeInt(input.popularity)
            buffer.writeInt(input.episodes)
            buffer.writeBoolean(input.isLocked)
            buffer.writeString(input.siteUrl)
            buffer.writeIf(Buffer::writeString, input.description)
            buffer.writeList({ it, score ->
                it.writeInt(score.score).writeInt(score.amount)
            }, input.stats.scoreDistribution)
            buffer.writeLong(input.retrieved.toUTC())

            return buffer
        }

        override fun read(buffer: Buffer): AniList {
            val id = buffer.readInt()
            val idMal = buffer.readIf(Buffer::readInt)
            val title = MediaTitle(
                romaji = buffer.readString(),
                native = buffer.readString(),
                english = buffer.readString()
            )
            val coverImage = CoverImage(
                extraLarge = buffer.readString(),
                large = buffer.readString(),
                medium = buffer.readString()
            )
            val bannerImage = buffer.readIf(Buffer::readString)
            val format = buffer.readString()
            val status = buffer.readString()
            val season = buffer.readIf(Buffer::readString)
            val synonyms = buffer.readList(Buffer::readString)
            val averageScore = buffer.readIf(Buffer::readInt)
            val popularity = buffer.readInt()
            val episodes = buffer.readInt()
            val isLocked = buffer.readBoolean()
            val siteUrl = buffer.readString()
            val description = buffer.readIf(Buffer::readString)
            val stats = MediaStats(buffer.readList {
                Score(score = it.readInt(), amount = it.readInt())
            })

            return AniList(
                id = id,
                idMal = idMal,
                title = title,
                coverImage = coverImage,
                bannerImage = bannerImage,
                format = format,
                status = status,
                season = season,
                synonyms = synonyms,
                averageScore = averageScore,
                popularity = popularity,
                episodes = episodes,
                isLocked = isLocked,
                siteUrl = siteUrl,
                description = description,
                stats = stats
            )
        }
    }
}