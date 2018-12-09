package com.chbachman.toron.serial

import org.mapdb.HTreeMap

class MutableSequence<T>(
    val list: HTreeMap.KeySet<T>,
    filter: ((T) -> Boolean)
) {
    val filters = mutableListOf(filter)
    fun select(closure: (T) -> Boolean): MutableSequence<T> {
        filters.add(closure)
        return this
    }

    inline fun replace(closure: (T) -> T) = modify { element, iterator ->
        iterator.remove()
        list.add(closure(element))
    }

    fun delete() = modify { _, iterator ->
        iterator.remove()
    }

    fun count(): Int {
        var total = 0

        modify { _, _ ->
            total++
        }

        return total
    }

    inline fun modify(closure: (T, MutableIterator<T?>) -> Unit) {
        val iterator = list.iterator()
        while (iterator.hasNext()) {
            val element = iterator.next()!!

            if (filters.all { it(element) }) {
                closure(element, iterator)
            }
        }
    }
}

fun <T> HTreeMap.KeySet<T>.select(closure: (T) -> Boolean): MutableSequence<T> {
    return MutableSequence<T>(this, closure)
}