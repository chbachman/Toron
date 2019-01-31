package com.chbachman.toron.jedis

import com.chbachman.toron.util.ByteCodable
import com.chbachman.toron.util.coder
import redis.clients.jedis.*
import redis.clients.jedis.ScanParams.SCAN_POINTER_START_BINARY

val star = "*".toByteArray()
val redisPassword: String = System.getenv("REDIS_PASSWORD")
val pool = JedisPool(JedisPoolConfig(), "localhost", 6379, Protocol.DEFAULT_TIMEOUT, redisPassword)

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