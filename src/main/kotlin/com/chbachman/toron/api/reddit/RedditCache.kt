package com.chbachman.toron.api.reddit

import com.chbachman.toron.api.pushshift.PushShiftApi
import com.chbachman.toron.serial.dbMap
import com.chbachman.toron.serial.mainDB
import com.chbachman.toron.serial.select
import com.chbachman.toron.serial.transaction
import com.chbachman.toron.util.toUTCDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.mapdb.HTreeMap
import kotlin.system.measureTimeMillis

class RedditCache {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) = runBlocking<Unit> {
            val map = dbMap<String, RedditPost>("reddit")

            addNewFast(map)
            updateFast(map)
        }

        private suspend fun addNew(set: HTreeMap<String, RedditPost>) = transaction {
            var after = set.values.filterNotNull().maxBy { it.createdUtc }!!.createdUtc

            while (true) {
                println("Fetching Date: " + after.toUTCDate())

                val fetched = PushShiftApi.getData(after) ?: return

                set.putAll(fetched.map { it.id to it })

                after = fetched.maxBy { it.createdUtc }!!.createdUtc
                delay(200)
            }
        }

        private suspend fun addNewFast(set: HTreeMap<String, RedditPost>) = transaction {
            var result = RedditApi.getNew() ?: return
            var seenCount = 0

            while (true) {
                val seen = result.data.all {
                    set.containsKey(it.id)
                }

                println("Amount Seen Already: $seenCount, Seen this one: $seen")

                if (seen) {
                    seenCount++

                    if (seenCount > 3) {
                        return
                    }
                }

                set.putAll(result.data.map { it.id to it })
                result = result.next() ?: return
            }
        }

        private suspend fun updateFast(set: HTreeMap<String, RedditPost>) = transaction {
            var count = set.filter { it.value.outdated }.count()
            var i = 0
            set.select { it.second.outdated }.replaceGroup(100) { list ->
                val new = RedditApi.update(list.map { it.first }) ?: return@replaceGroup list

                println("Have $count posts left.")
                count -= list.size

                i++
                if (i > 10) {
                    println("Adding changes so far.")
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