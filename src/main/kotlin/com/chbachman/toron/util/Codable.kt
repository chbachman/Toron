package com.chbachman.toron.util

import okio.Buffer
import kotlin.reflect.full.companionObjectInstance

interface ByteCodable<T> {
    fun read(input: ByteArray): T
    fun write(input: T): ByteArray
}

interface Codable<T>: ByteCodable<T> {
    fun write(input: T, buffer: Buffer): Buffer
    fun read(buffer: Buffer): T

    override fun read(input: ByteArray): T =
        read(Buffer().write(input))

    override fun write(input: T): ByteArray =
        write(input, Buffer()).readByteArray()
}

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