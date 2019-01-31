package com.chbachman.toron.link

import com.chbachman.toron.api.anilist.AniList
import com.chbachman.toron.api.reddit.RedditPost
import com.chbachman.toron.jedis.transaction
import com.chbachman.toron.util.*
import kotlinx.coroutines.Deferred
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

class Linker constructor(
    private val data: Map<Int, List<Long>>,
    jedis: Jedis
): Iterable<Show> {
    private val valueSet = jedis.redditPosts()
    private val keySet = jedis.anilistShows()

    operator fun get(id: Int) =
        data[id]?.let { posts(it) }

    operator fun get(id: Collection<Int>) =
        id.mapNotNull { data[it] }.map { posts(it) }

    override fun iterator() = object: Iterator<Show> {
        val iterator = data.iterator()
        override fun hasNext(): Boolean = iterator.hasNext()

        override fun next(): Show {
            val data = iterator.next()
            return Show(keySet[data.key]!!, posts(data.value))
        }
    }

    private fun posts(posts: List<Long>) =
        valueSet[posts.map { it.toString(36) }]

    companion object {
        private val logger = KotlinLogging.logger {  }
        @Volatile var data = refreshAsync()
            private set
        private val mutex = Mutex()

        fun invalidate() { data = refreshAsync() }

        private fun refreshAsync() =
            GlobalScope.async { mutex.withLock { generateMap() } }

        private suspend fun generateMap(): Map<Int, List<Long>> = transaction {
            logger.info { "Loading initial list." }
            val posts = redditPosts()
            logger.info { "Starting up. Doing Grouping." }

            val result = posts.values
                .asSequence()
                .filter { it.numComments > 2 }
                .filter { it.score > 1 }
                .filter { it.isSelf }
                .filter { it.episode != null }
                .filter { it.title.contains("\\d".toRegex()) }
                .filterNot { it.selftext?.deleteInside(Char::isOpening, Char::isClosing).isNullOrBlank() }
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

            logger.info { "Grouping completed with a size of ${result.size}" }

            result
        }
    }


}