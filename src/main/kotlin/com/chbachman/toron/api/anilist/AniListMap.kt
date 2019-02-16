package com.chbachman.toron.api.anilist

import com.chbachman.toron.jedis.JedisMap
import com.chbachman.toron.util.coder
import redis.clients.jedis.Jedis

class AniListMap(
    jedis: Jedis
): JedisMap<Int, AniList>(coder(), coder(), "show".toByteArray(), jedis) {

    private val hashKey = "h".toByteArray() + prefix

    override fun set(collection: Collection<Pair<Int, AniList>>): String {
        val modified = collection
            .filter { it.second.idMal != null }
            .map { encodeMal(it.second.idMal!!) to encodeKey(it.first) }
            .toMap()

        if (!modified.isEmpty()) {
            jedis.hmset(hashKey, modified)
        }

        return super.set(collection)
    }

    override fun set(key: Int, value: AniList): String {
        if (value.idMal != null) {
            jedis.hset(hashKey, encodeMal(value.idMal), encodeKey(key))
        }

        return super.set(key, value)
    }

    override fun delete(key: Int): Long {
        val value = this[key]

        if (value?.idMal != null) {
            jedis.hdel(hashKey, encodeMal(value.idMal))
        }

        return super.delete(key)
    }

    override fun delete(keys: Collection<Int>): Long {
        val modified = keys
            .mapNotNull { this[it]?.idMal }
            .map { encodeMal(it) }
            .toTypedArray()

        jedis.hdel(hashKey, *modified)
        return super.delete(keys)
    }

    fun getMAL(key: Int): AniList? {
        val id = jedis.hget(hashKey, encodeMal(key)) ?: return null
        return decodeValue(jedis[id])
    }


    private fun encodeMal(key: Int): ByteArray =
        key.toString().toByteArray()
}