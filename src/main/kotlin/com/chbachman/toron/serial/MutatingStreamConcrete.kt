package com.chbachman.toron.serial

import org.mapdb.HTreeMap

interface InPlaceIterator<T>: MutableIterator<T> {
    fun replace(new :T)
}

interface InPlaceGroupIterator<T>: InPlaceIterator<T> {
    fun remove(element: T)
    fun replace(element: T, with: T)
}

class InPlaceMap<K, V> (
    private val map: HTreeMap<K, V>
): InPlaceGroupIterator<Pair<K, V>> {
    private val iterator = map.iterator()

    override fun remove(element: Pair<K, V>) {
        map.remove(element.first)
    }

    override fun replace(new: Pair<K, V>) {
        map[new.first] = new.second
    }

    override fun replace(element: Pair<K, V>, with: Pair<K, V>) {
        map[element.first] = with.second
    }

    override fun hasNext(): Boolean = iterator.hasNext()
    override fun next(): Pair<K, V> = iterator.next().toPair()
    override fun remove() = iterator.remove()
}

class InPlaceMapValue<K, V> (
    private val map: HTreeMap<K, V>
): InPlaceIterator<V> {
    private val iterator = map.iterator()
    private var currentKey: K? = null

    override fun hasNext() = iterator.hasNext()
    override fun remove() = iterator.remove()

    override fun next(): V {
        val temp = iterator.next()
        currentKey = temp.key
        return temp.value
    }

    override fun replace(new: V) {
        map[currentKey!!] = new
    }
}