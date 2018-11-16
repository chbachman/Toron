package com.chbachman.serial

import okio.buffer
import okio.sink
import okio.source
import java.io.File
import kotlin.math.abs
import kotlin.reflect.KClass
import kotlin.reflect.full.createType

private const val cacheFiles = 16

class Cache<In: Any, Out: Any>(
    private val cacheName: String,
    val produce: suspend (In) -> Out,
    private val inClass: KClass<In>,
    private val outClass: KClass<Out>
) {
    companion object {
        inline fun <reified In: Any, reified Out: Any> create(
            cacheName: String,
            noinline produce: suspend (In) -> Out
        ): Cache<In, Out> {
            return Cache(cacheName, produce, In::class, Out::class)
        }
    }

    private val memory = mutableMapOf<In, Out>()
    private val cacheDir = File(System.getProperty("user.home") + "/Desktop/Toron/Cache")
    private val loadedFiles = mutableSetOf<String>()

    private fun cacheName(query: In): String {
        val index = abs(query.hashCode() % cacheFiles)
        return "$cacheName-$index.cache"
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadCacheFile(query: In): List<Pair<In, Out>>? {
        val cacheFile = File(cacheDir, cacheName(query))

        if (loadedFiles.contains(cacheFile.path)) return null

        loadedFiles.add(cacheFile.path)

        if (!cacheFile.exists()) return null
        if (!cacheFile.canRead()) return null

        val source = cacheFile.source().buffer()

        val list = mutableListOf<Pair<In, Out>>()

        while (!source.exhausted()) {
            val inValue = Serial.read(source, inClass.createType()) as In
            val outValue = Serial.read(source, outClass.createType()) as Out
            list += inValue to outValue
        }

        return list
    }

    private fun addCacheData(query: In, page: Out) {
        // Add to memory cache.
        memory[query] = page

        // Add to file cache.
        val cacheFile = File(cacheDir, cacheName(query))
        val sink = cacheFile.sink(append = true).buffer()

        Serial.write(sink, query, inClass.createType())
        Serial.write(sink, page, outClass.createType())
        sink.flush()
    }

    suspend fun get(query: In): Out {
        if (memory.containsKey(query)) {
            return memory[query]!!
        }

        // Not in memory, load the file associated with it.
        val fileData = loadCacheFile(query)
        if (fileData != null) {
            memory.putAll(fileData)
        }

        if (memory.containsKey(query)) {
            return memory[query]!!
        }

        // Not in the file. We now need to load from the producer.
        val data = produce(query)

        // Add to cache.
        addCacheData(query, data)
        return data
    }
}