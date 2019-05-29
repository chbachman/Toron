package com.chbachman.toron.api.reddit

import com.chbachman.toron.jedis.manualTransaction
import com.chbachman.toron.jedis.pipeline
import com.chbachman.toron.jedis.transaction
import com.chbachman.toron.link.Linker
import com.chbachman.toron.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.time.LocalDateTime
import kotlin.concurrent.fixedRateTimer

class RedditCache {
    companion object {
        private val logger = KotlinLogging.logger {}

        fun start() {
            // Run every 10 minutes.
            fixedRateTimer(name = "Update Reddit", period = 10 * 60 * 1000) {
                logger.info { "Starting Reddit update." }

                runBlocking {
                    try {
                        addNewFast()
                        updateFast()
                        cleanup()
                        Linker.invalidate()
                    } catch (err: Exception) {
                        logger.error(err) { "Unable to update Reddit. Exiting this attempt." }
                    }
                }

                logger.info { "Finished Reddit update." }
            }
        }

        private fun cleanup() {
            val jedis = manualTransaction()
            logger.debug { "Started cleaning up posts." }

            var removedCount = 0
            jedis.redditPosts().scanValuesGroup { posts ->
                val removed = posts.asSequence()
                    .filter { it.created.monthsAgo > 7 }
                    .filterNot { it.numComments > 2 }
                    .filterNot { it.score > 1 }
                    .filterNot { it.isSelf }
                    .map { it.id }
                    .toList()

                removedCount += removed.size

                delete(removed)
            }

            logger.debug { "Deleted $removedCount elements." }
            jedis.close()
        }

        // This pulls from PushShift.
        // This guarantees that we will never miss any posts after the most recent.
        private suspend fun addNew(
            start: LocalDateTime,
            end: LocalDateTime = LocalDateTime.now()
        ) = transaction {
            var after = start.toUTC()

            pipeline {
                val posts = redditPosts()
                while (true) {
                    logger.debug { "Fetching Date: ${after.toUTCDate()}" }

                    val fetched = PushShiftApi.getData(after) ?: break

                    posts.set(fetched.map { it.id to it })

                    val max = fetched.maxBy { it.createdUtc }!!

                    after = max.createdUtc

                    if (max.created > end) {
                        break
                    }

                    delay(500)
                }
            }
        }

        // Pulls from Reddit.
        // Since this one starts at the latest and goes backwards it can miss posts.
        private suspend fun addNewFast() {
            var result = RedditApi.getNew() ?: return
            var seenCount = 0
            var oldestDate = LocalDateTime.now()
            val newestDate = transaction { redditPosts().values.map { it.created }.max()!! }

            while (true) {
                val seen = transaction { redditPosts().anyExists(result.data.map { it.id }) }

                logger.debug { "Amount Seen Already: $seenCount, Seen this one: $seen" }
                oldestDate = minOf(result.data.map { it.created }.min()!!, oldestDate)
                logger.debug { "Oldest Date Seen So Far: $oldestDate" }

                if (seen) {
                    seenCount++

                    if (seenCount > 3) {
                        break
                    }
                }

                transaction { redditPosts().set(result.data.map { it.id to it }) }
                findLinked(result.data)
                delay(500)
                result = result.next() ?: break
            }

            logger.debug { newestDate }
            if (oldestDate > newestDate) {
                logger.debug { "Couldn't get back to where we were before." }
                addNew(newestDate, oldestDate)
            }
        }

        private suspend fun findLinked(posts: List<RedditPost>) {
            val ids = transaction {
                val redditPosts = redditPosts()
                posts
                    .asSequence()
                    .mapNotNull { it.links }
                    .flatten()
                    .filter { it.type == ServiceType.Reddit }
                    .mapNotNull { it.id }
                    .filter { !redditPosts.exists(it) }
                    .toList()
            }

            val fetched = RedditApi.update(ids)?.data ?: return

            val final = fetched.filter { it.subreddit == "anime" }.map { it.id to it }

            if (final.isNotEmpty()) {
                logger.debug { "Adding ${final.size} Reddit Posts from links" }

                transaction { redditPosts().set(final) }
            }
        }

        private suspend fun updateFast() {
            val jedis = manualTransaction()
            logger.info { "Updating Reddit Posts." }
            var count = 0
            jedis.redditPosts().scanValuesGroup(1000) { posts ->
                val outdated = posts.filter { it.outdated }

                if (outdated.isNotEmpty()) {
                    count += outdated.size
                    logger.debug { "Updated $count posts so far." }

                    val new = RedditApi.update(outdated.map { it.id })

                    if (new != null) {
                        delay(500)
                        val result = outdated
                            .zip(new.data)
                            .map { (original, update) ->
                                original.update(update)
                            }
                            .map { it.id to it }

                        set(result)
                    }
                }
            }
            logger.info { "Reddit Posts Updated." }
            jedis.close()
        }
    }
}