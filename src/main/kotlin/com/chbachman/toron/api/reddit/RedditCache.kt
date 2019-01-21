package com.chbachman.toron.api.reddit

import com.chbachman.toron.serial.*
import com.chbachman.toron.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.dizitart.kno2.filters.eq
import org.dizitart.kno2.filters.lte
import org.dizitart.no2.objects.ObjectRepository
import java.time.LocalDateTime
import kotlin.concurrent.fixedRateTimer

class RedditCache {
    companion object {
        private val logger = KotlinLogging.logger {}

        suspend fun start() {
            val repo = repo<RedditPost>()

            // Run every 10 minutes.
            fixedRateTimer(name = "Update Reddit", period = 10 * 60 * 1000) {
                logger.info { "Starting Reddit update." }

                runBlocking {
                    addNewFast(repo)
                    updateFast(repo)
                    cleanup(repo)
                }

                logger.info { "Finished Reddit update." }
            }
        }

        private fun cleanup(repo: ObjectRepository<RedditPost>) {
            logger.debug { "Started with ${repo.size()}." }

            repo.any(
                RedditPost::numComments lte 2,
                RedditPost::score lte 1,
                RedditPost::isSelf eq false
            ).filter {
                it.created.monthsAgo > 7
            }.forEach {
                repo.remove(it)
            }

            logger.debug { "Ended with ${repo.size()} elements." }
        }

        // This pulls from PushShift.
        // This guarantees that we will never miss any posts after the most recent.
        private suspend fun addNew(
            repo: ObjectRepository<RedditPost>,
            start: LocalDateTime,
            end: LocalDateTime = LocalDateTime.now()
        ) {
            var after = start.toUTC()

            while (true) {
                logger.debug { "Fetching Date: ${after.toUTCDate()}" }

                val fetched = PushShiftApi.getData(after) ?: break

                repo.insert(fetched)

                val max = fetched.maxBy { it.createdUtc }!!

                after = max.createdUtc

                if (max.created > end) {
                    break
                }

                delay(200)
            }
        }

        // Pulls from Reddit.
        // Since this one starts at the latest and goes backwards it can miss posts.
        private suspend fun addNewFast(repo: ObjectRepository<RedditPost>) {
            var result = RedditApi.getNew() ?: return
            var seenCount = 0
            var oldestDate = LocalDateTime.now()
            val newestDate = repo.find().map { it.created }.max()!!

            while (true) {
                val seen = result.data.all {
                    repo.find(RedditPost::id eq it.id).size() != 0
                }

                logger.debug { "Amount Seen Already: $seenCount, Seen this one: $seen" }
                oldestDate = minOf(result.data.map { it.created }.min()!!, oldestDate)
                logger.debug { "Oldest Date Seen So Far: $oldestDate" }

                if (seen) {
                    seenCount++

                    if (seenCount > 3) {
                        break
                    }
                }

                repo.upsert(result.data)
                delay(500)
                result = result.next() ?: break
            }

            logger.debug { newestDate }
            if (oldestDate > newestDate) {
                logger.debug { "Couldn't get back to where we were before." }
                addNew(repo, newestDate, oldestDate)
            }
        }

        private suspend fun updateFast(set: ObjectRepository<RedditPost>) {
            val outdated = set.find().filter { it.outdated }.toMutableSet()
            var count = outdated.count()

            outdated.forEachGroup(100) { list ->
                val new = RedditApi.update(list.map { it.id }) ?: return@forEachGroup

                logger.debug { "Have $count posts left." }
                count -= list.size

                delay(500)
                val result = list
                    .zip(new.data)
                    .map { (post, update) -> post.update(update) }

                set.update(result)
            }
        }
    }
}