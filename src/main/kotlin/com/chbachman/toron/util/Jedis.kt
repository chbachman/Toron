package com.chbachman.toron.util

import redis.clients.jedis.*
import redis.clients.jedis.ScanParams.SCAN_POINTER_START_BINARY

val star = "*".toByteArray()
val pool = JedisPool(JedisPoolConfig(), "localhost")

fun Jedis.scan(params: ScanParams) =
    scan(SCAN_POINTER_START_BINARY, params)

inline fun <T> transaction(closure: Jedis.() -> T): T =
    pool.resource.use { jedis -> jedis.closure() }

inline fun <T> Jedis.pipeline(closure: Pipeline.() -> T): T {
    val p = pipelined()
    val result = p.closure()
    p.sync()
    return result
}

fun closeDB() = pool.close()

interface KeyValueCoder<K, V> {
    val keyCoder: ByteCodable<K>
    val valueCoder: ByteCodable<V>
    val prefix: ByteArray

    fun encodeKey(key: K) =
        prefix + keyCoder.write(key)

    fun encodeValue(value: V) =
        valueCoder.write(value)

    fun decodeKey(bytes: ByteArray) =
        keyCoder.read(bytes.copyOfRange(prefix.size, bytes.size))

    fun decodeValue(bytes: ByteArray) =
        valueCoder.read(bytes)
}

inline fun <reified K, reified V> Pipeline.mapOf(prefix: String) =
    JedisPipelineSet(coder<K>(), coder<V>(), prefix.toByteArray(), this)

class JedisPipelineSet<K, V>(
    override val keyCoder: ByteCodable<K>,
    override val valueCoder: ByteCodable<V>,
    override val prefix: ByteArray,
    val pipeline: Pipeline
): KeyValueCoder<K, V> {
    operator fun set(key: K, value: V): Response<String> =
        pipeline.set(encodeKey(key), encodeValue(value))

    fun set(collection: Collection<Pair<K, V>>): Response<String> =
        pipeline.mset(*collection.flatMap {
            listOf(encodeKey(it.first), encodeValue(it.second))
        }.toTypedArray())

    fun delete(key: K): Response<Long> =
        pipeline.del(encodeKey(key))

    fun delete(keys: Collection<K>): Response<Long> =
        pipeline.del(*keys.map { encodeKey(it) }.toTypedArray())
}

inline fun <reified K, reified V> Jedis.mapOf(prefix: String) =
    JedisSet(coder<K>(), coder<V>(), prefix.toByteArray(), this)

class JedisSet<K, V>(
    override val keyCoder: ByteCodable<K>,
    override val valueCoder: ByteCodable<V>,
    override val prefix: ByteArray,
    val jedis: Jedis
): KeyValueCoder<K, V>, Iterable<K> {
    operator fun set(key: K, value: V): String =
        jedis.set(encodeKey(key), encodeValue(value))

    fun set(collection: Collection<Pair<K, V>>): String =
        jedis.mset(*collection.flatMap {
            listOf(encodeKey(it.first), encodeValue(it.second))
        }.toTypedArray())

    operator fun get(key: K): V? =
        jedis.get(encodeKey(key))?.let { decodeValue(it) }

    operator fun get(keys: Collection<K>): List<V> =
        if (!keys.isEmpty()) {
            jedis.mget(*keys.map { encodeKey(it) }.toTypedArray())
                .filterNotNull()
                .map { decodeValue(it) }
        } else {
            emptyList()
        }


    fun delete(key: K): Long =
        jedis.del(encodeKey(key))

    fun delete(keys: Collection<K>): Long =
        if (!keys.isEmpty()) {
            jedis.del(*keys.map { encodeKey(it) }.toTypedArray())
        } else {
            0
        }

    fun exists(key: K): Boolean =
        jedis.exists(encodeKey(key))

    fun anyExists(keys: Collection<K>): Boolean =
        jedis.exists(*keys.map { encodeKey(it) }.toTypedArray()) > 0

    inline fun scanKeysGroup(closure: JedisSet<K, V>.(List<K>) -> Unit) = scanRaw { rawList ->
        closure(rawList.map { decodeKey(it) })
    }

    inline fun scanValuesGroup(closure: JedisSet<K, V>.(List<V>) -> Unit) = scanRaw { rawList ->
        closure(
            jedis.mget(*rawList.toTypedArray())
                .filterNotNull()
                .map { decodeValue(it) }
        )
    }

    inline fun scanValuesGroupPipeline(closure: JedisPipelineSet<K, V>.(List<V>) -> Unit) = jedis.pipeline {
        val pipelineSet = JedisPipelineSet(
            keyCoder,
            valueCoder,
            prefix,
            this
        )

        scanRaw { rawList ->
            pipelineSet.closure(
                jedis.mget(*rawList.toTypedArray())
                    .filterNotNull()
                    .map { decodeValue(it) }
            )
        }
    }

    inline fun scanRaw(closure: (List<ByteArray>) -> Unit) {
        val params = ScanParams().match(prefix + star)
        var search = jedis.scan(params)

        while (!search.isCompleteIteration) {
            closure(search.result)
            search = jedis.scan(search.cursorAsBytes, params)
        }

        // Get last iteration from the scan.
        closure(search.result)
    }

    override fun iterator(): Iterator<K> = object: Iterator<K> {
        val params = ScanParams().match(prefix + star)
        var search = jedis.scan(params)
        var current = search.result.iterator()

        override fun hasNext(): Boolean =
            current.hasNext() || !search.isCompleteIteration

        override fun next(): K =
            if (current.hasNext()) {
                decodeKey(current.next())
            } else {
                search = jedis.scan(search.cursorAsBytes, params)
                current = search.result.iterator()
                next()
            }
    }
}