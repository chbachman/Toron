package com.chbachman.toron.jedis

import com.chbachman.toron.util.ByteCodable
import com.chbachman.toron.util.coder
import redis.clients.jedis.Jedis
import redis.clients.jedis.Pipeline
import redis.clients.jedis.Response
import redis.clients.jedis.ScanParams

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
    JedisPipelineMap(coder<K>(), coder<V>(), prefix.toByteArray(), this)

class JedisPipelineMap<K, V>(
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
    JedisMap(coder<K>(), coder<V>(), prefix.toByteArray(), this)

open class JedisMap<K, V>(
    override val keyCoder: ByteCodable<K>,
    override val valueCoder: ByteCodable<V>,
    override val prefix: ByteArray,
    val jedis: Jedis
): KeyValueCoder<K, V> {
    open operator fun set(key: K, value: V): String =
        jedis.set(encodeKey(key), encodeValue(value))

    open fun set(collection: Collection<Pair<K, V>>): String =
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


    open fun delete(key: K): Long =
        jedis.del(encodeKey(key))

    open fun delete(keys: Collection<K>): Long =
        if (!keys.isEmpty()) {
            jedis.del(*keys.map { encodeKey(it) }.toTypedArray())
        } else {
            0
        }

    fun exists(key: K): Boolean =
        jedis.exists(encodeKey(key))

    fun anyExists(keys: Collection<K>): Boolean =
        jedis.exists(*keys.map { encodeKey(it) }.toTypedArray()) > 0

    inline fun scanKeysGroup(count: Int = 10, closure: JedisMap<K, V>.(List<K>) -> Unit) = scanRaw(count) { rawList ->
        closure(rawList.map { decodeKey(it) })
    }

    inline fun scanValuesGroup(count: Int = 10, closure: JedisMap<K, V>.(List<V>) -> Unit) = scanRaw(count) { rawList ->
        if (rawList.isNotEmpty()) {
            closure(
                jedis.mget(*rawList.toTypedArray())
                    .filterNotNull()
                    .map { decodeValue(it) }
            )
        }
    }

    inline fun scanRaw(count: Int = 10, closure: (List<ByteArray>) -> Unit) {
        val params = ScanParams().match(prefix + star).count(count)
        var search = jedis.scan(params)

        while (!search.isCompleteIteration) {
            closure(search.result)
            search = jedis.scan(search.cursorAsBytes, params)
        }

        // Get last iteration from the scan.
        closure(search.result)
    }

    val keys get() = object: Iterable<K> {
        override fun iterator(): Iterator<K> = object: Iterator<K> {
            val params = ScanParams().match(prefix + star)
            var search = jedis.scan(params)
            var current = search.result.iterator()
            var next = generateNext()

            override fun hasNext(): Boolean = next != null

            override fun next(): K {
                val temp = next
                next = generateNext()
                return temp!!
            }

            private tailrec fun generateNext(): K? =
                if (current.hasNext()) {
                    decodeKey(current.next())
                } else {
                    if (search.isCompleteIteration) {
                        null
                    } else {
                        search = jedis.scan(search.cursorAsBytes, params)
                        current = search.result.iterator()
                        generateNext()
                    }
                }
        }
    }

    val values get() = object: Iterable<V> {
        override fun iterator(): Iterator<V> = object: Iterator<V> {
            val params = ScanParams().match(prefix + star)
            var search = jedis.scan(params)
            var current = convert(search.result)
            var next = generateNext()

            override fun hasNext(): Boolean = next != null

            override fun next(): V {
                val temp = next
                next = generateNext()
                return temp!!
            }

            private tailrec fun generateNext(): V? =
                if (current.hasNext()) {
                    decodeValue(current.next())
                } else {
                    if (search.isCompleteIteration) {
                        null
                    } else {
                        search = jedis.scan(search.cursorAsBytes, params)
                        current = convert(search.result)
                        generateNext()
                    }
                }

            private fun convert(search: List<ByteArray>) =
                if (search.isNotEmpty()) {
                    jedis.mget(*search.toTypedArray()).iterator()
                } else {
                    emptyList<ByteArray>().iterator()
                }
        }
    }
}