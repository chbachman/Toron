package com.chbachman.toron

import com.chbachman.toron.api.reddit.RedditPost
import com.chbachman.toron.jedis.closeDB
import com.chbachman.toron.jedis.transaction
import com.chbachman.toron.util.*
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

class Test {
    companion object {
        private val logger = KotlinLogging.logger {}

        @JvmStatic
        fun main(args: Array<String>) = runBlocking<Unit> {
            transaction {
                val redditPosts = redditPosts()
            }
            closeDB()
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