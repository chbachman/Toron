package com.chbachman.toron.util

import com.chbachman.toron.ByteCodable
import com.chbachman.toron.Codable
import okio.Buffer
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.ScanParams
import redis.clients.jedis.ScanParams.SCAN_POINTER_START_BINARY
import kotlin.reflect.full.companionObjectInstance

val star = "*".toByteArray()
val pool = JedisPool(JedisPoolConfig(), "localhost")

operator fun Jedis.set(key: String, value: ByteArray) {
    this[key.toByteArray()] = value
}

fun Jedis.scan(params: ScanParams) = scan(SCAN_POINTER_START_BINARY, params)

inline fun <T> transaction(closure: (Jedis) -> T): T {
    pool.resource.use { jedis ->
        return closure(jedis)
    }
}

fun closeDB() = pool.close()

val stringCoder = object: ByteCodable<String> {
    override fun read(input: ByteArray): String =
        String(input)

    override fun write(input: String): ByteArray =
        input.toByteArray()
}

val intCoder = object: Codable<Int> {
    override fun write(input: Int, buffer: Buffer): Buffer =
        buffer.writeInt(input)

    override fun read(buffer: Buffer): Int =
        buffer.readInt()
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T> coder(): ByteCodable<T> =
    when (T::class) {
        String::class -> stringCoder
        Int::class -> intCoder
        else -> T::class.companionObjectInstance
    } as ByteCodable<T>

inline fun <reified K, reified V> Jedis.mapOf(prefix: String) =
    JedisSet(coder<K>(), coder<V>(), prefix.toByteArray(), this)

class JedisSet<K, V>(
    val keyCoder: ByteCodable<K>,
    val valueCoder: ByteCodable<V>,
    val prefix: ByteArray,
    val jedis: Jedis
) {
    fun keyFrom(key: K) =
        prefix + keyCoder.write(key)

    fun valueFrom(value: V) =
        valueCoder.write(value)

    fun decodeKey(bytes: ByteArray) =
        keyCoder.read(bytes.copyOfRange(prefix.size, bytes.size))

    operator fun set(key: K, value: V): String =
        jedis.set(keyFrom(key), valueFrom(value))

    operator fun get(key: K): V? =
        jedis.get(keyFrom(key)).let { valueCoder.read(it) }

    operator fun get(keys: Collection<K>) =
        jedis.mget(*keys.map { keyCoder.write(it) }.toTypedArray())
            .filterNotNull()
            .map { valueCoder.read(it) }

    inline fun scan(closure: (K) -> Unit) {
        scanGroup { group -> group.forEach { closure(it) } }
    }

    inline fun scanGroup(closure: (List<K>) -> Unit) {
        val params = ScanParams().match(prefix + star)
        var search = jedis.scan(params)

        while (!search.isCompleteIteration) {
            closure(search.result.map { decodeKey(it) })
            search = jedis.scan(search.cursorAsBytes, params)
        }
    }

    fun setnx(key: K, value: V): Long =
        jedis.setnx(keyFrom(key), valueCoder.write(value))
}