package com.chbachman.toron.api.reddit

import com.chbachman.toron.serial.dbMap
import com.chbachman.toron.serial.mainDB
import com.chbachman.toron.serial.select
import com.chbachman.toron.serial.transaction
import com.chbachman.toron.util.toUTC
import com.chbachman.toron.util.toUTCDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.mapdb.HTreeMap
import java.time.LocalDateTime
import kotlin.concurrent.fixedRateTimer

class RedditCache {
    companion object {
        private val logger = KotlinLogging.logger {}

        suspend fun start() {
            val map = dbMap<String, RedditPost>("reddit")

            // Run every 10 minutes.
            fixedRateTimer(name = "Update Reddit", period = 10 * 60 * 1000) {
                logger.debug { "Starting Reddit update." }

                runBlocking {
                    addNewFast(map)
                    updateFast(map)
                }

                logger.debug { "Finished Reddit update." }
            }
        }

        // This pulls from PushShift.
        // This guarantees that we will never miss any posts after the most recent.
        private suspend fun addNew(
            set: HTreeMap<String, RedditPost>,
            start: LocalDateTime = set.values.filterNotNull().map { it.created }.max()!!,
            end: LocalDateTime = LocalDateTime.now()
        ) = transaction {
            var after = start.toUTC()

            while (true) {
                logger.debug { "Fetching Date: ${after.toUTCDate()}" }

                val fetched = PushShiftApi.getData(after) ?: break

                set.putAll(fetched.map { it.id to it })

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
        private suspend fun addNewFast(set: HTreeMap<String, RedditPost>) = transaction {
            var result = RedditApi.getNew() ?: return
            var seenCount = 0
            var oldestDate = LocalDateTime.now()
            val newestDate = set.map { it.value.created }.max()!!

            while (true) {
                val seen = result.data.all {
                    set.containsKey(it.id)
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

                set.putAll(result.data.map { it.id to it })
                delay(500)
                result = result.next() ?: break
            }

            logger.debug { newestDate }
            if (oldestDate > newestDate) {
                logger.debug { "Couldn't get back to where we were before." }
                addNew(set, newestDate, oldestDate)
            }
        }

        private suspend fun updateFast(set: HTreeMap<String, RedditPost>) = transaction {
            var count = set.filter { it.value.outdated }.count()
            var i = 0
            set.select { it.second.outdated }.replaceGroup(100) { list ->
                val new = RedditApi.update(list.map { it.first }) ?: return@replaceGroup list

                logger.debug { "Have $count posts left." }
                count -= list.size

                i++
                if (i > 10) {
                    logger.debug { "Adding changes so far." }
                    i = 0
                    mainDB.commit()
                }

                delay(500)
                list.zip(new.data).map { (post, update) ->
                    post.first to post.second.update(update)
                }
            }
        }

    }

}