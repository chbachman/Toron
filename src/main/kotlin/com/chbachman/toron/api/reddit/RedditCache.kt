package com.chbachman.toron.api.reddit

import com.chbachman.toron.api.pushshift.PushShift
import com.chbachman.toron.api.reddit.RedditCache.Companion.update
import com.chbachman.toron.data
import com.chbachman.toron.homeDir
import com.chbachman.toron.serial.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okio.buffer
import okio.sink
import okio.source
import org.mapdb.*
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.system.measureTimeMillis

class RedditCache {

    companion object {
        val databaseDir = File(homeDir, "database")

        @JvmStatic
        fun main(args: Array<String>) = runBlocking<Unit> {

            val newSet = dbSet<PushShift>("redditPosts")

            transaction {
                // If it's older than a month
                val count = newSet
                    .select {
                        ChronoUnit.MONTHS.between(it.created, LocalDate.now()) < 1
                    }
                    .select {
                        println(it.created.toString() + " - " + LocalDate.now())
                        println(ChronoUnit.MONTHS.between(it.created, LocalDate.now()))
                        ChronoUnit.MONTHS.between(it.created, LocalDate.now()) in 1..6
                    }.select {
                        ChronoUnit.MONTHS.between(it.created, it.fetched) < 1
                    }.count()

                println(count)
            }
        }

        suspend fun updateFinal(set: HTreeMap.KeySet<PushShift>) {
            // Update last time if greater than 6 months old.
            set
                .select {
                    ChronoUnit.MONTHS.between(it.created, LocalDate.now()) > 6
                }.select {
                    ChronoUnit.MONTHS.between(it.created, it.fetched) < 6
                }.update()
        }

        fun readBackup(): List<PushShift> {
            val source = File("/Users/Chandler/Desktop/Toron/newBinary").source().buffer()

            val list = mutableListOf<PushShift>()
            while(!source.exhausted()) {
                list.add(PushShift.read(source))
            }

            return list
        }

        private suspend fun MutableSequence<PushShift>.update() {
            this.replace {
                delay(100)
                it.update()
            }
        }
    }
}