package com.chbachman.toron

import com.chbachman.toron.api.anilist.AniList
import com.chbachman.toron.api.anilist.AniListSearch
import com.chbachman.toron.api.reddit.RedditPost
import com.chbachman.toron.serial.repo
import com.chbachman.toron.util.*
import mu.KotlinLogging
import okio.Buffer

data class Test(
    var x: Int,
    var y: Boolean
) {
    companion object: Codable<Test> {
        override fun write(input: Test, buffer: Buffer): Buffer {
            buffer.writeInt(input.x)
            buffer.writeBoolean(input.y)

            return buffer
        }

        override fun read(buffer: Buffer): Test {
            val x = buffer.readInt()
            val y = buffer.readBoolean()

            return Test(
                x = x,
                y = y
            )
        }

        @JvmStatic
        fun main(args: Array<String>) {
            val logger = KotlinLogging.logger {}

            logger.info { "Loading initial list." }

            val originalList = repo<RedditPost>()
            val coder = RedditPost

            logger.info { "Starting up. Doing initial filtering." }

            transaction { jedis ->
                val posts = jedis.mapOf<String, RedditPost>("post")

                posts.scan {
                    println(posts[it]?.score)
                }
            }

            logger.info { "Finished adding new Redis." }

            closeDB()
        }
    }
}

interface ByteCodable<T> {
    fun read(input: ByteArray): T
    fun write(input: T): ByteArray
}

interface Codable<T>: ByteCodable<T> {
    fun write(input: T, buffer: Buffer): Buffer
    fun read(buffer: Buffer): T

    override fun read(input: ByteArray): T =
        read(Buffer().write(input))

    override fun write(input: T): ByteArray =
        write(input, Buffer()).readByteArray()
}

