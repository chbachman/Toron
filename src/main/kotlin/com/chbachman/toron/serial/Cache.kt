package com.chbachman.toron.serial

import com.chbachman.toron.homeDir
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import kotlin.math.abs
import kotlin.reflect.KClass
import kotlin.reflect.full.createType

class CacheFile<In: Any, Out: Any>(
    private val cacheFile: File,
    val expired: (In, Out) -> Boolean,
    val produce: suspend (In) -> Out,
    private val inClass: KClass<In>,
    private val outClass: KClass<Out>
) {
    // The in-memory cache.
    private val memory = mutableMapOf<In, Out>()

    // The in-memory items that don't exist on disk.
    private val unsaved = mutableMapOf<In, Out>()

    // The sink to write to when saving needs to happen.
    private val sink = cacheFile.sink(append = true).buffer()
    var loaded = false

    init {
        if (!cacheFile.exists()) {
            cacheFile.createNewFile()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun load() {
        // Only load once.
        if (loaded) {
            return
        }

        if (cacheFile.exists() && cacheFile.canRead()) {
            val source = cacheFile.source().buffer()

            while (!source.exhausted()) {
                val inValue = Serial.read(source, inClass.createType()) as In
                val outValue = Serial.read(source, outClass.createType()) as Out
                memory[inValue] = outValue
            }
        }

        loaded = true
    }

    private fun save() {
        unsaved.forEach { (inValue, outValue) ->
            Serial.write(sink, inValue, inClass.createType())
            Serial.write(sink, outValue, outClass.createType())
        }

        unsaved.clear()

        sink.flush()
    }

    fun add(query: In, page: Out) {
        // Key already exists, don't add it again.
        // Use set for that.
        if (memory.containsKey(query)) {
            return
        }

        // Add to memory cache.
        memory[query] = page

        // Add to the queue to save to file cache.
        unsaved[query] = page

        // TODO: Look into some method to saving when needed.
        // TODO: Maybe on a background thread? Shouldn't be too hard. (Famous last words)
        save()
    }

    // For right now, this deletes the file and marks everything as needing saving.
    fun set(query: In, page: Out) {
        // That was the easy part.
        memory[query] = page

        // We need to load. Otherwise we risk losing data on the disk.
        load()

        // Mark everything as needing to be saved.
        unsaved.putAll(memory)

        // Nuke disk cache.
        cacheFile.delete()

        // Now save the cache to the disk.
        // TODO: Look into some method to saving when needed.
        save()
    }

    private suspend fun refresh(query: In, data: Out): Out {
        val expired = expired(query, data)

        if (!expired) { return data }

        val refreshed = produce(query)

        // Update the cache with the new value.
        set(query, refreshed)

        return refreshed
    }

    suspend fun get(query: In): Out {
        // First try and see if it is in memory.
        if (memory.containsKey(query)) {
            return refresh(query, memory[query]!!)
        }

        load()

        // Try again and see if we got it.
        if (memory.containsKey(query)) {
            return refresh(query, memory[query]!!)
        }

        // Not in the file. We now need to load from the producer.
        val data = produce(query)

        // Add to cache.
        add(query, data)

        // Don't need to refresh since we just got it.
        return data
    }
}

private const val cacheFiles = 16
private val cacheDir = File(homeDir, "toron/cache")

class Cache<In: Any, Out: Any>(
    private val cacheName: String,
    val expired: (In, Out) -> Boolean = { _, _ -> false },
    val produce: suspend (In) -> Out,
    private val inClass: KClass<In>,
    private val outClass: KClass<Out>
) {
    companion object {
        inline fun <reified In: Any, reified Out: Any> create(
            cacheName: String,
            noinline expired: (In, Out) -> Boolean = { _, _ -> false },
            noinline produce: suspend (In) -> Out
        ): Cache<In, Out> {
            return Cache(cacheName, expired, produce, In::class, Out::class)
        }
    }

    private val caches = List(cacheFiles) { index ->
        CacheFile(
            File(cacheDir, "$cacheName-$index.cache"),
            expired,
            produce,
            inClass,
            outClass
        )
    }

    private fun cache(value: In): CacheFile<In, Out> {
        val index = abs(value.hashCode() % cacheFiles)
        return caches[index]
    }

    fun add(inValue: In, outValue: Out) {
        cache(inValue).add(inValue, outValue)
    }

    suspend fun get(inValue: In): Out {
        return cache(inValue).get(inValue)
    }
}