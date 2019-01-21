package com.chbachman.toron.api.anilist

import com.chbachman.toron.Codable
import com.chbachman.toron.util.*
import okio.Buffer
import org.dizitart.no2.objects.Id
import org.mapdb.DataInput2
import org.mapdb.DataOutput2
import org.mapdb.Serializer

data class Score(
    val score: Int,
    val amount: Int
) {
    companion object: Serializer<Score> {
        override fun serialize(out: DataOutput2, value: Score) {
            out.writeInt(value.score)
            out.writeInt(value.amount)
        }

        override fun deserialize(input: DataInput2, available: Int): Score {
            return Score(input.readInt(), input.readInt())
        }
    }
}

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
    @Id val id: Int,
    val idMal: Int? = null,
    val title: MediaTitle,
    val coverImage: CoverImage,
    val bannerImage: String? = null,
    val format: String,
    val status: String,
    val season: String? = null,
    val synonyms: List<String>,
    val averageScore: Int? = null,
    val popularity: Int,
    val episodes: Int = 0,
    val isLocked: Boolean,
    val siteUrl: String,
    val description: String? = null,
    val stats: MediaStats
) {
    companion object: Serializer<AniList>, Codable<AniList> {
        override fun serialize(out: DataOutput2, value: AniList) {
            out.writeInt(value.id)
            out.writeIf(DataOutput2::writeInt, value.idMal)
            out.writeUTF(value.title.romaji)
            out.writeUTF(value.title.native)
            out.writeUTF(value.title.english)
            out.writeUTF(value.coverImage.extraLarge)
            out.writeUTF(value.coverImage.large)
            out.writeUTF(value.coverImage.medium)
            out.writeIf(DataOutput2::writeUTF, value.bannerImage)
            out.writeUTF(value.format)
            out.writeUTF(value.status)
            out.writeIf(DataOutput2::writeUTF, value.season)
            out.writeList(DataOutput2::writeUTF, value.synonyms)
            out.writeIf(DataOutput2::writeInt, value.averageScore)
            out.writeInt(value.popularity)
            out.writeInt(value.episodes)
            out.writeBoolean(value.isLocked)
            out.writeUTF(value.siteUrl)
            out.writeIf(DataOutput2::writeUTF, value.description)
            out.writeList(Score.writer(), value.stats.scoreDistribution)
        }

        override fun deserialize(input: DataInput2, available: Int): AniList {
            val id = input.readInt()
            val idMal = input.readIf(DataInput2::readInt)
            val title = MediaTitle(
                romaji = input.readUTF(),
                native = input.readUTF(),
                english = input.readUTF()
            )
            val coverImage = CoverImage(
                extraLarge = input.readUTF(),
                large = input.readUTF(),
                medium = input.readUTF()
            )
            val bannerImage = input.readIf(DataInput2::readUTF)
            val format = input.readUTF()
            val status = input.readUTF()
            val season = input.readIf(DataInput2::readUTF)
            val synonyms = input.readList(DataInput2::readUTF)
            val averageScore = input.readIf(DataInput2::readInt)
            val popularity = input.readInt()
            val episodes = input.readInt()
            val isLocked = input.readBoolean()
            val siteUrl = input.readUTF()
            val description = input.readIf(DataInput2::readUTF)
            val stats = MediaStats(input.readList(Score.reader()))

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
            buffer.writeString(input.status)
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