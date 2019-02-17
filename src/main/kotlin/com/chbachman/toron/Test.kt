package com.chbachman.toron

import com.chbachman.toron.api.reddit.RedditPost
import com.chbachman.toron.jedis.closeDB
import com.chbachman.toron.jedis.transaction
import com.chbachman.toron.link.linker
import com.chbachman.toron.util.*
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.File

class Test {
    companion object {
        private val logger = KotlinLogging.logger {}

        @JvmStatic
        fun main(args: Array<String>) = runBlocking<Unit> {
            val ids = File(loadResource("temp.txt").toURI()).readLines().mapNotNull { it.toIntOrNull() }
            linker { linker ->
                val original = ids
                    .map { linker[it] }
                    .filterNotNull()
                    .flatten()
                    .toList()

                val count = original
                    .filterNot { isDiscussion(it) }
                    .toList()

                println("Count: ${count.size}. Should be 0.")
                count.take(10).forEach { println("https://www.reddit.com" + it.permalink) }
            }

            transaction {
                val posts = redditPosts()

                val count = posts.values
                    .filter { it.numComments > 2 }
                    .filter { it.score > 1 }
                    .filter { it.isSelf }
                    .filter { it.episode != null }
                    .filter { it.title.contains("\\d".toRegex()) }
                    .filterNot { it.selftext?.deleteInside(Char::isOpening, Char::isClosing).isNullOrBlank() }
                    .filter { it.show.await() == null }
                    .filter { isDiscussion(it) }
                    .toList()

                println("Count should be 0: ${count.size}")

                count.take(10).forEach { println("https://www.reddit.com" + it.permalink) }
            }
            closeDB()
        }

        fun isDiscussion(post: RedditPost): Boolean {
            val title = post.title.trim()

            if (!title.contains("Discussion", ignoreCase = true)) {
                return false
            }

            return true
        }

        fun testPost(post: RedditPost) {
            if (!(post.numComments > 2))
                logger.debug { "Comments are two low: ${post.numComments} > 2" }
            if (!(post.score > 1))
                logger.debug { "Score is two low: ${post.score} > 1" }
            if (!post.isSelf)
                logger.debug { "Post is not a self post." }
            if (post.episode == null)
                logger.debug { "Post does not have an episode." }
            if (!post.title.contains("\\d".toRegex()))
                logger.debug { "Post does not contain a number." }
            if (post.selftext?.deleteInside(Char::isOpening, Char::isClosing).isNullOrBlank())
                logger.debug { "Post does not have a selftext." }
        }
    }
}