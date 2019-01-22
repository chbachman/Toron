package com.chbachman.toron

import com.chbachman.toron.api.anilist.AniList
import com.chbachman.toron.api.anilist.AniListSearch
import com.chbachman.toron.api.reddit.RedditPost
import com.chbachman.toron.util.*
import mu.KotlinLogging
import okio.Buffer
import kotlin.system.measureTimeMillis

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

            val coder = RedditPost

            logger.info { "Starting up. Doing initial filtering." }

            transaction {
                val posts = mapOf<Int, AniList>("show")

                posts.forEach { println(it) }
            }

            logger.info { "Finished adding new Redis." }

            closeDB()
        }
    }
}

