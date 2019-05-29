package com.chbachman.toron.link

import com.chbachman.toron.api.anilist.AniList
import com.chbachman.toron.api.reddit.RedditPost
import com.chbachman.toron.jedis.manualTransaction
import com.chbachman.toron.jedis.transaction
import com.chbachman.toron.util.*
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import redis.clients.jedis.Jedis

data class Show(
    val info: AniList,
    val threads: List<RedditPost>
)

suspend inline fun <T> linker(closure: (Linker) -> T) = transaction {
    val data = Linker.data.await()
    val linker = Linker(data, this)

    closure(linker)
}

// We store data here to allow for easy rebuilding on cache.
class LinkerData(val full: Map<Int, List<Long>>) {
    val top = GlobalScope.async(start = CoroutineStart.LAZY) {
        transaction {
            val linker = Linker(Linker.data.await(), this)

            linker
                .sortedByDescending { show -> show.threads.sumBy { it.score } / show.threads.size }
                .take(25)
        }
    }
}

// This shouldn't hold any data. It is only processing the data.
class Linker constructor(
    private val data: LinkerData,
    jedis: Jedis
): Iterable<Show> {
    private val valueSet = jedis.redditPosts()
    private val keySet = jedis.anilistShows()

    operator fun get(id: Int) =
        data.full[id]?.let { posts(it) }

    operator fun get(id: Collection<Int>) =
        id.mapNotNull { data.full[it] }.map { posts(it) }

    override fun iterator() = object: Iterator<Show> {
        val iterator = data.full.iterator()
        override fun hasNext(): Boolean = iterator.hasNext()

        override fun next(): Show {
            val data = iterator.next()
            return Show(keySet[data.key]!!, posts(data.value))
        }
    }

    private fun posts(posts: List<Long>) =
        valueSet[posts.map { it.toString(36) }]

    // Generates the Data, Stores it for Future Use
    companion object {
        private val logger = KotlinLogging.logger {  }
        @Volatile var data = refreshAsync()
            private set
        private val mutex = Mutex()

        fun invalidate() { data = refreshAsync() }

        private fun refreshAsync() =
            GlobalScope.async { mutex.withLock { generateMap() } }

        private suspend fun generateMap(): LinkerData {
            logger.info { "Loading initial list." }
            logger.info { "Starting up. Doing Grouping." }

            val result = transaction {
                redditPosts().values
                    .asSequence()
                    .filter { it.numComments > 2 }
                    .filter { it.score > 1 }
                    .filter { it.isSelf }
                    .filter { it.episode != null }
                    .filter { it.title.contains("\\d".toRegex()) }
                    .filterNot { it.selftext?.deleteInside(Char::isOpening, Char::isClosing).isNullOrBlank() }
                    .toList()
            }

            logger.info { "Filtering Done with a size of ${result.size}" }

            val temp = result
                .groupBy { it.show.await() }
                .mapNotNull { entry ->
                    val showId = entry.key
                    if (showId != null) {
                        showId.id to entry.value.map { it.id }.map { it.toLong(36) }
                    } else {
                        null
                    }
                }
                .toMap()

            logger.info { "Grouping completed with a size of ${temp.size}" }


            return LinkerData(temp)
        }
    }
}