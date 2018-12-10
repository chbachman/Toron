package com.chbachman.toron.api.reddit

import com.chbachman.toron.api.pushshift.PushShift
import com.chbachman.toron.api.pushshift.getData
import com.chbachman.toron.api.pushshift.retry
import com.chbachman.toron.api.reddit.RedditCache.Companion.update
import com.chbachman.toron.data
import com.chbachman.toron.homeDir
import com.chbachman.toron.serial.*
import com.chbachman.toron.util.toUTCDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okio.buffer
import okio.sink
import okio.source
import org.mapdb.*
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.system.measureTimeMillis

class RedditCache {

    companion object {
        val databaseDir = File(homeDir, "database")

        @JvmStatic
        fun main(args: Array<String>) = runBlocking<Unit> {
            val set = dbSet<PushShift>("reddit")
        }

        suspend fun addNew(set: HTreeMap.KeySet<PushShift>) = transaction {
            var after = set.maxBy { it.created_utc }!!.created_utc

            while (true) {
                println("Fetching Date: " + after.toUTCDate())

                val fetched = getData(after) ?: return

                set.addAll(fetched)

                after = fetched.maxBy { it.created_utc }!!.created_utc
                delay(200)
            }
        }

        suspend fun updateFinal(set: HTreeMap.KeySet<PushShift>) = transaction {
            // Update last time if greater than 6 months old.
            set
                .select {
                    ChronoUnit.MONTHS.between(it.created, LocalDate.now()) > 6
                }.select {
                    ChronoUnit.MONTHS.between(it.created, it.fetched) < 6
                }.update()
        }

        private suspend fun MutableSequence<PushShift>.update() {
            this.replace {
                delay(100)
                it.update()
            }
        }
    }
}