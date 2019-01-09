@file:Suppress("UNCHECKED_CAST")

package com.chbachman.toron.serial

import com.chbachman.toron.api.anilist.AniList
import com.chbachman.toron.api.anilist.AniListSearch
import com.chbachman.toron.api.reddit.RedditPost
import com.chbachman.toron.homeDir
import mu.KotlinLogging
import org.dizitart.kno2.nitrite
import org.dizitart.no2.objects.ObjectRepository
import org.mapdb.*
import java.io.File
import kotlin.reflect.full.companionObjectInstance

class Migration {
    companion object {
        private val logger = KotlinLogging.logger {}

        @JvmStatic
        fun main(args: Array<String>) {
            val reddit = dbMap<String, RedditPost>("reddit")
            val searchCache = dbMap<String, AniListSearch>("anilist-cache")
            val anilist = dbMap<Int, AniList>("anilist")

            val redditNew = repo<RedditPost>()
            val searchCacheNew = repo<AniListSearch>()
            val anilistNew = repo<AniList>()

            anilist.forEach { anilistNew.insert(it.value) }
            logger.info { "Anilist Done." }
            searchCache.forEach { searchCacheNew.insert(it.value.copy(search = it.key)) }
            logger.info { "Anilist Search Cache Done." }
            reddit.forEach { redditNew.insert(it.value) }
            logger.info { "Reddit Cache Done." }

            println(reddit.size)
            println(redditNew.size())
            println(searchCache.size)
            println(searchCacheNew.size())
            println(anilist.size)
            println(anilistNew.size())
        }
    }
}

val databaseDir = File(homeDir, "database")
val mainDB = createDB("toron.db")

enum class StoreType {
    List,
    Set,
    Map
}

val db = nitrite {
    file = File(databaseDir, "toron-nitrite.db")
}

inline fun <reified T> repo(): ObjectRepository<T> = db.getRepository(T::class.java)

val dbStore = mutableMapOf<Pair<DB, String>, Pair<List<Serializer<*>>, StoreType>>()

inline fun <reified T> dbList(name: String, db: DB = mainDB): IndexTreeList<T> {
    dbStore[db to name] = listOf(serializer<T>()) to StoreType.List
    return db.indexTreeList(name, serializer<T>()).createOrOpen()
}

inline fun <reified T> dbSet(name: String, db: DB = mainDB): HTreeMap.KeySet<T> {
    dbStore[db to name] = listOf(serializer<T>()) to StoreType.Set
    return db.hashSet(name, serializer<T>()).createOrOpen()
}

inline fun <reified T, reified K> dbMap(name: String, db: DB = mainDB): HTreeMap<T, K> {
    dbStore[db to name] = listOf(serializer<T>(), serializer<K>()) to StoreType.Map
    return db.hashMap(name, serializer<T>(), serializer<K>()).createOrOpen()
}

fun DB.copyTo(other: DB) = transaction(other) {
    getAll().forEach { name, source ->
        val (serializers, type) = dbStore[this to name]!!

        val dest = when (type) {
            StoreType.List -> other.indexTreeList(name, serializers.first())
            StoreType.Set -> other.hashSet(name, serializers.first())
            StoreType.Map -> other.hashMap(name, serializers.first(), serializers.last())
        }.createOrOpen()

        when (type) {
            StoreType.List -> (dest as IndexTreeList<Any?>).addAll(source as IndexTreeList<*>)
            StoreType.Set -> (dest as HTreeMap.KeySet<Any?>).addAll(source as HTreeMap.KeySet<*>)
            StoreType.Map -> (dest as HTreeMap<Any?, Any?>).putAll(source as HTreeMap<*, *>)
        }

        println((dest as HTreeMap<*, *>).size)
        println((source as HTreeMap<*, *>).size)
    }
}

inline fun <T> transaction(db: DB = mainDB, closure: () -> T): T {
    val temp = closure()
    db.commit()
    return temp
}

inline fun <reified T> serializer(): Serializer<T> =
    when (T::class) {
        Int::class -> Serializer.INTEGER
        String::class -> Serializer.STRING
        Long::class -> Serializer.LONG
        else -> T::class.companionObjectInstance
    } as Serializer<T>

fun createDB(name: String): DB =
    DBMaker
        .fileDB(File(databaseDir, name))
        .transactionEnable()
        .fileMmapEnableIfSupported()
        .fileMmapPreclearDisable()
        .closeOnJvmShutdown()
        .make()

