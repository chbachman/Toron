package com.chbachman.toron.api.reddit

import com.chbachman.toron.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.time.LocalDateTime
import kotlin.concurrent.fixedRateTimer

class RedditCache {
    companion object {
        private val logger = KotlinLogging.logger {}

        suspend fun start() {
            // Run every 10 minutes.
            fixedRateTimer(name = "Update Reddit", period = 10 * 60 * 1000) {
                logger.info { "Starting Reddit update." }

                runBlocking {
                    // addNewFast()
                    updateFast()
                    cleanup()
                }

                logger.info { "Finished Reddit update." }
            }
        }

        private fun cleanup() = transaction {
            logger.debug { "Started cleaning up posts." }

            var removedCount = 0
            redditPosts().scanValuesGroup { posts ->
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

                    delay(200)
                }
            }
        }

        // Pulls from Reddit.
        // Since this one starts at the latest and goes backwards it can miss posts.
        private suspend fun addNewFast() = transaction {
            val posts = redditPosts()

            var result = RedditApi.getNew() ?: return
            var seenCount = 0
            var oldestDate = LocalDateTime.now()
            val newestDate = posts.mapNotNull { posts[it]?.created }.max()!!

            while (true) {
                val seen = posts.anyExists(result.data.map { it.id })

                logger.debug { "Amount Seen Already: $seenCount, Seen this one: $seen" }
                oldestDate = minOf(result.data.map { it.created }.min()!!, oldestDate)
                logger.debug { "Oldest Date Seen So Far: $oldestDate" }

                if (seen) {
                    seenCount++

                    if (seenCount > 3) {
                        break
                    }
                }

                posts.set(result.data.map { it.id to it })
                delay(500)
                result = result.next() ?: break
            }

            logger.debug { newestDate }
            if (oldestDate > newestDate) {
                logger.debug { "Couldn't get back to where we were before." }
                addNew(newestDate, oldestDate)
            }
        }

        private suspend fun updateFast() = transaction {
            redditPosts().scanValuesGroup { posts ->
                val outdated = posts.filter { it.outdated }

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
    }
}