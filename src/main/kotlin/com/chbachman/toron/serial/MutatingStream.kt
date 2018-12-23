package com.chbachman.toron.serial

import org.mapdb.HTreeMap

open class InPlaceDriver<T>(val iterator: InPlaceIterator<T>) {
    val filters = mutableListOf<(T) -> Boolean>()

    inline fun replace(closure: (T) -> T) = modify { element, iterator ->
        iterator.replace(closure(element))
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

    inline fun modify(closure: (T, InPlaceIterator<T>) -> Unit) {
        while (iterator.hasNext()) {
            val element = iterator.next()

            if (filters.all { it(element) }) {
                closure(element, iterator)
            }
        }
    }
}

fun <T> InPlaceDriver<T>.select(closure: (T) -> Boolean): InPlaceDriver<T> {
    filters.add(closure)
    return this
}

class InPlaceGroupDriver<T>(
    val groupIterator: InPlaceGroupIterator<T>
): InPlaceDriver<T>(groupIterator) {
    inline fun replaceGroup(n: Int, closure: (List<T>) -> List<T>) {
        while (iterator.hasNext()) {
            val original = grab(n)
            val changed = closure(original)

            original.zip(changed).forEach { groupIterator.replace(it.first, it.second) }
        }
    }

    inline fun forEachGroup(n: Int, closure: (List<T>) -> Unit) {
        while(iterator.hasNext()) {
            val group = grab(n)

            if (!group.isEmpty()) {
                closure(group)
            }
        }
    }

    fun grab(n: Int): List<T> {
        val list = mutableListOf<T>()
        var remaining = n

        while (iterator.hasNext() && remaining > 0) {
            val element = iterator.next()
            if (filters.all { it(element)} ) {
                list.add(element)
                remaining--
            }
        }

        return list
    }
}

fun <T> InPlaceGroupDriver<T>.select(closure: (T) -> Boolean): InPlaceGroupDriver<T> {
    filters.add(closure)
    return this
}

fun <K, V> HTreeMap<K, V>.select(filter: (Pair<K, V>) -> Boolean) =
    InPlaceGroupDriver(InPlaceMap(this)).select(filter)

fun <K, V> HTreeMap<K, V>.selectValues(filter: (V) -> Boolean) =
    InPlaceDriver(InPlaceMapValue(this)).select(filter)

fun <K, V> HTreeMap<K, V>.selectAllValues(filter: (V) -> Boolean) =
    InPlaceDriver(InPlaceMapValue(this))