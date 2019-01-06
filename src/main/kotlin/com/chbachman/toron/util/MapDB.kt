package com.chbachman.toron.util

import org.mapdb.DataInput2
import org.mapdb.DataOutput2
import org.mapdb.Serializer

fun <T> Serializer<T>.writer() = this::serialize
fun <T> Serializer<T>.reader() = { output: DataInput2 ->
    this.deserialize(output, -1)
}

inline fun <T> DataInput2.readList(
    closure: (DataInput2) -> T
): List<T> {
    val length = readInt()

    return List(length) {
        closure(this)
    }
}

inline fun <T> DataInput2.readIf(
    closure: (DataInput2) -> T
): T? =
    if (readBoolean()) {
        closure(this)
    } else {
        null
    }

inline fun <T> DataOutput2.writeAll(
    closure: (DataOutput2, T) -> Unit,
    vararg values: T
) {
    writeInt(values.size)

    values.forEach { closure(this, it) }
}

inline fun <T> DataOutput2.writeList(
    closure: (DataOutput2, T) -> Unit,
    value: Collection<T>
) {
    writeInt(value.size)

    value.forEach { closure(this, it) }
}

inline fun <T> DataOutput2.writeIf(
    closure: (DataOutput2, T) -> Unit,
    value: T?
) {
    writeBoolean(value != null)

    if (value != null) {
        closure(this, value)
    }
}