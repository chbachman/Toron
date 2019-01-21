package com.chbachman.toron.util

import okio.Buffer
import okio.utf8Size

fun Buffer.readBoolean(): Boolean =
    readByte() != 0.toByte()

fun Buffer.readString(): String =
    readUtf8(readLong())

fun Buffer.writeBoolean(boolean: Boolean) =
    writeByte(if (boolean) 1 else 0)

fun Buffer.writeString(s: String) =
    writeLong(s.utf8Size()).writeUtf8(s)

inline fun <T> Buffer.readList(
    closure: (Buffer) -> T
): List<T> {
    val length = readInt()

    return List(length) {
        closure(this)
    }
}

inline fun <T> Buffer.readIf(
    closure: (Buffer) -> T
): T? =
    if (readBoolean()) {
        closure(this)
    } else {
        null
    }

inline fun <T> Buffer.writeList(
    closure: (Buffer, T) -> Buffer,
    value: Collection<T>
): Buffer {
    writeInt(value.size)

    value.forEach { closure(this, it) }

    return this
}

inline fun <T> Buffer.writeIf(
    closure: (Buffer, T) -> Buffer,
    value: T?
): Buffer {
    writeBoolean(value != null)

    if (value != null) {
        closure(this, value)
    }

    return this
}